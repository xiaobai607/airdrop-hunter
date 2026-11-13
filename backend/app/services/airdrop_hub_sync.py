import hashlib
import json
from html.parser import HTMLParser
import logging
import os
import re
from datetime import datetime, timezone
from typing import Any
from urllib.parse import unquote, urlparse

import httpx
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AirdropCampaign, AirdropCampaignSourceSnapshot, AirdropCampaignStatusHistory, AirdropDiscoveryCandidate, AirdropDiscoveryObservation, AirdropProject
from app.services.discovery_source_status import record_discovery_source_status

logger = logging.getLogger("airdrop_hunter.airdrop_sync")

BINANCE_CMS_SOURCE = "https://www.binance.com/bapi/apex/v1/public/apex/cms/article/list/query"
BINANCE_CMS_LEGACY_SOURCE = "https://www.binance.com/bapi/composite/v1/public/cms/article/list/query"
BINANCE_DISCOVERY_REFERENCE_URL = "https://raw.githubusercontent.com/DIYgod/RSSHub/master/lib/routes/binance/announcement.ts"
BINANCE_CATALOGS = (128, 93)
_BINANCE_DISCOVERY_CACHE_TTL_SECONDS = 6 * 60 * 60
_binance_discovery_cache: tuple[str, datetime] | None = None


def _extract_binance_cms_path_from_reference(source: str) -> str | None:
    """Extract only Binance CMS list paths from the public adapter reference."""
    if not isinstance(source, str) or len(source) > 1_000_000:
        return None
    match = re.search(
        r"\$\{baseUrl\}(/bapi/[A-Za-z0-9/_-]+/cms/article/list/query)",
        source,
    )
    if not match:
        return None
    path = match.group(1)
    if not re.fullmatch(r"/bapi/[A-Za-z0-9/_-]+/cms/article/list/query", path):
        return None
    return path


async def _discover_binance_cms_endpoint(client: httpx.AsyncClient) -> str | None:
    """Discover the endpoint path from an adapter reference, then keep Binance as the only host."""
    global _binance_discovery_cache
    now = datetime.now(timezone.utc)
    if _binance_discovery_cache is not None:
        cached_path, cached_at = _binance_discovery_cache
        if (now - cached_at).total_seconds() < _BINANCE_DISCOVERY_CACHE_TTL_SECONDS:
            return f"https://www.binance.com{cached_path}"
    try:
        response = await client.get(
            BINANCE_DISCOVERY_REFERENCE_URL,
            headers={"Accept": "text/plain"},
        )
        response.raise_for_status()
        if len(response.content) > 1_000_000:
            raise ValueError("Binance discovery reference exceeds 1 MB")
        path = _extract_binance_cms_path_from_reference(response.text)
        if path:
            _binance_discovery_cache = (path, now)
            return f"https://www.binance.com{path}"
    except (httpx.HTTPError, ValueError, TypeError) as exc:
        logger.warning("Binance endpoint discovery reference unavailable: %s", type(exc).__name__)
    return None

# Populate only after a provider has a documented free airdrop-directory API
# and its terms permit this product public catalog use. General token/TVL APIs
# are deliberately not eligible airdrop sources.
TRUSTED_AIRDROP_PROVIDER_LABELS: dict[str, str] = {}
TRUST_LEVEL_0 = "TRUST_LEVEL_0"
TRUST_LEVEL_1 = "TRUST_LEVEL_1"
OBSERVATION = "OBSERVATION"
PUBLISHED_SAFE = "PUBLISHED_SAFE"
SOURCE_LABELS = {"binance-announcements": "Binance Announcements", "coingecko-airdrop-category": "CoinGecko Airdrop Category", "github-airdrop-lists": "GitHub Open-source Lists", "dexscreener-new-pools": "DexScreener New Active Pools"}

def _source_publication_state(source_name: str) -> tuple[str, str]:
    if source_name in TRUSTED_AIRDROP_PROVIDER_LABELS:
        return TRUST_LEVEL_1, PUBLISHED_SAFE
    return TRUST_LEVEL_0, OBSERVATION
_sync_status: dict[str, Any] = {
    "last_attempt_at": None,
    "last_success_at": None,
    "imported_count": 0,
    "source": "none",
    "error": None,
}


def _valid_http_url(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    candidate = value.strip()
    if not candidate or any(ord(char) < 0x21 for char in candidate):
        return None
    try:
        parsed = urlparse(candidate)
        port = parsed.port
    except ValueError:
        return None
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username
        or parsed.password
        or (port is not None and not 1 <= port <= 65535)
    ):
        return None
    return candidate[:1000]


