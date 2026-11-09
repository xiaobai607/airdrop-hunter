"""Allowlisted RSS/Atom intake. Feed items remain candidates, never verified projects."""
import hashlib
import logging
import os
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from urllib.parse import urljoin, urlparse

import httpx
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AirdropDiscoveryCandidate
from app.services.discovery_source_status import record_discovery_source_status

logger = logging.getLogger("airdrop_hunter.rss_discovery")
MAX_FEED_BYTES = 2_000_000
MAX_ITEMS_PER_FEED = 200


async def _read_limited_response(response) -> bytes:
    """Read a streamed feed without buffering an untrusted response past the limit."""
    declared_length = response.headers.get("content-length")
    if declared_length is not None:
        try:
            parsed_length = int(declared_length)
        except (TypeError, ValueError) as exc:
            raise ValueError("RSS feed has an invalid Content-Length") from exc
        if parsed_length < 0 or parsed_length > MAX_FEED_BYTES:
            raise ValueError("RSS feed exceeds size limit")

    body = bytearray()
    async for chunk in response.aiter_bytes():
        if len(body) + len(chunk) > MAX_FEED_BYTES:
            raise ValueError("RSS feed exceeds size limit")
        body.extend(chunk)
    return bytes(body)


def _configured_feeds() -> list[tuple[str, str]]:
    """AIRDROP_RSS_FEEDS format: name=https://host/feed.xml;other=https://host/rss"""
    configured = os.getenv("AIRDROP_RSS_FEEDS", "")
    allowed_hosts = {
        host.strip().lower()
        for host in os.getenv("AIRDROP_RSS_ALLOWED_HOSTS", "").split(",")
        if host.strip()
    }
    # RSS availability does not itself grant rights to republish feed content.
    # A source must be explicitly approved after rights/terms review.
    approved_sources = {
        source.strip().casefold()
        for source in os.getenv("AIRDROP_RSS_APPROVED_SOURCES", "").split(",")
        if source.strip()
    }
    feeds: list[tuple[str, str]] = []
    for entry in configured.split(";"):
        if "=" not in entry:
            continue
        name, raw_url = entry.split("=", 1)
        name, raw_url = name.strip()[:120], raw_url.strip()
        parsed = urlparse(raw_url)
        host = (parsed.hostname or "").lower()
        if not name or name.casefold() not in approved_sources:
            logger.warning("Skipping RSS feed without explicit content-reuse approval")
            continue
        if (
            parsed.scheme != "https" or not host
            or parsed.username or parsed.password or host not in allowed_hosts
        ):
            logger.warning("Skipping RSS feed not covered by HTTPS host allowlist")
            continue
        feeds.append((name, raw_url[:1000]))
    return feeds


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].lower()


def _child_text(element: ET.Element, names: set[str]) -> str:
    for child in element.iter():
        if child is element or _local_name(child.tag) not in names:
            continue
        if child.text and child.text.strip():
            return child.text.strip()
        href = child.attrib.get("href")
        if href:
            return href.strip()
    return ""


def _parse_date(value: str) -> datetime | None:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return parsed.replace(tzinfo=timezone.utc) if parsed.tzinfo is None else parsed
    except ValueError:
        try:
            parsed = parsedate_to_datetime(value)
            return parsed.replace(tzinfo=timezone.utc) if parsed.tzinfo is None else parsed
        except (TypeError, ValueError, OverflowError):
            return None


def parse_feed(xml_bytes: bytes, feed_url: str) -> list[dict[str, object]]:
    """Parse common RSS 2.0 and Atom feeds without executing or rendering markup."""
    if len(xml_bytes) > MAX_FEED_BYTES:
        raise ValueError("RSS feed exceeds size limit")
    root = ET.fromstring(xml_bytes)
    items = [node for node in root.iter() if _local_name(node.tag) in {"item", "entry"}]
    results: list[dict[str, object]] = []
    for item in items[:MAX_ITEMS_PER_FEED]:
        title = _child_text(item, {"title"})[:300]
        raw_link = _child_text(item, {"link"})
        if not raw_link:
            continue
        item_url = urljoin(feed_url, raw_link)
        parsed = urlparse(item_url)
        if not title or parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
            continue
        summary = _child_text(item, {"description", "summary", "encoded", "content"})[:5000]
        published = _child_text(item, {"pubdate", "published", "updated", "date"})
        results.append({
            "title": title,
            "item_url": item_url[:1000],
            "summary": summary,
            "published_at": _parse_date(published),
        })
    return results


async def sync_rss_discovery_candidates(db: AsyncSession) -> int:
    feeds = _configured_feeds()
    if not feeds:
        return 0
    total = 0
    now = datetime.now(timezone.utc)
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(12.0),
        follow_redirects=False,
        headers={"User-Agent": "AirdropHunter-FeedImporter/1.0"},
    ) as client:
        for source_name, source_url in feeds:
            try:
                async with client.stream("GET", source_url) as response:
                    response.raise_for_status()
                    feed_bytes = await _read_limited_response(response)
                records = parse_feed(feed_bytes, source_url)
                await record_discovery_source_status(
                    db, source_name, source_url, now, "success", item_count=len(records)
                )
                for record in records:
                    item_url = str(record["item_url"])
                    source_key = hashlib.sha256(item_url.encode("utf-8")).hexdigest()
                    current = await db.scalar(
                        select(AirdropDiscoveryCandidate).where(
                            AirdropDiscoveryCandidate.source_key == source_key
                        )
                    )
                    if current is None:
                        db.add(AirdropDiscoveryCandidate(
                            source_key=source_key,
                            source_name=source_name,
                            source_url=source_url,
                            title=str(record["title"]),
                            item_url=item_url,
                            summary=str(record["summary"]),
                            published_at=record["published_at"],
                        ))
                    else:
                        current.source_name = source_name
                        current.source_url = source_url
                        current.title = str(record["title"])
                        current.summary = str(record["summary"])
                        current.published_at = record["published_at"]
                        current.last_seen_at = now
                    total += 1
            except (httpx.HTTPError, ET.ParseError, ValueError) as exc:
                # One broken feed must not block other configured feeds.
                await record_discovery_source_status(
                    db, source_name, source_url, now, "failed", error=type(exc).__name__
                )
                logger.warning("RSS feed %s failed: %s", source_name, type(exc).__name__)
                continue
    await db.commit()
    return total
