"""Conservative, zero-key public DEX market enrichment for campaign records.

DexScreener is queried only by explicit chain ID + token contract address.
GeckoTerminal trending pools are fetched once per sync, never per campaign.
Provider failures preserve the last known campaign market fields.
"""
import asyncio
import json
import math
import time
from collections import defaultdict
from typing import Any, Awaitable, Callable

import httpx
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AirdropCampaign, AirdropCampaignMarketSnapshot

DEX_BASE_URL = "https://api.dexscreener.com"
GECKO_BASE_URL = "https://api.geckoterminal.com/api/v2"
GECKO_ACCEPT = "application/json;version=20230203"
MAX_RESPONSE_BYTES = 2 * 1024 * 1024
DEX_BATCH_SIZE = 30
DEX_MIN_INTERVAL_SECONDS = 2.0
GECKO_MIN_INTERVAL_SECONDS = 6.0
GECKO_CACHE_TTL_SECONDS = 300.0
_GECKO_TRENDING_CACHE: set[tuple[str, str]] | None = None
_GECKO_TRENDING_CACHE_AT = 0.0

# Canonical app network -> (DexScreener chainId, GeckoTerminal network id).
NETWORK_IDS: dict[str, tuple[str, str]] = {
    "ethereum": ("ethereum", "eth"),
    "eth": ("ethereum", "eth"),
    "solana": ("solana", "solana"),
    "base": ("base", "base"),
    "arbitrum": ("arbitrum", "arbitrum"),
    "optimism": ("optimism", "optimism"),
    "polygon": ("polygon", "polygon_pos"),
    "polygon_pos": ("polygon", "polygon_pos"),
    "bsc": ("bsc", "bsc"),
    "bnb": ("bsc", "bsc"),
    "avalanche": ("avalanche", "avax"),
    "avax": ("avalanche", "avax"),
    "sui": ("sui", "sui"),
}
EVM_NETWORKS = {"ethereum", "eth", "base", "arbitrum", "optimism", "polygon", "polygon_pos", "bsc", "bnb", "avalanche", "avax"}


def _canonical_network(value: str | None) -> str | None:
    key = str(value or "").strip().lower()
    if key in {"eth", "ethereum"}:
        return "ethereum"
    if key in {"polygon", "polygon_pos"}:
        return "polygon"
    if key in {"bnb", "bsc"}:
        return "bsc"
    if key in {"avax", "avalanche"}:
        return "avalanche"
    return key or None


def _address_key(network: str, address: str) -> str:
    return address.lower() if network.lower() in EVM_NETWORKS else address


def _finite_number(value: Any) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError, OverflowError):
        return None
    return number if math.isfinite(number) and number >= 0 else None


def build_dex_batches(campaigns: list[AirdropCampaign]) -> list[dict[str, Any]]:
    """Group exact contract addresses by supported network, at most 30 per call."""
    grouped: dict[str, dict[str, str]] = defaultdict(dict)
    for campaign in campaigns:
        raw_network = str(campaign.token_network or campaign.chain or "").strip().lower()
        ids = NETWORK_IDS.get(raw_network)
        address = str(campaign.token_contract_address or "").strip()
        if not ids or not address or len(address) > 160 or any(ch.isspace() for ch in address):
            continue
        dex_chain, _ = ids
        canonical = _canonical_network(raw_network) or dex_chain
        grouped[dex_chain][canonical + "|" + _address_key(canonical, address)] = address

    batches: list[dict[str, Any]] = []
    for dex_chain, keyed_addresses in sorted(grouped.items()):
        addresses = list(keyed_addresses.values())
        for offset in range(0, len(addresses), DEX_BATCH_SIZE):
            batches.append({
                "chain_id": dex_chain,
                "addresses": addresses[offset:offset + DEX_BATCH_SIZE],
            })
    return batches