def _configured_github_raw_sources() -> list[dict[str, str]]:
    """Return only manually approved, license-reviewed, commit-pinned GitHub Raw sources."""
    try:
        configured = json.loads(os.getenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", "[]"))
    except (TypeError, ValueError):
        return []
    if not isinstance(configured, list) or not configured or len(configured) > 20:
        return []

    required = {
        "source_id", "url", "repository", "license_id", "license_url",
        "attribution", "review_status", "reviewed_at",
    }
    approved: list[dict[str, str]] = []
    seen_ids: set[str] = set()
    seen_urls: set[str] = set()
    for entry in configured:
        if not isinstance(entry, dict) or set(entry) != required:
            return []
        if any(not isinstance(entry.get(key), str) for key in required):
            return []
        source_id = entry["source_id"].strip()
        url = entry["url"].strip()
        repository = entry["repository"].strip()
        license_id = entry["license_id"].strip()
        license_url = _valid_http_url(entry["license_url"])
        attribution = entry["attribution"].strip()
        review_status = entry["review_status"].strip()
        reviewed_at = entry["reviewed_at"].strip()

        if (
            not re.fullmatch(r"[a-z0-9][a-z0-9._-]{1,63}", source_id)
            or not re.fullmatch(r"[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}", repository)
            or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.+-]{0,63}", license_id)
            or not license_url
            or not attribution
            or len(attribution) > 500
            or review_status != "approved"
            or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", reviewed_at)
        ):
            return []
        try:
            datetime.strptime(reviewed_at, "%Y-%m-%d")
            parsed = urlparse(url)
            port = parsed.port
        except ValueError:
            return []

        path_parts = [part for part in parsed.path.split("/") if part]
        decoded_parts = [unquote(part) for part in path_parts]
        if (
            parsed.scheme != "https"
            or parsed.hostname != "raw.githubusercontent.com"
            or parsed.netloc.lower() != "raw.githubusercontent.com"
            or port is not None
            or parsed.query
            or parsed.fragment
            or len(path_parts) < 4
            or f"{path_parts[0]}/{path_parts[1]}" != repository
            or not re.fullmatch(r"[0-9a-fA-F]{40}", path_parts[2])
            or any(
                part in {".", ".."}
                or "/" in part
                or "\\" in part
                or any(ord(char) < 0x21 or char.isspace() for char in part)
                for part in decoded_parts
            )
            or re.search(r"%(?![0-9A-Fa-f]{2})", url)
            or any(ord(char) < 0x21 or char.isspace() for char in url)
        ):
            return []
        if source_id in seen_ids or url in seen_urls:
            return []
        seen_ids.add(source_id)
        seen_urls.add(url)
        approved.append({
            "source_id": source_id,
            "url": url,
            "repository": repository,
            "license_id": license_id,
            "license_url": license_url,
            "attribution": attribution,
            "review_status": review_status,
            "reviewed_at": reviewed_at,
        })
    return approved


def _github_source_attribution(source: dict[str, str]) -> str:
    """Preserve the operator-reviewed license and attribution with each imported lead."""
    return (
        f"开源名单 {source['source_id']}（{source['repository']}）；"
        f"许可证 {source['license_id']}（{source['license_url']}）；"
        f"署名要求：{source['attribution']}；审核日期 {source['reviewed_at']}。"
    )


def _parse_binance_published_at(value: Any) -> datetime | None:
    """Parse Binance releaseDate values without guessing missing or malformed dates."""
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return None
        try:
            numeric = float(text)
        except ValueError:
            try:
                parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
            except ValueError:
                return None
            if parsed.tzinfo is None:
                parsed = parsed.replace(tzinfo=timezone.utc)
            return parsed.astimezone(timezone.utc)
    else:
        try:
            numeric = float(value)
        except (TypeError, ValueError, OverflowError):
            return None
    if not 0 < numeric < 100_000_000_000_000:
        return None
    if numeric > 10_000_000_000:
        numeric /= 1000
    try:
        return datetime.fromtimestamp(numeric, timezone.utc)
    except (OverflowError, OSError, ValueError):
        return None


def _normalize_binance_announcements(payload: Any, *, catalog_id: int) -> list[dict[str, Any]]:
    """Convert Binance announcement headlines into unverified observation leads."""
    data = payload.get("data") if isinstance(payload, dict) else None
    catalogs = data.get("catalogs") if isinstance(data, dict) else None
    if not isinstance(catalogs, list):
        return []
    records: list[dict[str, str]] = []
    for catalog in catalogs:
        if not isinstance(catalog, dict):
            continue
        articles = catalog.get("articles")
        if not isinstance(articles, list):
            continue
        for article in articles:
            if not isinstance(article, dict):
                continue
            title = str(article.get("title") or "").strip()
            code = str(article.get("code") or "").strip()
            if not title or not code:
                continue
            safe_code = re.sub(r"[^A-Za-z0-9_-]", "", code)
            # Never silently rewrite an upstream identifier into a different article code.
            if not safe_code or safe_code != code:
                continue
            published_at = _parse_binance_published_at(article.get("releaseDate"))
            records.append({
                "source_key": f"binance:{safe_code}"[:240],
                "name": title[:180],
                "published_at": published_at,
                "chain": "Unknown",
                "description": "Binance 公告线索；请打开原文核实活动资格、时间与参与条件。",
                "status": "待核实",
                "link": f"https://www.binance.com/en/support/announcement/{safe_code}",
                "source_name": "binance-announcements",
                "trust_level": TRUST_LEVEL_0,
                "publication_status": OBSERVATION,
                "catalog_id": catalog_id,
                "catalog_name": str(catalog.get("catalogName") or "")[:120],
                "_raw_payload": article,
            })
    return records


BINANCE_CMS_DETAIL_SOURCE = "https://www.binance.com/bapi/composite/v1/public/cms/article/detail/query"


def _normalize_binance_article_detail(payload: Any, *, fallback_title: str, fallback_url: str) -> dict[str, Any]:
    """Read the original Binance CMS article fields without rewriting their meaning."""
    data = payload.get("data") if isinstance(payload, dict) else None
    if isinstance(data, list):
        data = next((entry for entry in data if isinstance(entry, dict)), {})
    if not isinstance(data, dict):
        data = {}
    article = data.get("article") if isinstance(data.get("article"), dict) else data
    body = article.get("body") or article.get("content") or article.get("articleContent") or ""
    if not isinstance(body, str) or not body.strip():
        raise ValueError("Binance CMS detail response did not contain article body")
    release_date = article.get("releaseDate") or article.get("publishDate")
    try:
        release_date = int(release_date) if release_date is not None else None
    except (TypeError, ValueError):
        release_date = None
    return {
        "title": str(article.get("title") or fallback_title)[:300],
        "content_html": body,
        "release_date": release_date,
        "catalog_name": str(article.get("catalogName") or "")[:120],
        "original_url": fallback_url,
    }


