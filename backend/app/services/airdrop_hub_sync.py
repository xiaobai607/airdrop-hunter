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

from app.models import AirdropProject
from app.services.discovery_source_status import record_discovery_source_status

logger = logging.getLogger("airdrop_hunter.airdrop_sync")

BINANCE_CMS_SOURCE = "https://www.binance.com/bapi/composite/v1/public/cms/article/list/query"
BINANCE_CATALOGS = (128, 93)
BINANCE_AIRDROP_KEYWORDS = re.compile(r"airdrop|launchpool|hodler|megadrop", re.IGNORECASE)

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


def _normalize_binance_announcements(payload: Any, *, catalog_id: int) -> list[dict[str, str]]:
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
            if catalog_id != 128 and not BINANCE_AIRDROP_KEYWORDS.search(title):
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


async def _fetch_binance_observations(client: httpx.AsyncClient) -> list[dict[str, str]]:
    by_key: dict[str, dict[str, str]] = {}
    errors: list[str] = []
    for catalog_id in BINANCE_CATALOGS:
        try:
            response = await client.get(
                BINANCE_CMS_SOURCE,
                params={"type": 1, "pageSize": 30, "pageNo": 1, "catalogId": catalog_id},
                headers={"Origin": "https://www.binance.com", "clienttype": "web", "lang": "en"},
            )
            response.raise_for_status()
            rows = _normalize_binance_announcements(response.json(), catalog_id=catalog_id)
            for row in rows:
                by_key[row["source_key"]] = row
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            errors.append(f"catalog {catalog_id}: {type(exc).__name__}")
            logger.warning("Binance catalog %s failed: %s", catalog_id, exc)
    if not by_key and errors:
        raise RuntimeError("Binance CMS categories unavailable: " + "; ".join(errors))
    return list(by_key.values())


def _observation_record(source_name: str, source_key: str, name: str, link: str, description: str, chain: str = 'Unknown', status: str = '待核实') -> dict[str, str]:
    return {'source_key': source_key[:240], 'name': name[:180], 'chain': chain[:80], 'description': description[:10000], 'status': status[:40], 'link': link[:1000], 'source_name': source_name, 'trust_level': TRUST_LEVEL_0, 'publication_status': OBSERVATION}

def _normalize_coingecko_categories(payload: Any) -> list[str]:
    if not isinstance(payload, list): return []
    return [str(item.get('id') or '').strip() for item in payload if isinstance(item, dict) and 'airdrop' in str(item.get('name') or '').casefold() and item.get('id')]

def _normalize_coingecko_coins(payload: Any, category_id: str) -> list[dict[str, str]]:
    if not isinstance(payload, list): return []
    return [_observation_record('coingecko-airdrop-category', f"coingecko:{category_id}:{item['id']}", str(item['name']), f"https://www.coingecko.com/en/coins/{item['id']}", 'CoinGecko Airdrop 类目代币线索；不代表当前存在可领取活动。') for item in payload if isinstance(item, dict) and item.get('id') and item.get('name')]

def _normalize_github_airdrop_list(payload: Any, source_url: str) -> list[dict[str, str]]:
    found: list[tuple[str, str]] = []
    def walk(value: Any) -> None:
        if isinstance(value, list):
            for entry in value: walk(entry)
        elif isinstance(value, dict):
            name = value.get('name') or value.get('project') or value.get('title')
            # Generic "url" fields often point to repositories or list entries, not
            # the project's official website. Require an explicit official-site field.
            url = value.get('website') or value.get('official_url') or value.get('officialUrl')
            safe_url = _valid_http_url(url)
            if name and safe_url: found.append((str(name).strip(), safe_url))
            else:
                for child in value.values():
                    if isinstance(child, (dict, list)): walk(child)
    if isinstance(payload, (dict, list)): walk(payload)
    elif isinstance(payload, str):
        for line in payload.splitlines():
            match = re.search(r'\[([^\]]{2,180})\]\((https://[^)\s]+)\)', line)
            if match: found.append((match.group(1).strip(), match.group(2)))
    result = []
    for name, url in found:
        digest = hashlib.sha256(f'{source_url}|{name.casefold()}|{url}'.encode()).hexdigest()[:24]
        result.append(_observation_record('github-airdrop-lists', f'github:{digest}', name, url, f'来自开源名单 {source_url}；项目状态及官网真实性待核实。'))
    return result

