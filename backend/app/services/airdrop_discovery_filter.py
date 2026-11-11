"""Centralized decisions for raw multi-provider airdrop observations."""
from __future__ import annotations
import re
from datetime import datetime, timezone
from typing import Any

BINANCE_AIRDROP_KEYWORDS = re.compile(r"airdrop|launchpool|hodler|megadrop", re.IGNORECASE)
DEXSCREENER_MAX_POOL_AGE_SECONDS = 604800
DEXSCREENER_MIN_LIQUIDITY_USD = 10000
DEXSCREENER_MIN_VOLUME_24H_USD = 50000
DEXSCREENER_MIN_TRADES_24H = 20


def select_coingecko_airdrop_categories(categories: list[dict[str, str]]) -> list[str]:
    return [item["id"] for item in categories if item.get("id") and "airdrop" in str(item.get("name") or "").casefold()]


def evaluate_discovery_observation(record: dict[str, Any], *, now: datetime | None = None) -> dict[str, Any]:
    source_name = str(record.get("source_name") or "")
    metadata = dict(record.get("_filter_metadata") or {})
    reasons: list[str] = []
    if source_name == "binance-announcements":
        catalog_id = record.get("catalog_id")
        if catalog_id != 128 and not BINANCE_AIRDROP_KEYWORDS.search(str(record.get("name") or "")):
            reasons.append("binance_activity_title_not_airdrop_related")
        metadata["catalog_id"] = catalog_id
    elif source_name == "dexscreener-new-pools":
        age, liquidity = metadata.get("age_seconds"), metadata.get("liquidity_usd")
        volume, trades = metadata.get("volume_24h_usd"), metadata.get("trades_24h")
        if age is None: reasons.append("pool_age_missing")
        elif age < 0 or age > DEXSCREENER_MAX_POOL_AGE_SECONDS: reasons.append("pool_age_outside_existing_limit")
        if liquidity is None: reasons.append("liquidity_missing")
        elif liquidity < DEXSCREENER_MIN_LIQUIDITY_USD: reasons.append("liquidity_below_existing_limit")
        if volume is None: reasons.append("volume_24h_missing")
        elif volume < DEXSCREENER_MIN_VOLUME_24H_USD: reasons.append("volume_24h_below_existing_limit")
        if trades is None: reasons.append("trades_24h_missing")
        elif trades < DEXSCREENER_MIN_TRADES_24H: reasons.append("trades_24h_below_existing_limit")
    return {"status": "filtered" if reasons else "eligible_for_review", "reasons": reasons, "metadata": metadata, "evaluated_at": (now or datetime.now(timezone.utc)).isoformat()}