async def _fetch_binance_article_detail_with_client(
    client: httpx.AsyncClient,
    article_code: str,
    *,
    fallback_title: str,
    fallback_url: str,
) -> dict[str, Any]:
    safe_code = re.sub(r"[^A-Za-z0-9_-]", "", article_code or "")
    if not safe_code or safe_code != article_code:
        raise ValueError("Invalid Binance article code")
    response = await client.get(
        BINANCE_CMS_DETAIL_SOURCE,
        params={"articleCode": safe_code},
        headers={
            "Origin": "https://www.binance.com",
            "clienttype": "web",
            "lang": "en",
        },
    )
    response.raise_for_status()
    if len(response.content) > 3_000_000:
        raise ValueError("Binance article exceeds the 3 MB display limit")
    return _normalize_binance_article_detail(
        response.json(), fallback_title=fallback_title, fallback_url=fallback_url
    )


async def fetch_binance_article_detail(article_code: str, *, fallback_title: str, fallback_url: str) -> dict[str, Any]:
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(20.0),
        headers={
            "User-Agent": "AirdropHunter/1.0",
            "Accept": "application/json",
        },
    ) as client:
        return await _fetch_binance_article_detail_with_client(
            client, article_code, fallback_title=fallback_title, fallback_url=fallback_url
        )


def _binance_http_error_label(exc: Exception) -> str:
    """Expose bounded provider error metadata without logging response bodies."""
    response = getattr(exc, "response", None)
    status_code = getattr(response, "status_code", None)
    label = f"{type(exc).__name__}(HTTP {status_code})" if status_code is not None else type(exc).__name__
    if response is not None:
        try:
            payload = response.json()
        except (ValueError, TypeError):
            payload = None
        if isinstance(payload, dict):
            details = []
            for key in ("code", "msg", "message"):
                value = payload.get(key)
                if isinstance(value, (str, int, float)) and str(value).strip():
                    details.append(f"{key}={str(value).strip()[:120]}")
            if details:
                label = f"{label} [{'; '.join(details)}]"
    return label


async def _fetch_binance_public_market_data(client: httpx.AsyncClient) -> list[dict[str, Any]]:
    """Minimal no-key Binance public API connectivity check; never classify market data as an airdrop."""
    response = await client.get(
        "https://data-api.binance.vision/api/v3/ticker/price",
        params={"symbol": "BTCUSDT"},
        headers={
            "User-Agent": (
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 "
                "AirdropHunter-Discovery/1.0"
            ),
            "Accept": "application/json",
        },
    )
    response.raise_for_status()
    payload = response.json()
    if not isinstance(payload, dict) or payload.get("symbol") != "BTCUSDT":
        raise ValueError("Binance public market endpoint returned an unexpected payload")
    try:
        price = float(payload.get("price"))
    except (TypeError, ValueError):
        raise ValueError("Binance public market endpoint returned an invalid BTCUSDT price")
    if price <= 0:
        raise ValueError("Binance public market endpoint returned a non-positive BTCUSDT price")
    return [{
        "name": "BTCUSDT",
        "price": price,
        "source_name": "binance-public-market-data",
    }]


async def _fetch_binance_observations(client: httpx.AsyncClient) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    errors: list[str] = []
    # Match the public announcement-page request shape used by the
    # currently maintained Binance announcement adapter: plain GET, browser
    # UA, page referer, and the page language. Avoid a bot-identifying UA
    # suffix and unnecessary JSON/body headers on a GET request.
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        ),
        "Accept": "application/json, text/plain, */*",
        "Referer": "https://www.binance.com/en/messages/v2/group/announcement",
        "lang": "en",
        "Accept-Language": "en",
    }
    # Discover the path from the public adapter's source code first. The
    # reference is only a discovery hint: all actual announcement data must
    # still come directly from www.binance.com and pass the response checks.
    discovered = await _discover_binance_cms_endpoint(client)
    endpoints = [discovered] if discovered else []
    for known in (BINANCE_CMS_SOURCE, BINANCE_CMS_LEGACY_SOURCE):
        if known not in endpoints:
            endpoints.append(known)

    for catalog_id in BINANCE_CATALOGS:
        last_error: Exception | None = None
        for endpoint in endpoints:
            try:
                response = await client.get(
                    endpoint,
                    params={"type": 1, "pageSize": 20, "pageNo": 1, "catalogId": catalog_id},
                    headers=headers,
                )
                response.raise_for_status()
                payload = response.json()
                data = payload.get("data") if isinstance(payload, dict) else None
                catalogs = data.get("catalogs") if isinstance(data, dict) else None
                if not isinstance(catalogs, list):
                    raise ValueError("Binance CMS response missing data.catalogs")
                if catalogs and not any(
                    isinstance(item, dict) and item.get("catalogId") == catalog_id
                    for item in catalogs
                ):
                    raise ValueError("Binance CMS response catalog ID did not match request")
                rows = _normalize_binance_announcements(payload, catalog_id=catalog_id)
                for row in rows:
                    row["source_url"] = str(response.request.url)
                records.extend(rows)
                last_error = None
                break
            except (httpx.HTTPError, ValueError, TypeError) as exc:
                last_error = exc
                error_label = _binance_http_error_label(exc)
                logger.warning(
                    "Binance catalog %s endpoint %s failed: %s",
                    catalog_id,
                    urlparse(endpoint).path,
                    error_label,
                )
        if last_error is not None:
            error_label = _binance_http_error_label(last_error)
            errors.append(f"catalog {catalog_id}: {error_label}")
    if not records and errors:
        raise RuntimeError("Binance CMS categories unavailable: " + "; ".join(errors))
    return records


def _observation_record(source_name: str, source_key: str, name: str, link: str, description: str, chain: str = 'Unknown', status: str = '待核实') -> dict[str, str]:
    return {'source_key': source_key[:240], 'name': name[:180], 'chain': chain[:80], 'description': description[:10000], 'status': status[:40], 'link': link[:1000], 'source_name': source_name, 'trust_level': TRUST_LEVEL_0, 'publication_status': OBSERVATION}

def _normalize_coingecko_categories(payload: Any) -> list[dict[str, str]]:
    """Normalize CoinGecko category IDs across the API's documented field names."""
    if not isinstance(payload, list):
        return []
    normalized: list[dict[str, str]] = []
    for item in payload:
        if not isinstance(item, dict):
            continue
        # /coins/categories/list uses category_id; some category payloads use id.
        category_id = str(item.get("category_id") or item.get("id") or "").strip()
        name = str(item.get("name") or "").strip()
        if category_id and name:
            normalized.append({"id": category_id, "name": name})
    return normalized


