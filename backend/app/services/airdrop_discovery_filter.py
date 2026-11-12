"""Centralized decisions for raw multi-provider airdrop observations."""
from __future__ import annotations
import hashlib
import re
from datetime import datetime, timezone, timedelta
from typing import Any
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from sqlalchemy import or_, select

BINANCE_AIRDROP_KEYWORDS = re.compile(r"airdrop|launchpool|hodler|megadrop", re.IGNORECASE)
DEXSCREENER_MAX_POOL_AGE_SECONDS = 604800
DEXSCREENER_MIN_LIQUIDITY_USD = 10000


def _parse_source_timestamp(value: Any) -> datetime | None:
    if isinstance(value, (int, float)):
        numeric = float(value)
        if numeric > 10_000_000_000:
            numeric /= 1000
        try:
            return datetime.fromtimestamp(numeric, timezone.utc)
        except (OverflowError, OSError, ValueError):
            return None
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        parsed = datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def is_source_fresh(payload: Any, *, now: datetime | None = None, max_age_days: int = 7) -> bool:
    """Fail closed if any upstream generated/updated timestamp is stale or malformed."""
    current = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    cutoff = current - timedelta(days=max(1, int(max_age_days)))
    timestamp_keys = {"generatedat", "updatedat", "generated_at", "updated_at", "last_updated"}

    def walk(value: Any) -> bool:
        if isinstance(value, dict):
            for key, child in value.items():
                normalized = str(key).replace("-", "").casefold()
                if normalized in timestamp_keys:
                    parsed = _parse_source_timestamp(child)
                    if parsed is None or parsed < cutoff or parsed > current + timedelta(minutes=5):
                        return False
                if isinstance(child, (dict, list)) and not walk(child):
                    return False
        elif isinstance(value, list):
            for child in value:
                if isinstance(child, (dict, list)) and not walk(child):
                    return False
        return True

    return walk(payload)


def select_dexscreener_top_candidates(records: list[dict[str, Any]], *, limit: int = 10) -> list[dict[str, Any]]:
    """Central filter selection: top ten market signals with liquidity strictly over $10k."""
    eligible = []
    for record in records:
        metadata = dict(record.get("_filter_metadata") or {})
        try:
            liquidity = float(metadata.get("liquidity_usd"))
        except (TypeError, ValueError):
            continue
        age = metadata.get("age_seconds")
        if age is not None and (age < 0 or age > DEXSCREENER_MAX_POOL_AGE_SECONDS):
            continue
        if liquidity > 10_000:
            eligible.append(record)
    eligible.sort(key=lambda item: (
        float((item.get("_filter_metadata") or {}).get("liquidity_usd") or 0),
        float((item.get("_filter_metadata") or {}).get("volume_24h_usd") or 0),
        int((item.get("_filter_metadata") or {}).get("trades_24h") or 0),
    ), reverse=True)
    return eligible[:max(1, min(int(limit), 10))]



def select_coingecko_airdrop_categories(categories: list[dict[str, str]], *, limit: int = 3) -> list[str]:
    """Select the bounded set of Airdrop categories centrally, not in the provider adapter."""
    selected = [
        item["id"] for item in categories
        if item.get("id") and (
            str(item.get("id")).casefold() in {"airdrop", "airdrop-hunter"}
            or "airdrop" in str(item.get("name") or "").casefold()
        )
    ]
    return selected[:max(1, min(int(limit), 20))]