def parse_dex_pairs(
    payload: Any,
    *,
    chain_id: str,
    requested_addresses: list[str],
) -> dict[tuple[str, str], dict[str, Any]]:
    """Parse only exact chain + base/quote contract matches from a successful response."""
    canonical = _canonical_network(chain_id) or chain_id
    requested = {
        _address_key(canonical, address): address
        for address in requested_addresses
    }
    pairs = payload.get("pairs", []) if isinstance(payload, dict) else payload
    if not isinstance(pairs, list):
        raise ValueError("DexScreener response has no pair list")

    matched: dict[str, dict[str, Any]] = {
        key: {
            "network": canonical,
            "contract_address": original,
            "pair_count": 0,
            "pair_address": None,
            "liquidity_usd": 0.0,
            "volume_24h_usd": 0.0,
            "_best_rank": (-1.0, -1.0),
            "_seen_pairs": set(),
        }
        for key, original in requested.items()
    }
    for pair in pairs:
        if not isinstance(pair, dict):
            continue
        if str(pair.get("chainId") or "").strip().lower() != chain_id.lower():
            continue
        pair_address = str(pair.get("pairAddress") or "").strip()
        if not pair_address:
            continue
        base = pair.get("baseToken") if isinstance(pair.get("baseToken"), dict) else {}
        quote = pair.get("quoteToken") if isinstance(pair.get("quoteToken"), dict) else {}
        for token in (base, quote):
            address = str(token.get("address") or "").strip()
            key = _address_key(canonical, address)
            result = matched.get(key)
            if result is None:
                continue
            if pair_address in result["_seen_pairs"]:
                continue
            result["_seen_pairs"].add(pair_address)
            result["pair_count"] += 1
            liquidity = _finite_number((pair.get("liquidity") or {}).get("usd")) if isinstance(pair.get("liquidity"), dict) else None
            volume = _finite_number((pair.get("volume") or {}).get("h24")) if isinstance(pair.get("volume"), dict) else None
            rank = (liquidity if liquidity is not None else -1.0, volume if volume is not None else -1.0)
            if rank > result["_best_rank"]:
                result["_best_rank"] = rank
                result["pair_address"] = pair_address
                result["liquidity_usd"] = liquidity
                result["volume_24h_usd"] = volume

    output: dict[tuple[str, str], dict[str, Any]] = {}
    for result in matched.values():
        result.pop("_best_rank", None)
        result.pop("_seen_pairs", None)
        address_key = _address_key(canonical, result["contract_address"])
        output[(canonical, address_key)] = result
    return output


def parse_gecko_trending_tokens(payload: Any) -> set[tuple[str, str]]:
    """Extract base/quote token identities from one global trending-pools response."""
    if not isinstance(payload, dict) or not isinstance(payload.get("data"), list):
        raise ValueError("GeckoTerminal response has no data list")
    tokens: set[tuple[str, str]] = set()

    def add_token_id(raw_id: Any) -> None:
        value = str(raw_id or "").strip()
        if "_" not in value:
            return
        network_id, address = value.split("_", 1)
        network = _canonical_network(network_id)
        if network and address:
            tokens.add((network, _address_key(network, address)))

    for pool in payload["data"]:
        if not isinstance(pool, dict):
            continue
        relationships = pool.get("relationships")
        if isinstance(relationships, dict):
            for key in ("base_token", "quote_token"):
                relation = relationships.get(key)
                data = relation.get("data") if isinstance(relation, dict) else None
                if isinstance(data, dict):
                    add_token_id(data.get("id"))
        included = pool.get("included")
        if isinstance(included, list):
            for item in included:
                if isinstance(item, dict) and item.get("type") == "token":
                    add_token_id(item.get("id"))
    included = payload.get("included")
    if isinstance(included, list):
        for item in included:
            if isinstance(item, dict) and item.get("type") == "token":
                add_token_id(item.get("id"))
    return tokens


async def _get_json(
    client: httpx.AsyncClient,
    url: str,
    *,
    headers: dict[str, str] | None = None,
    sleep: Callable[[float], Awaitable[Any]] = asyncio.sleep,
) -> Any:
    last_error: Exception | None = None
    for attempt in range(2):
        try:
            async with client.stream("GET", url, headers=headers) as response:
                if response.status_code in {408, 425, 429} or response.status_code >= 500:
                    if attempt == 0:
                        retry_after = _finite_number(response.headers.get("Retry-After"))
                        if response.status_code == 429:
                            delay = min(60.0, max(6.0, retry_after or 6.0))
                        else:
                            delay = min(6.0, retry_after if retry_after is not None else 1.0)
                        await sleep(delay)
                        continue
                response.raise_for_status()
                declared_size = _finite_number(response.headers.get("Content-Length"))
                if declared_size is not None and declared_size > MAX_RESPONSE_BYTES:
                    raise ValueError("Public market response exceeded the size limit")
                body = bytearray()
                async for chunk in response.aiter_bytes():
                    body.extend(chunk)
                    if len(body) > MAX_RESPONSE_BYTES:
                        raise ValueError("Public market response exceeded the size limit")
                return json.loads(body)
        except (httpx.TransportError, httpx.HTTPStatusError) as error:
            last_error = error
            status = error.response.status_code if isinstance(error, httpx.HTTPStatusError) else None
            retryable = status is None or status in {408, 425, 429} or (status is not None and status >= 500)
            if attempt == 0 and retryable:
                await sleep(1.0)
                continue
            raise
    if last_error:
        raise last_error
    raise RuntimeError("Public market request failed")