def _normalize_coingecko_coins(payload: Any, category_id: str, website_by_id: dict[str, str] | None = None) -> list[dict[str, Any]]:
    if not isinstance(payload, list):
        return []
    websites = website_by_id or {}
    rows: list[dict[str, Any]] = []
    for item in payload[:20]:
        if not isinstance(item, dict) or not item.get("id") or not item.get("name"):
            continue
        coin_id = str(item["id"])
        official_url = _valid_http_url(websites.get(coin_id))
        if not official_url:
            continue
        row = _observation_record("coingecko-airdrop-category", f"coingecko:{category_id}:{coin_id}",
            str(item["name"]), official_url, "CoinGecko Airdrop 类目代币线索；不代表当前存在可领取活动。")
        row["_filter_metadata"] = {
            "category_id": category_id, "coin_id": coin_id,
            "market_cap_rank": item.get("market_cap_rank"), "market_cap": item.get("market_cap"),
            "total_volume": item.get("total_volume"),
            "price_change_percentage_24h": item.get("price_change_percentage_24h"),
            "source_updated_at": item.get("last_updated"), "official_url": official_url,
            "market_signal_only": True,
        }
        row["_raw_payload"] = {"market": item}
        rows.append(row)
    return rows


def _normalize_github_airdrop_list(payload: Any, source_url: str) -> list[dict[str, Any]]:
    """Extract list entries without deciding whether their links qualify as official."""
    found: list[tuple[str, Any, Any]] = []

    def walk(value: Any) -> None:
        if isinstance(value, list):
            for entry in value:
                walk(entry)
        elif isinstance(value, dict):
            name = value.get("name") or value.get("project") or value.get("title")
            official_raw = value.get("website") or value.get("official_url") or value.get("officialUrl")
            if name:
                found.append((str(name).strip(), official_raw, value))
            for child in value.values():
                if isinstance(child, (dict, list)):
                    walk(child)

    if isinstance(payload, (dict, list)):
        walk(payload)
    elif isinstance(payload, str):
        for line in payload.splitlines():
            match = re.search(r"\[([^\]]{2,180})\]\(([^)\s]+)\)", line)
            if match:
                found.append((match.group(1).strip(), match.group(2), {"markdown_line": line}))

    result: list[dict[str, Any]] = []
    safe_source_url = _valid_http_url(source_url)
    if not safe_source_url:
        return result
    for name, official_raw, raw_entry in found:
        if not name:
            continue
        official_text = str(official_raw).strip() if official_raw is not None else ""
        safe_official_url = _valid_http_url(official_text) if official_text else None
        # A source-file URL is only an evidence locator, never a project identity.
        item_url = safe_official_url or safe_source_url
        raw_text = json.dumps(raw_entry, ensure_ascii=False, sort_keys=True, default=str)
        digest = hashlib.sha256(
            f"{source_url}|{name.casefold()}|{official_text}|{raw_text}".encode()
        ).hexdigest()[:24]
        row = _observation_record(
            "github-airdrop-lists",
            f"github:{digest}",
            name,
            item_url,
            f"来自开源名单 {source_url}；项目状态及官网真实性待核实。",
        )
        row["source_url"] = safe_source_url
        row["_filter_metadata"] = {
            "official_url": safe_official_url,
            "official_url_raw": official_text[:1000],
        }
        row["_raw_payload"] = raw_entry
        result.append(row)
    return result

def _normalize_dexscreener_pairs(payload: Any, query: str) -> list[dict[str, Any]]:
    pairs = payload.get("pairs") if isinstance(payload, dict) else None
    if not isinstance(pairs, list):
        return []
    now = datetime.now(timezone.utc).timestamp()
    rows: list[dict[str, Any]] = []
    for pair in pairs:
        if not isinstance(pair, dict):
            continue
        token = pair.get("baseToken") or {}
        name, address = str(token.get("name") or "").strip(), str(token.get("address") or "").strip()
        chain, url = str(pair.get("chainId") or "Unknown"), _valid_http_url(pair.get("url"))
        if not name or not address or not url:
            continue
        try:
            created_at = pair.get("pairCreatedAt")
            age = now - float(created_at) / 1000 if created_at is not None else None
            liquidity = float((pair.get("liquidity") or {}).get("usd")) if (pair.get("liquidity") or {}).get("usd") is not None else None
            volume = float((pair.get("volume") or {}).get("h24")) if (pair.get("volume") or {}).get("h24") is not None else None
            tx = (pair.get("txns") or {}).get("h24") or {}
            trades = int(tx.get("buys") or 0) + int(tx.get("sells") or 0)
        except (TypeError, ValueError, AttributeError):
            age, liquidity, volume, trades = None, None, None, None
        symbol = str(token.get("symbol") or "")
        desc = f"DexScreener 原始交易池线索（搜索词 {query}）；池龄秒数 {age if age is not None else '未知'}；流动性 USD {liquidity if liquidity is not None else '未知'}；24h 成交量 USD {volume if volume is not None else '未知'}；24h 买卖笔数 {trades if trades is not None else '未知'}。交易活跃不代表空投或安全。"
        row = _observation_record("dexscreener-new-pools", f"dexscreener:{chain}:{address}".lower(), f"{name} ({symbol})" if symbol else name, url, desc, chain, "待筛选")
        row["_filter_metadata"] = {"query": query, "pair_created_at": pair.get("pairCreatedAt"), "age_seconds": age, "liquidity_usd": liquidity, "volume_24h_usd": volume, "trades_24h": trades, "chain_id": chain, "token_address": address, "token_symbol": symbol, "dex_id": pair.get("dexId")}
        row["_raw_payload"] = pair
        rows.append(row)
    return rows

