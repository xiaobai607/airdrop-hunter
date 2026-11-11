import hashlib
import json
import logging
import os
import re
from datetime import datetime, timezone
from typing import Any
from urllib.parse import urlparse

import httpx
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AirdropProject
from app.services.discovery_source_status import record_discovery_source_status

logger = logging.getLogger("airdrop_hunter.airdrop_sync")

JSON_SOURCE = "https://web3-discover.vercel.app/api/airdrops.json"
MCP_SOURCE = "https://web3-discover.vercel.app/api/mcp"

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
    parsed = urlparse(candidate)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        return None
    return candidate[:1000]


def _status_label(value: Any) -> str:
    raw = str(value or "").strip().lower().replace("_", " ").replace("-", " ")
    if any(token in raw for token in ("hot", "popular", "trending")):
        return "热门"
    if any(token in raw for token in ("upcoming", "pre tge", "pre token", "待发币")):
        return "待发币"
    if any(token in raw for token in ("active", "ongoing", "live", "进行中")):
        return "进行中"
    if any(token in raw for token in ("ended", "expired", "closed", "已结束")):
        return "已结束"
    return str(value or "待确认")[:40]


def _as_items(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if not isinstance(payload, dict):
        return []
    for key in ("airdrops", "data", "projects", "items", "results"):
        value = payload.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
        if isinstance(value, dict):
            nested = _as_items(value)
            if nested:
                return nested
    return []


def _normalize(payload: Any, *, source_name: str = "web3-discover") -> list[dict[str, str]]:
    normalized: list[dict[str, str]] = []
    trust_level, publication_status = _source_publication_state(source_name)
    for item in _as_items(payload):
        name = str(item.get("project") or item.get("name") or item.get("title") or item.get("project_name") or "").strip()
        link = _valid_http_url(item.get("officialUrl") or item.get("official_url") or item.get("website") or item.get("link") or item.get("url"))
        if not name or not link:
            continue
        description = str(item.get("blurb") or item.get("short_description") or item.get("description") or item.get("summary") or "").strip()
        chains = item.get("blockchains") or item.get("chains") or item.get("chain") or item.get("blockchain") or "Unknown"
        if isinstance(chains, str):
            chain_values = [part.strip() for part in re.split(r"[,;/|]", chains) if part.strip()]
        elif isinstance(chains, list):
            chain_values = [str(part).strip() for part in chains if str(part).strip()]
        else:
            chain_values = ["Unknown"]
        if not chain_values:
            chain_values = ["Unknown"]
        status = _status_label(item.get("status"))
        source_id = str(item.get("slug") or item.get("id") or name.lower().replace(" ", "-"))
        for chain in chain_values:
            key = f"{source_id}:{chain.lower()}"
            normalized.append({
                "source_key": key[:240],
                "name": name[:180],
                "chain": chain[:80],
                "description": description[:10000],
                "status": status,
                "link": link,
                "source_name": source_name,
                "trust_level": trust_level,
                "publication_status": publication_status,
            })
    return normalized


async def _fetch_source(client: httpx.AsyncClient) -> tuple[list[dict[str, str]], str]:
    errors: list[str] = []
    try:
        response = await client.get(JSON_SOURCE)
        response.raise_for_status()
        records = _normalize(response.json())
        if records:
            return records, JSON_SOURCE
        errors.append("JSON 数据源返回空数据或格式不受支持")
    except (httpx.HTTPError, ValueError) as exc:
        errors.append(f"JSON 数据源失败：{type(exc).__name__}")

    # The same public directory also exposes a no-key MCP tool endpoint.
    try:
        response = await client.post(
            MCP_SOURCE,
            json={
                "jsonrpc": "2.0",
                "id": 1,
                "method": "tools/call",
                "params": {"name": "list_active_airdrops", "arguments": {"limit": 100, "sort_by": "added"}},
            },
        )
        response.raise_for_status()
        body = response.json()
        content = ((body.get("result") or {}).get("content") or [])
        text_payload = next((part.get("text") for part in content if isinstance(part, dict) and part.get("text")), None)
        if text_payload:
            try:
                parsed = json.loads(text_payload)
            except ValueError:
                parsed = None
            records = _normalize(parsed if parsed is not None else _extract_json(text_payload))
            if records:
                return records, MCP_SOURCE
        errors.append("MCP 数据源没有返回可识别的项目列表")
    except (httpx.HTTPError, ValueError, TypeError) as exc:
        errors.append(f"MCP 数据源失败：{type(exc).__name__}")

    raise RuntimeError("；".join(errors))


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
            if not safe_code:
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


def _extract_json(text: str) -> Any:
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        try:
            return json.loads(text[start:end + 1])
        except ValueError:
            return None
    return None


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
            url = value.get('website') or value.get('official_url') or value.get('officialUrl') or value.get('url')
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
    try: urls = json.loads(os.getenv('AIRDROP_GITHUB_RAW_SOURCES', '[]'))
    except ValueError: urls = []
    if not isinstance(urls, list): urls = []
    urls = list(dict.fromkeys(url.strip() for url in urls if isinstance(url, str) and url.startswith('https://raw.githubusercontent.com/')))[:20]
    if not urls: raise RuntimeError('AIRDROP_GITHUB_RAW_SOURCES 未配置有效 Raw URL')
    rows, failures = [], 0
    for url in urls:
        try:
            response = await client.get(url, headers={'Accept': 'application/json, text/plain'})
            response.raise_for_status()
            try: payload = response.json()
            except ValueError: payload = response.text
            rows.extend(_normalize_github_airdrop_list(payload, url))
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            failures += 1
            logger.warning('GitHub Raw source failed (%s): %s', url, type(exc).__name__)
    if not rows: raise RuntimeError(f'GitHub Raw 未解析到项目；失败文件数 {failures}')
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
