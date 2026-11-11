import hashlib
import json
import logging
import os
import re
from datetime import datetime, timezone
from typing import Any
from urllib.parse import unquote, urlparse

import httpx
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AirdropDiscoveryCandidate, AirdropDiscoveryObservation, AirdropProject
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
            records.append({
                "source_key": f"binance:{safe_code}"[:240],
                "name": title[:180],
                "chain": "Unknown",
                "description": "Binance 公告线索；请打开原文核实活动资格、时间与参与条件。",
                "status": "待核实",
                "link": f"https://www.binance.com/en/support/announcement/{safe_code}",
                "source_name": "binance-announcements",
                "trust_level": TRUST_LEVEL_0,
                "publication_status": OBSERVATION,
                "catalog_id": catalog_id,
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


async def fetch_binance_article_detail(article_code: str, *, fallback_title: str, fallback_url: str) -> dict[str, Any]:
    safe_code = re.sub(r"[^A-Za-z0-9_-]", "", article_code or "")
    if not safe_code or safe_code != article_code:
        raise ValueError("Invalid Binance article code")
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(20.0),
        headers={
            "User-Agent": "AirdropHunter/1.0",
            "Accept": "application/json",
            "Origin": "https://www.binance.com",
            "clienttype": "web",
            "lang": "en",
        },
    ) as client:
        response = await client.get(BINANCE_CMS_DETAIL_SOURCE, params={"articleCode": safe_code})
        response.raise_for_status()
        if len(response.content) > 3_000_000:
            raise ValueError("Binance article exceeds the 3 MB display limit")
        return _normalize_binance_article_detail(
            response.json(), fallback_title=fallback_title, fallback_url=fallback_url
        )


async def _fetch_binance_observations(client: httpx.AsyncClient) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    errors: list[str] = []
    headers = {
        "Origin": "https://www.binance.com",
        "Referer": "https://www.binance.com/en/messages/v2/group/announcement",
        "clienttype": "web",
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
                    params={"type": 1, "pageSize": 30, "pageNo": 1, "catalogId": catalog_id},
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
                logger.warning(
                    "Binance catalog %s endpoint %s failed: %s",
                    catalog_id,
                    urlparse(endpoint).path,
                    type(exc).__name__,
                )
        if last_error is not None:
            errors.append(f"catalog {catalog_id}: {type(last_error).__name__}")
    if not records and errors:
        raise RuntimeError("Binance CMS categories unavailable: " + "; ".join(errors))
    return records


def _observation_record(source_name: str, source_key: str, name: str, link: str, description: str, chain: str = 'Unknown', status: str = '待核实') -> dict[str, str]:
    return {'source_key': source_key[:240], 'name': name[:180], 'chain': chain[:80], 'description': description[:10000], 'status': status[:40], 'link': link[:1000], 'source_name': source_name, 'trust_level': TRUST_LEVEL_0, 'publication_status': OBSERVATION}

def _normalize_coingecko_categories(payload: Any) -> list[dict[str, str]]:
    if not isinstance(payload, list): return []
    return [{"id": str(item.get("id") or "").strip(), "name": str(item.get("name") or "").strip()} for item in payload if isinstance(item, dict) and item.get("id") and item.get("name")]

def _normalize_coingecko_coins(payload: Any, category_id: str) -> list[dict[str, Any]]:
    if not isinstance(payload, list): return []
    rows = []
    for item in payload:
        if not isinstance(item, dict) or not item.get("id") or not item.get("name"):
            continue
        row = _observation_record("coingecko-airdrop-category", f"coingecko:{category_id}:{item['id']}", str(item["name"]), f"https://www.coingecko.com/en/coins/{item['id']}", "CoinGecko Airdrop 类目代币线索；不代表当前存在可领取活动。")
        row["_raw_payload"] = item
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

async def _fetch_coingecko_observations(client: httpx.AsyncClient) -> list[dict[str, str]]:
    key = os.getenv('COINGECKO_DEMO_API_KEY', '').strip()
    if not key: raise RuntimeError('COINGECKO_DEMO_API_KEY 未配置')
    auth_headers = {'x-cg-demo-api-key': key}
    response = await client.get('https://api.coingecko.com/api/v3/coins/categories', headers=auth_headers)
    response.raise_for_status()
    categories = _normalize_coingecko_categories(response.json())
    from app.services.airdrop_discovery_filter import select_coingecko_airdrop_categories
    category_ids = select_coingecko_airdrop_categories(categories)
    if not category_ids: raise RuntimeError('CoinGecko 未返回 Airdrop 类目')
    rows = []
    for category_id in category_ids:
        response = await client.get('https://api.coingecko.com/api/v3/coins/markets', headers=auth_headers, params={'vs_currency': 'usd', 'category': category_id, 'order': 'market_cap_desc', 'per_page': 250, 'page': 1, 'sparkline': 'false'})
        response.raise_for_status()
        category_rows = _normalize_coingecko_coins(response.json(), category_id)
        for row in category_rows:
            row["source_url"] = str(response.request.url)
        rows.extend(category_rows)
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

async def _fetch_dexscreener_observations(client: httpx.AsyncClient) -> list[dict[str, Any]]:
    queries = list(dict.fromkeys(
        part.strip()
        for part in os.getenv("DEXSCREENER_SEARCH_QUERIES", "SOL,ETH,USDC").split(",")
        if part.strip()
    ))[:8]
    if not queries:
        raise RuntimeError("DEXSCREENER_SEARCH_QUERIES 未配置有效搜索词")

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
            failures.append(f"{query}:{type(exc).__name__}")
            logger.warning(
                "DexScreener search %s failed; continuing with remaining terms: %s",
                query,
                type(exc).__name__,
            )

    if not rows:
        if failures:
            raise RuntimeError(
                "DexScreener 所有搜索词均失败或未产生有效记录: " + "; ".join(failures)
            )
        raise RuntimeError("DexScreener 本轮没有返回可解析的交易池记录")
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
    try:
        records = await fetcher(client)
        if not records: raise RuntimeError('empty source result')
        for record in records:
            from app.services.airdrop_discovery_filter import compose_discovery_decision, evaluate_discovery_identity, evaluate_discovery_observation
            filter_result = evaluate_discovery_observation(record, now=now)
            identity_result = await evaluate_discovery_identity(db, record)
            decision_result = compose_discovery_decision(filter_result, identity_result)
            filter_metadata = {**filter_result["metadata"], "evaluated_at": filter_result["evaluated_at"], "decision_version": 1}
            project_record = {
                key: value for key, value in record.items()
                if not key.startswith("_") and key not in {"catalog_id", "source_url"}
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
        await record_discovery_source_status(db, source_name, source_url, now, 'failed', error=type(exc).__name__)
        await db.commit()
        logger.warning('Discovery source %s failed; continuing: %s', source_name, type(exc).__name__)
        return 0

async def sync_airdrop_hub(db: AsyncSession) -> int:
    now = datetime.now(timezone.utc)
    _sync_status.update({'last_attempt_at': now.isoformat(), 'error': None})
    total = 0
    async with httpx.AsyncClient(timeout=httpx.Timeout(20.0), headers={'User-Agent': 'AirdropHunter/1.0', 'Accept': 'application/json'}) as client:
        sources = [
            ('coingecko-airdrop-category', 'https://api.coingecko.com/api/v3/coins/categories', _fetch_coingecko_observations),
            ('github-airdrop-lists', 'https://raw.githubusercontent.com/', _fetch_github_observations),
            ('dexscreener-new-pools', 'https://api.dexscreener.com/latest/dex/search', _fetch_dexscreener_observations),
            ('binance-announcements', BINANCE_CMS_SOURCE, _fetch_binance_observations),
        ]
        for source_name, source_url, fetcher in sources:
            total += await _upsert_source_records(db, source_name, source_url, fetcher, client, now)
    if total == 0:
        _sync_status.update({'source': 'four-source-pipeline', 'error': '四个数据源本轮均未产生有效观察记录；已保留原有数据'})
        raise RuntimeError(_sync_status['error'])
    _sync_status.update({'last_success_at': now.isoformat(), 'imported_count': total, 'source': 'coingecko+github-raw+dexscreener+binance', 'error': None})
    return total

def get_sync_status() -> dict[str, Any]:
    return dict(_sync_status)