async def _fetch_coingecko_observations(client: httpx.AsyncClient) -> list[dict[str, Any]]:
    key = os.getenv("COINGECKO_DEMO_API_KEY", "").strip()
    if not key:
        raise RuntimeError("COINGECKO_DEMO_API_KEY 未配置")
    headers = {"x-cg-demo-api-key": key, "Accept": "application/json"}
    category_response = await client.get("https://api.coingecko.com/api/v3/coins/categories/list", headers=headers)
    category_response.raise_for_status()
    categories = _normalize_coingecko_categories(category_response.json())
    from app.services.airdrop_discovery_filter import select_coingecko_airdrop_categories
    category_ids = select_coingecko_airdrop_categories(categories)
    if not category_ids:
        raise RuntimeError("CoinGecko categories/list 未找到 Airdrop 分类")
    category_id = category_ids[0]
    market_response = await client.get(
        "https://api.coingecko.com/api/v3/coins/markets", headers=headers,
        params={"vs_currency": "usd", "category": category_id, "order": "market_cap_desc",
                "per_page": 20, "page": 1, "sparkline": "false"})
    market_response.raise_for_status()
    markets = market_response.json()
    if not isinstance(markets, list) or not markets:
        raise RuntimeError("CoinGecko Airdrop category returned no market entries")
    website_by_id: dict[str, str] = {}
    detail_by_id: dict[str, dict[str, Any]] = {}
    # /coins/markets omits official websites; resolve no more than 20 details sequentially.
    for market in markets[:20]:
        if not isinstance(market, dict) or not market.get("id"):
            continue
        coin_id = str(market["id"])
        detail_response = await client.get(
            f"https://api.coingecko.com/api/v3/coins/{coin_id}", headers=headers,
            params={"localization": "false", "tickers": "false", "market_data": "false",
                    "community_data": "false", "developer_data": "false", "sparkline": "false"})
        detail_response.raise_for_status()
        detail = detail_response.json()
        links = detail.get("links") if isinstance(detail, dict) else {}
        homepages = links.get("homepage") if isinstance(links, dict) else []
        homepage = next((_valid_http_url(value) for value in homepages if _valid_http_url(value)), None) if isinstance(homepages, list) else None
        if homepage:
            website_by_id[coin_id] = homepage
            detail_by_id[coin_id] = detail
    rows = _normalize_coingecko_coins(markets, category_id, website_by_id)
    for row in rows:
        coin_id = row["_filter_metadata"]["coin_id"]
        row["_raw_payload"]["detail"] = detail_by_id.get(coin_id, {})
        row["source_url"] = f"https://api.coingecko.com/api/v3/coins/{coin_id}"
    if not rows:
        raise RuntimeError("CoinGecko Airdrop category returned no coins with valid official websites")
    return rows


async def _fetch_github_observations(client: httpx.AsyncClient) -> list[dict[str, str]]:
    sources = _configured_github_raw_sources()
    if not sources:
        raise RuntimeError(
            "AIRDROP_GITHUB_RAW_SOURCE_APPROVALS 未配置有效的人工审核来源；旧 URL-only 配置不会被读取"
        )
    rows, failures = [], 0
    for source in sources:
        url = source["url"]
        try:
            response = await client.get(url, headers={"Accept": "application/json, text/plain"})
            response.raise_for_status()
            if len(response.content) > 2_000_000:
                raise ValueError("GitHub Raw file exceeds 2 MB safety limit")
            try:
                payload = response.json()
            except ValueError:
                payload = response.text
            attribution = _github_source_attribution(source)
            for row in _normalize_github_airdrop_list(payload, url):
                row["description"] = f"{attribution} {row['description']}"[:10000]
                rows.append(row)
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            failures += 1
            logger.warning(
                "GitHub Raw source %s failed: %s", source["source_id"], type(exc).__name__
            )
    if not rows:
        raise RuntimeError(f"GitHub Raw 未解析到项目；失败文件数 {failures}")
    return rows

def _configured_dexscreener_search_queries() -> list[str]:
    """Use a safe free default when the optional Actions variable is absent or blank."""
    raw = os.getenv("DEXSCREENER_SEARCH_QUERIES", "")
    queries = list(dict.fromkeys(
        part.strip() for part in raw.split(",") if part.strip()
    ))[:8]
    return queries or ["airdrop"]


async def _fetch_dexscreener_observations(client: httpx.AsyncClient) -> list[dict[str, Any]]:
    queries = _configured_dexscreener_search_queries()

    rows: list[dict[str, Any]] = []
    failures: list[str] = []
    for query in queries:
        try:
            response = await client.get(
                "https://api.dexscreener.com/latest/dex/search",
                params={"q": query},
            )
            response.raise_for_status()
            query_rows = _normalize_dexscreener_pairs(response.json(), query)
            for row in query_rows:
                row["source_url"] = str(response.request.url)
            rows.extend(query_rows)
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            status_code = getattr(getattr(exc, "response", None), "status_code", None)
            error_label = (
                f"{type(exc).__name__}(HTTP {status_code})"
                if status_code is not None else type(exc).__name__
            )
            failures.append(f"{query}:{error_label}")
            logger.warning(
                "DexScreener search %s failed; continuing with remaining terms: %s",
                query,
                error_label,
            )

    if not rows:
        if failures:
            raise RuntimeError(
                "DexScreener 所有搜索词均失败或未产生有效记录: " + "; ".join(failures)
            )
        raise RuntimeError("DexScreener 本轮没有返回可解析的交易池记录")
    from app.services.airdrop_discovery_filter import select_dexscreener_top_candidates
    rows = select_dexscreener_top_candidates(rows, limit=10)
    if failures:
        logger.warning(
            "DexScreener completed partially: %s of %s search terms failed",
            len(failures),
            len(queries),
        )
    return rows