def evaluate_discovery_observation(record: dict[str, Any], *, now: datetime | None = None) -> dict[str, Any]:
    source_name = str(record.get("source_name") or "")
    metadata = dict(record.get("_filter_metadata") or {})
    reasons: list[str] = []
    if source_name == "binance-announcements":
        catalog_id = record.get("catalog_id")
        if catalog_id != 128 and not BINANCE_AIRDROP_KEYWORDS.search(str(record.get("name") or "")):
            reasons.append("binance_activity_title_not_airdrop_related")
        metadata["catalog_id"] = catalog_id
        published_at = record.get("published_at")
        if isinstance(published_at, datetime):
            metadata["published_at"] = published_at.isoformat()
    elif source_name == "github-airdrop-lists":
        official_url = metadata.get("official_url")
        if not official_url:
            reason = "github_official_url_invalid" if metadata.get("official_url_raw") else "github_official_url_missing"
            reasons.append(reason)
        elif _canonical_identity_url(official_url) is None:
            reasons.append("github_official_url_invalid")
    elif source_name == "coingecko-airdrop-category":
        metadata["market_signal_only"] = True
    elif source_name == "dexscreener-new-pools":
        metadata["market_signal_only"] = True
        age, liquidity = metadata.get("age_seconds"), metadata.get("liquidity_usd")
        if age is not None and (age < 0 or age > DEXSCREENER_MAX_POOL_AGE_SECONDS):
            reasons.append("pool_age_outside_existing_limit")
        if liquidity is None: reasons.append("liquidity_missing")
        elif liquidity <= DEXSCREENER_MIN_LIQUIDITY_USD:
            reasons.append("liquidity_not_above_10000_usd")
        # Volume and transactions are ranking evidence, not proof of an airdrop.
    return {"status": "filtered" if reasons else "eligible_for_review", "reasons": reasons, "metadata": metadata, "evaluated_at": (now or datetime.now(timezone.utc)).isoformat()}


def compose_discovery_decision(filter_result: dict[str, Any], identity_result: dict[str, Any]) -> dict[str, Any]:
    """Combine content filtering and identity evidence into one explainable disposition.

    'keep' means retain as an observation for human review; it never means publish.
    """
    reasons = list(filter_result.get("reasons") or [])
    identity_conflict = bool(identity_result.get("identity_conflict"))
    if identity_conflict:
        reasons.append("project_campaign_identity_conflict_needs_manual_review")
    if filter_result.get("status") == "filtered":
        disposition = "filtered"
    elif identity_conflict:
        disposition = "needs_review"
    else:
        disposition = "keep"
    return {
        **filter_result,
        "disposition": disposition,
        "reasons": reasons,
        "identity_status": identity_result.get("identity_status"),
        "identity_group_type": identity_result.get("identity_group_type"),
        "identity_group_key": identity_result.get("identity_group_key"),
    }


def _canonical_identity_url(value: Any) -> str | None:
    if not isinstance(value, str): return None
    try:
        parsed = urlsplit(value.strip())
        if parsed.scheme.lower() != "https" or not parsed.hostname or parsed.username or parsed.password: return None
        if parsed.port not in (None, 443): return None
        host = parsed.hostname.lower().rstrip(".")
        path = parsed.path or "/"
        if path != "/": path = path.rstrip("/")
        query = urlencode(sorted((k, v) for k, v in parse_qsl(parsed.query, keep_blank_values=True) if not k.lower().startswith("utm_") and k.lower() not in {"ref", "source"}))
        return urlunsplit(("https", host, path, query, ""))
    except (TypeError, ValueError):
        return None


def build_discovery_identity(record: dict[str, Any]) -> dict[str, Any]:
    """Build conservative identity fingerprints; never use project names or tickers."""
    source_name = str(record.get("source_name") or "")
    metadata = dict(record.get("_filter_metadata") or {})
    project_evidence: dict[str, str] = {}
    campaign_evidence: dict[str, str] = {}
    chain = str(metadata.get("chain_id") or record.get("chain") or "").strip().lower()
    address = str(metadata.get("token_address") or record.get("token_contract_address") or "").strip()
    if chain and address:
        normalized = address.lower() if re.fullmatch(r"0x[a-fA-F0-9]{40}", address) else address
        project_evidence = {"method": "chain_contract", "chain": chain, "contract": normalized}
    else:
        official = metadata.get("official_url") or record.get("official_url") or record.get("project_url")
        canonical = _canonical_identity_url(official)
        if canonical: project_evidence = {"method": "exact_official_url", "url": canonical}
    campaign_url = metadata.get("campaign_url") or metadata.get("activity_url") or record.get("campaign_url") or record.get("activity_url")
    if not campaign_url and source_name == "binance-announcements": campaign_url = record.get("link")
    canonical_campaign = _canonical_identity_url(campaign_url)
    if canonical_campaign: campaign_evidence = {"method": "exact_campaign_url", "url": canonical_campaign}

    def fingerprint(kind: str, evidence: dict[str, str]) -> str | None:
        if not evidence: return None
        payload = "|".join(f"{k}={v}" for k, v in sorted(evidence.items()))
        return hashlib.sha256(f"{kind}|{payload}".encode()).hexdigest()
    return {
        "project_identity_key": fingerprint("project", project_evidence),
        "campaign_identity_key": fingerprint("campaign", campaign_evidence),
        "project_identity_evidence": project_evidence,
        "campaign_identity_evidence": campaign_evidence,
    }