async def collect_market_signals(
    campaigns: list[AirdropCampaign],
    *,
    client: httpx.AsyncClient | None = None,
    sleep: Callable[[float], Awaitable[Any]] = asyncio.sleep,
) -> tuple[dict[tuple[str, str], dict[str, Any]], set[tuple[str, str]] | None]:
    """Fetch DEX metrics in batched calls and Gecko trending once per run.

    A None trending set means Gecko failed and callers must preserve prior values.
    """
    owns_client = client is None
    active_client = client or httpx.AsyncClient(
        timeout=httpx.Timeout(10.0, connect=5.0),
        limits=httpx.Limits(max_connections=4, max_keepalive_connections=2),
        follow_redirects=False,
    )
    dex_results: dict[tuple[str, str], dict[str, Any]] = {}
    try:
        batches = build_dex_batches(campaigns)
        if not batches:
            return dex_results, None
        for index, batch in enumerate(batches):
            if index:
                await sleep(DEX_MIN_INTERVAL_SECONDS)
            url = (
                f"{DEX_BASE_URL}/tokens/v1/{batch['chain_id']}/"
                + ",".join(batch["addresses"])
            )
            try:
                payload = await _get_json(active_client, url, sleep=sleep)
                dex_results.update(parse_dex_pairs(
                    payload,
                    chain_id=batch["chain_id"],
                    requested_addresses=batch["addresses"],
                ))
            except (httpx.HTTPError, ValueError, TypeError):
                # Preserve last-known-good values for this failed network batch.
                continue

        global _GECKO_TRENDING_CACHE, _GECKO_TRENDING_CACHE_AT
        trending_tokens: set[tuple[str, str]] | None = None
        now = time.monotonic()
        if (
            _GECKO_TRENDING_CACHE is not None
            and now - _GECKO_TRENDING_CACHE_AT < GECKO_CACHE_TTL_SECONDS
        ):
            trending_tokens = set(_GECKO_TRENDING_CACHE)
        else:
            try:
                await sleep(GECKO_MIN_INTERVAL_SECONDS)
                payload = await _get_json(
                    active_client,
                    f"{GECKO_BASE_URL}/networks/trending_pools",
                    headers={"Accept": GECKO_ACCEPT},
                    sleep=sleep,
                )
                trending_tokens = parse_gecko_trending_tokens(payload)
                _GECKO_TRENDING_CACHE = set(trending_tokens)
                _GECKO_TRENDING_CACHE_AT = time.monotonic()
            except (httpx.HTTPError, ValueError, TypeError):
                trending_tokens = None
        return dex_results, trending_tokens
    finally:
        if owns_client:
            await active_client.aclose()


async def refresh_campaign_market_data(
    db: AsyncSession,
    *,
    client: httpx.AsyncClient | None = None,
    sleep: Callable[[float], Awaitable[Any]] = asyncio.sleep,
) -> dict[str, int]:
    """Persist successful market observations without clearing data on provider errors."""
    campaigns = list(await db.scalars(select(AirdropCampaign).where(
        AirdropCampaign.token_contract_address.is_not(None),
        AirdropCampaign.token_network.is_not(None),
    )))
    dex_results, trending_tokens = await collect_market_signals(
        campaigns, client=client, sleep=sleep
    )
    dex_updated = 0
    gecko_updated = 0
    for campaign in campaigns:
        raw_network = str(campaign.token_network or "").strip().lower()
        ids = NETWORK_IDS.get(raw_network)
        address = str(campaign.token_contract_address or "").strip()
        if not ids or not address:
            continue
        canonical = _canonical_network(raw_network) or ids[0]
        key = (canonical, _address_key(canonical, address))
        dex = dex_results.get(key)
        if dex is not None:
            campaign.dex_pair_count = dex["pair_count"]
            campaign.dex_liquidity_usd = dex["liquidity_usd"]
            campaign.dex_volume_24h_usd = dex["volume_24h_usd"]
            db.add(AirdropCampaignMarketSnapshot(
                campaign_id=campaign.id,
                provider="dexscreener",
                network=canonical,
                contract_address=address,
                pair_address=dex["pair_address"],
                liquidity_usd=dex["liquidity_usd"],
                volume_24h_usd=dex["volume_24h_usd"],
            ))
            dex_updated += 1
        if trending_tokens is not None:
            is_trending = key in trending_tokens
            campaign.gecko_trending = is_trending
            db.add(AirdropCampaignMarketSnapshot(
                campaign_id=campaign.id,
                provider="geckoterminal",
                network=canonical,
                contract_address=address,
                is_trending=is_trending,
            ))
            gecko_updated += 1
    await db.commit()
    return {
        "campaigns_considered": len(campaigns),
        "dex_updated": dex_updated,
        "gecko_updated": gecko_updated,
    }