_DISCOVERY_NORMALIZER_VERSION = "discovery-normalizer-v1"
_DISCOVERY_FILTER_VERSION = "central-discovery-filter-v1"
_DISCOVERY_RAW_PAYLOAD_MAX_BYTES = 64 * 1024
_DISCOVERY_SECRET_FIELD = re.compile(r"(?:api.?key|access.?token|refresh.?token|authorization|cookie|password|secret|signature|private.?key)", re.IGNORECASE)


def _safe_observation_payload(value: Any, *, depth: int = 0) -> Any:
    """Keep bounded provider evidence while removing credential-like fields."""
    if depth > 8:
        return "[max-depth]"
    if isinstance(value, dict):
        result = {}
        for index, (key, child) in enumerate(value.items()):
            if index >= 200:
                result["_truncated_fields"] = True
                break
            key_text = str(key)[:120]
            if _DISCOVERY_SECRET_FIELD.search(key_text):
                continue
            result[key_text] = _safe_observation_payload(child, depth=depth + 1)
        return result
    if isinstance(value, (list, tuple)):
        return [_safe_observation_payload(item, depth=depth + 1) for item in value[:200]]
    if isinstance(value, str):
        return value[:80000]
    if value is None or isinstance(value, (bool, int, float)):
        return value
    if isinstance(value, datetime):
        return value.isoformat()
    return str(value)[:2000]