def classify_identity_matches(identity: dict[str, Any], matches: list[Any]) -> dict[str, Any]:
    """Classify project/campaign matches without hiding contradictory evidence."""
    project_key = identity.get("project_identity_key")
    campaign_key = identity.get("campaign_identity_key")
    project_matches = [
        item for item in matches
        if project_key and getattr(item, "project_identity_key", None) == project_key
    ]
    campaign_matches = [
        item for item in matches
        if campaign_key and getattr(item, "campaign_identity_key", None) == campaign_key
    ]

    campaign_project_keys = {
        getattr(item, "project_identity_key", None)
        for item in campaign_matches
        if getattr(item, "project_identity_key", None)
    }
    identity_conflict = (
        len(campaign_project_keys) > 1
        or bool(project_key and any(key != project_key for key in campaign_project_keys))
    )

    exact_pair_matches = [
        item for item in campaign_matches
        if project_key and getattr(item, "project_identity_key", None) == project_key
    ]
    if identity_conflict:
        status, selected = "identity_conflict_needs_review", (campaign_matches or project_matches)[0]
    elif exact_pair_matches:
        status, selected = "same_project_same_campaign", exact_pair_matches[0]
    elif campaign_matches:
        status, selected = "same_campaign_project_unconfirmed", campaign_matches[0]
    elif project_matches and campaign_key:
        distinct = [
            item for item in project_matches
            if getattr(item, "campaign_identity_key", None)
            and getattr(item, "campaign_identity_key", None) != campaign_key
        ]
        status, selected = (
            ("same_project_distinct_campaign", distinct[0])
            if distinct else ("same_project_campaign_unconfirmed", project_matches[0])
        )
    elif project_matches:
        status, selected = "same_project_campaign_unconfirmed", project_matches[0]
    elif project_key or campaign_key:
        status, selected = "no_strong_match_found", None
    else:
        status, selected = "insufficient_identity_evidence", None

    matched = project_matches + campaign_matches
    matched_source_keys = sorted({
        str(getattr(item, "source_key", ""))
        for item in matched
        if getattr(item, "source_key", None)
    })

    # Campaign groups represent the same evidenced activity. Project groups are
    # only a parent relationship and must never be treated as campaign duplicates.
    if identity_conflict:
        group_type, group_key = "needs_review", None
    elif campaign_key:
        group_type, group_key = "campaign", campaign_key
    elif project_key:
        group_type, group_key = "project", project_key
    else:
        group_type, group_key = "unresolved", None

    return {
        **identity,
        "identity_status": status,
        "identity_conflict": identity_conflict,
        "identity_group_type": group_type,
        "identity_group_key": group_key,
        "matched_source_key": getattr(selected, "source_key", None),
        "matched_source_name": getattr(selected, "source_name", None),
        "matched_source_keys": matched_source_keys,
    }


async def evaluate_discovery_identity(db: Any, record: dict[str, Any]) -> dict[str, Any]:
    """Compare strong identity evidence against stored observations without merging or deleting them."""
    identity = build_discovery_identity(record)
    pk, ck = identity["project_identity_key"], identity["campaign_identity_key"]
    if not pk and not ck: return classify_identity_matches(identity, [])
    from app.models import AirdropProject
    predicates = []
    if pk: predicates.append(AirdropProject.project_identity_key == pk)
    if ck: predicates.append(AirdropProject.campaign_identity_key == ck)
    statement = select(AirdropProject).where(AirdropProject.source_key != str(record.get("source_key") or ""), or_(*predicates)).limit(50)
    rows = (await db.scalars(statement)).all()
    return classify_identity_matches(identity, rows)