def _normalize_dexscreener_pairs(payload: Any, query: str) -> list[dict[str, str]]:
    pairs = payload.get('pairs') if isinstance(payload, dict) else None
    if not isinstance(pairs, list): return []
    now = datetime.now(timezone.utc).timestamp()
    rows = []
    for pair in pairs:
        if not isinstance(pair, dict): continue
        try:
            age = now - float(pair.get('pairCreatedAt')) / 1000
            liquidity = float((pair.get('liquidity') or {}).get('usd') or 0)
            volume = float((pair.get('volume') or {}).get('h24') or 0)
            tx = (pair.get('txns') or {}).get('h24') or {}
            trades = int(tx.get('buys') or 0) + int(tx.get('sells') or 0)
        except (TypeError, ValueError, AttributeError): continue
        if age < 0 or age > 604800 or liquidity < 10000 or volume < 50000 or trades < 20: continue
        token = pair.get('baseToken') or {}
        name, address, chain = str(token.get('name') or '').strip(), str(token.get('address') or '').strip(), str(pair.get('chainId') or 'Unknown')
        url = _valid_http_url(pair.get('url'))
        if not name or not address or not url: continue
        symbol = str(token.get('symbol') or '')
        desc = f'DexScreener 近期交易池线索（搜索词 {query}）；24h 成交量 ${volume:,.0f}，流动性 ${liquidity:,.0f}，24h 买卖笔数 {trades}。交易活跃不代表空投或安全。'
        rows.append(_observation_record('dexscreener-new-pools', f'dexscreener:{chain}:{address}'.lower(), f'{name} ({symbol})' if symbol else name, url, desc, chain, '交易活跃·待核实'))
    return rows

async def _fetch_coingecko_observations(client: httpx.AsyncClient) -> list[dict[str, str]]:
    key = os.getenv('COINGECKO_DEMO_API_KEY', '').strip()
    if not key: raise RuntimeError('COINGECKO_DEMO_API_KEY 未配置')
    response = await client.get('https://api.coingecko.com/api/v3/coins/categories', params={'x_cg_demo_api_key': key})
    response.raise_for_status()
    category_ids = _normalize_coingecko_categories(response.json())
    if not category_ids: raise RuntimeError('CoinGecko 未返回 Airdrop 类目')
    rows = []
    for category_id in category_ids[:3]:
        response = await client.get('https://api.coingecko.com/api/v3/coins/markets', params={'x_cg_demo_api_key': key, 'vs_currency': 'usd', 'category': category_id, 'order': 'market_cap_desc', 'per_page': 250, 'page': 1, 'sparkline': 'false'})
        response.raise_for_status()
        rows.extend(_normalize_coingecko_coins(response.json(), category_id))
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

async def _fetch_dexscreener_observations(client: httpx.AsyncClient) -> list[dict[str, str]]:
    queries = list(dict.fromkeys(part.strip() for part in os.getenv('DEXSCREENER_SEARCH_QUERIES', 'SOL,ETH,USDC').split(',') if part.strip()))[:8]
    rows = {}
    for query in queries:
        response = await client.get('https://api.dexscreener.com/latest/dex/search', params={'q': query})
        response.raise_for_status()
        for row in _normalize_dexscreener_pairs(response.json(), query): rows[row['source_key']] = row
    if not rows: raise RuntimeError('DexScreener 没有符合新池活跃阈值的交易对')
    return list(rows.values())

async def _upsert_source_records(db: AsyncSession, source_name: str, source_url: str, fetcher: Any, client: httpx.AsyncClient, now: datetime) -> int:
    try:
        records = await fetcher(client)
        if not records: raise RuntimeError('empty source result')
        for record in records:
            current = await db.scalar(select(AirdropProject).where(AirdropProject.source_key == record['source_key']))
            if current is None: db.add(AirdropProject(**record))
            else:
                for key, value in record.items(): setattr(current, key, value)
                current.imported_at = now
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