def _bounded_observation_payload(value: Any) -> dict[str, Any]:
    safe = _safe_observation_payload(value)
    if not isinstance(safe, dict):
        safe = {"value": safe}
    encoded = json.dumps(safe, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    encoded_bytes = len(encoded.encode("utf-8"))
    if encoded_bytes <= _DISCOVERY_RAW_PAYLOAD_MAX_BYTES:
        return safe
    return {
        "_truncated": True,
        "_original_serialized_bytes": encoded_bytes,
        "_preview": encoded[:_DISCOVERY_RAW_PAYLOAD_MAX_BYTES // 2],
    }


def _build_discovery_observation(
    *, candidate: AirdropDiscoveryCandidate, source_name: str, source_record_key: str,
    source_url: str, record: dict[str, Any], now: datetime,
    filter_result: dict[str, Any], decision_result: dict[str, Any],
    identity_result: dict[str, Any],
) -> AirdropDiscoveryObservation:
    raw_payload = _bounded_observation_payload(record.get("_raw_payload") or {
        key: value for key, value in record.items() if not key.startswith("_")
    })
    normalized_payload = _bounded_observation_payload({
        key: value for key, value in record.items() if not key.startswith("_")
    })
    payload_text = json.dumps(raw_payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return AirdropDiscoveryObservation(
        candidate_id=candidate.id,
        source_name=source_name,
        source_record_key=source_record_key[:240],
        source_url=source_url[:1000],
        item_url=str(record.get("link") or "")[:1000],
        observed_at=now,
        payload_sha256=hashlib.sha256(payload_text.encode("utf-8")).hexdigest(),
        raw_payload=raw_payload,
        normalized_payload=normalized_payload,
        filter_decision={
            "disposition": decision_result.get("disposition"),
            "reasons": list(decision_result.get("reasons") or []),
            "evaluated_at": filter_result.get("evaluated_at"),
            "metadata": _safe_observation_payload(filter_result.get("metadata") or {}),
            "filter_version": _DISCOVERY_FILTER_VERSION,
        },
        identity_decision={key: identity_result.get(key) for key in (
            "project_identity_key", "campaign_identity_key", "identity_status",
            "project_identity_evidence", "campaign_identity_evidence",
            "matched_source_keys", "identity_conflict", "identity_group_type",
            "identity_group_key",
        )},
        normalizer_version=_DISCOVERY_NORMALIZER_VERSION,
        filter_version=_DISCOVERY_FILTER_VERSION,
    )

def _candidate_source_key(source_name: str, record_source_key: str) -> str:
    """Use a bounded, stable key while preserving the provider-specific source key elsewhere."""
    return hashlib.sha256(
        f"{source_name}|{record_source_key}".encode("utf-8")
    ).hexdigest()


class _CampaignHtmlTextParser(HTMLParser):
    _BLOCK_TAGS = {"br", "div", "p", "li", "h1", "h2", "h3", "h4", "tr"}

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() in self._BLOCK_TAGS:
            self.parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() in self._BLOCK_TAGS:
            self.parts.append("\n")

    def handle_data(self, data: str) -> None:
        if data.strip():
            self.parts.append(data.strip())


def _binance_article_plain_text(content_html: str) -> str:
    parser = _CampaignHtmlTextParser()
    parser.feed(content_html[:3_000_000])
    parser.close()
    lines = [" ".join(part.split()) for part in "".join(parser.parts).splitlines()]
    return "\n".join(line for line in lines if line)[:10_000]


async def _upsert_binance_airdrop_campaign(
    db: AsyncSession,
    *,
    record: dict[str, Any],
    detail: dict[str, Any],
    now: datetime,
) -> bool:
    """Persist only detailed articles from Binance's explicit Crypto Airdrop category."""
    if record.get("catalog_id") != 128 or str(record.get("catalog_name") or "").strip().casefold() != "crypto airdrop":
        return False

    official_url = _valid_http_url(record.get("link"))
    parsed_url = urlparse(official_url or "")
    if parsed_url.hostname not in {"binance.com", "www.binance.com"}:
        return False

    article_code = str(record.get("source_key") or "").removeprefix("binance:")
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,200}", article_code):
        return False

    content_html = str(detail.get("content_html") or "")
    description = _binance_article_plain_text(content_html)
    title = str(detail.get("title") or record.get("name") or "").strip()[:240]
    if not title or not description:
        return False

    source_key = f"binance:crypto-airdrop:{article_code}"[:240]
    campaign = await db.scalar(
        select(AirdropCampaign).where(AirdropCampaign.source_key == source_key)
    )
    is_new = campaign is None
    if campaign is None:
        campaign = AirdropCampaign(
            source_key=source_key,
            source_name="binance-crypto-airdrop",
            source_url=official_url,
            name=title,
            chain=None,
            description=description,
            official_url=official_url,
            campaign_type="airdrop",
            campaign_status="unknown",
            trust_status="verified",
            tier="observation",
            is_published=False,
            last_verified_at=now,
            last_source_success_at=now,
        )
        db.add(campaign)
        await db.flush()
        db.add(AirdropCampaignStatusHistory(
            campaign_id=campaign.id,
            previous_status=None,
            new_status="unknown",
            reason="Binance 官方 Crypto Airdrop 分类文章已核验；原文未提供可可靠解析的活动状态，保留未知",
            source_name="binance-crypto-airdrop",
        ))
    else:
        campaign.source_name = "binance-crypto-airdrop"
        campaign.source_url = official_url
        campaign.name = title
        campaign.description = description
        campaign.official_url = official_url
        campaign.campaign_type = "airdrop"
        campaign.trust_status = "verified"
        campaign.last_verified_at = now
        campaign.last_source_success_at = now
        campaign.last_seen_at = now
        if not campaign.campaign_status:
            campaign.campaign_status = "unknown"

    evidence = {
        "catalog_id": 128,
        "catalog_name": "Crypto Airdrop",
        "article_code": article_code,
        "title": title,
        "official_url": official_url,
        "release_date": detail.get("release_date"),
        "content_sha256": hashlib.sha256(content_html.encode("utf-8")).hexdigest(),
        "content_excerpt": description[:2000],
    }
    serialized = json.dumps(evidence, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    snapshot_hash = hashlib.sha256(serialized.encode("utf-8")).hexdigest()
    existing_snapshot = await db.scalar(
        select(AirdropCampaignSourceSnapshot.id).where(
            AirdropCampaignSourceSnapshot.campaign_id == campaign.id,
            AirdropCampaignSourceSnapshot.source_name == "binance-crypto-airdrop",
            AirdropCampaignSourceSnapshot.snapshot_hash == snapshot_hash,
        )
    )
    if existing_snapshot is None:
        db.add(AirdropCampaignSourceSnapshot(
            campaign_id=campaign.id,
            source_name="binance-crypto-airdrop",
            source_url=official_url,
            snapshot_hash=snapshot_hash,
            normalized_payload=evidence,
        ))
    return is_new


async def _upsert_discovery_candidate(
    db: AsyncSession,
    *,
    source_name: str,
    source_url: str,
    record: dict[str, Any],
    now: datetime,
    identity_result: dict[str, Any] | None = None,
    decision_result: dict[str, Any] | None = None,
) -> bool:
    """Feed every provider observation into one review queue without auto-publishing it."""
    item_url = _valid_http_url(record.get("link"))
    title = str(record.get("name") or "").strip()
    if not item_url or not title:
        return False

    record_source_key = str(record.get("source_key") or "").strip()
    if not record_source_key:
        return False
    candidate_key = _candidate_source_key(source_name, record_source_key)
    candidate = await db.scalar(
        select(AirdropDiscoveryCandidate).where(
            AirdropDiscoveryCandidate.source_key == candidate_key
        )
    )
    candidate_source_url = _valid_http_url(record.get("source_url")) or source_url
    from app.services.airdrop_discovery_filter import compose_discovery_decision, evaluate_discovery_identity, evaluate_discovery_observation
    identity_result = identity_result or await evaluate_discovery_identity(db, record)
    filter_result = evaluate_discovery_observation(record, now=now)
    decision_result = decision_result or compose_discovery_decision(filter_result, identity_result)
    summary = str(record.get("description") or "")[:10000]
    if candidate is None:
        db.add(AirdropDiscoveryCandidate(
            source_key=candidate_key,
            source_name=source_name,
            source_url=candidate_source_url,
            title=title[:300],
            item_url=item_url,
            summary=summary,
            published_at=record.get("published_at"),
            first_seen_at=now,
            last_seen_at=now,
            review_status="pending",
            filter_status=decision_result["disposition"],
            filter_reasons=decision_result["reasons"],
            filter_metadata={**filter_result["metadata"], "evaluated_at": filter_result["evaluated_at"], "decision_version": 1},
            project_identity_key=identity_result["project_identity_key"],
            campaign_identity_key=identity_result["campaign_identity_key"],
            identity_status=identity_result["identity_status"],
            identity_evidence={key: identity_result[key] for key in ("project_identity_evidence", "campaign_identity_evidence", "matched_source_keys", "identity_conflict", "identity_group_type", "identity_group_key")},
            matched_source_key=identity_result["matched_source_key"],
        ))
    else:
        candidate.source_name = source_name
        candidate.source_url = candidate_source_url
        candidate.title = title[:300]
        candidate.item_url = item_url
        candidate.summary = summary
        candidate.published_at = record.get("published_at")
        candidate.last_seen_at = now
        candidate.filter_status = decision_result["disposition"]
        candidate.filter_reasons = decision_result["reasons"]
        candidate.filter_metadata = {**filter_result["metadata"], "evaluated_at": filter_result["evaluated_at"], "decision_version": 1}
        candidate.project_identity_key = identity_result["project_identity_key"]
        candidate.campaign_identity_key = identity_result["campaign_identity_key"]
        candidate.identity_status = identity_result["identity_status"]
        candidate.identity_evidence = {key: identity_result[key] for key in ("project_identity_evidence", "campaign_identity_evidence", "matched_source_keys", "identity_conflict", "identity_group_type", "identity_group_key")}
        candidate.matched_source_key = identity_result["matched_source_key"]
        # Preserve approved/rejected review decisions across future refreshes.
    return True


async def _upsert_source_records(db: AsyncSession, source_name: str, source_url: str, fetcher: Any, client: httpx.AsyncClient, now: datetime) -> int:
    status = "failed"
    error_code = "source_sync_error"
    try:
        records = await fetcher(client)
        if not records:
            status = "empty"
            error_code = "source_empty_result"
            raise RuntimeError("empty source result")
        from app.services.airdrop_discovery_filter import is_source_fresh
        if not is_source_fresh(records, now=now, max_age_days=7):
            status = "stale"
            error_code = "source_payload_stale_or_timestamp_invalid"
            raise RuntimeError("source payload contains generatedAt/updatedAt older than 7 days")
        for record in records:
            campaign_detail = None
            if (
                source_name == "binance-announcements"
                and record.get("catalog_id") == 128
                and str(record.get("catalog_name") or "").strip().casefold() == "crypto airdrop"
            ):
                article_code = str(record.get("source_key") or "").removeprefix("binance:")
                try:
                    campaign_detail = await _fetch_binance_article_detail_with_client(
                        client,
                        article_code,
                        fallback_title=str(record.get("name") or ""),
                        fallback_url=str(record.get("link") or ""),
                    )
                except (httpx.HTTPError, ValueError, TypeError) as exc:
                    logger.warning(
                        "Binance Crypto Airdrop detail unavailable for article %s: %s",
                        article_code[:80],
                        type(exc).__name__,
                    )
            from app.services.airdrop_discovery_filter import compose_discovery_decision, evaluate_discovery_identity, evaluate_discovery_observation
            filter_result = evaluate_discovery_observation(record, now=now)
            identity_result = await evaluate_discovery_identity(db, record)
            decision_result = compose_discovery_decision(filter_result, identity_result)
            filter_metadata = {**filter_result["metadata"], "evaluated_at": filter_result["evaluated_at"], "decision_version": 1}
            project_record = {
                key: value for key, value in record.items()
                if not key.startswith("_") and key not in {"catalog_id", "source_url", "published_at"}
            }
            project_record["filter_status"] = decision_result["disposition"]
            project_record["filter_reasons"] = decision_result["reasons"]
            project_record["filter_metadata"] = filter_metadata
            project_record["project_identity_key"] = identity_result["project_identity_key"]
            project_record["campaign_identity_key"] = identity_result["campaign_identity_key"]
            project_record["identity_status"] = identity_result["identity_status"]
            project_record["identity_evidence"] = {key: identity_result[key] for key in ("project_identity_evidence", "campaign_identity_evidence", "matched_source_keys", "identity_conflict", "identity_group_type", "identity_group_key")}
            project_record["matched_source_key"] = identity_result["matched_source_key"]
            if record.get("catalog_id") is not None:
                project_record["description"] = f"{project_record.get('description', '')} Binance catalog ID: {record['catalog_id']}."
            current = await db.scalar(select(AirdropProject).where(AirdropProject.source_key == project_record['source_key']))
            if current is None: db.add(AirdropProject(**project_record))
            else:
                for key, value in project_record.items(): setattr(current, key, value)
                current.imported_at = now
            await _upsert_discovery_candidate(
                db,
                source_name=source_name,
                source_url=source_url,
                record=record,
                now=now,
                identity_result=identity_result,
                decision_result=decision_result,
            )
            if campaign_detail is not None:
                await _upsert_binance_airdrop_campaign(
                    db,
                    record=record,
                    detail=campaign_detail,
                    now=now,
                )
            candidate_key = _candidate_source_key(source_name, str(record.get("source_key") or ""))
            candidate = await db.scalar(
                select(AirdropDiscoveryCandidate).where(AirdropDiscoveryCandidate.source_key == candidate_key)
            )
            if candidate is not None:
                db.add(_build_discovery_observation(
                    candidate=candidate,
                    source_name=source_name,
                    source_record_key=str(record.get("source_key") or ""),
                    source_url=_valid_http_url(record.get("source_url")) or source_url,
                    record=record,
                    now=now,
                    filter_result=filter_result,
                    decision_result=decision_result,
                    identity_result=identity_result,
                ))
        await record_discovery_source_status(db, source_name, source_url, now, 'success', item_count=len(records))
        await db.commit()
        return len(records)
    except Exception as exc:
        await db.rollback()
        if status == "failed":
            error_code = f"source_sync_error:{type(exc).__name__}"
        await record_discovery_source_status(db, source_name, source_url, now, status, error=error_code)
        await db.commit()
        logger.warning('Discovery source %s %s; continuing: %s', source_name, status, type(exc).__name__)
        return 0

def _configured_discovery_sources() -> list[tuple[str, str, Any]]:
    """Return zero-key public sources by default; credentialed/approved sources are opt-in."""
    sources: list[tuple[str, str, Any]] = [
        ('dexscreener-new-pools', 'https://api.dexscreener.com/latest/dex/search', _fetch_dexscreener_observations),
        ('binance-announcements', BINANCE_CMS_SOURCE, _fetch_binance_observations),
    ]
    if os.getenv("COINGECKO_DEMO_API_KEY", "").strip():
        sources.append((
            'coingecko-airdrop-category',
            'https://api.coingecko.com/api/v3/coins/categories/list',
            _fetch_coingecko_observations,
        ))
    if os.getenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", "").strip():
        sources.append((
            'github-airdrop-lists',
            'https://raw.githubusercontent.com/',
            _fetch_github_observations,
        ))
    return sources


async def sync_airdrop_hub(db: AsyncSession) -> int:
    now = datetime.now(timezone.utc)
    _sync_status.update({'last_attempt_at': now.isoformat(), 'error': None})
    total = 0
    async with httpx.AsyncClient(timeout=httpx.Timeout(20.0), headers={'User-Agent': 'AirdropHunter/1.0', 'Accept': 'application/json'}) as client:
        for source_name, source_url, fetcher in _configured_discovery_sources():
            total += await _upsert_source_records(db, source_name, source_url, fetcher, client, now)
    if total == 0:
        _sync_status.update({'source': 'four-source-pipeline', 'error': '四个数据源本轮均未产生有效观察记录；已保留原有数据'})
        raise RuntimeError(_sync_status['error'])
    _sync_status.update({'last_success_at': now.isoformat(), 'imported_count': total, 'source': 'coingecko+github-raw+dexscreener+binance', 'error': None})
    return total

def get_sync_status() -> dict[str, Any]:
    return dict(_sync_status)
