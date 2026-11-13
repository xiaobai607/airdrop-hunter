"""Deterministic, evidence-aware campaign scoring and publication tiers."""
from typing import Any, Mapping

SCORING_VERSION = "v1"
TIERS = {"featured", "ordinary", "observation", "archived"}


def evaluate_campaign(
    item: Mapping[str, Any],
    *,
    min_liquidity_usd: float = 1000.0,
    min_volume_24h_usd: float = 1000.0,
    high_cost_usd: float = 25.0,
) -> dict[str, Any]:
    """Return an explainable score/tier decision; unknown values stay unknown.

    temperature and opportunity_score overlap, so the stronger source signal is
    used once instead of summing both. Market checks apply only when a contract
    address is explicitly known; pre-token projects are not penalized for that.
    """
    def number(key: str) -> float | None:
        value = item.get(key)
        if value is None:
            return None
        try:
            return float(value)
        except (TypeError, ValueError):
            return None

    temperature = number("temperature")
    opportunity = number("opportunity_score")
    heat_values = [max(0.0, min(100.0, value)) for value in (temperature, opportunity) if value is not None]
    source_heat = max(heat_values) if heat_values else None
    trust = str(item.get("trust_status") or "unverified").lower()
    token_address = str(item.get("token_contract_address") or "").strip()
    pair_count = number("dex_pair_count")
    liquidity = number("dex_liquidity_usd")
    volume = number("dex_volume_24h_usd")
    trending = item.get("gecko_trending") is True
    requires_kyc = item.get("requires_kyc")
    is_kyc_free = item.get("is_kyc_free")
    difficulty = str(item.get("difficulty") or "").lower()
    cost = number("cost_to_farm_usd")
    status = str(item.get("campaign_status") or "unknown").lower()
    retroactive = item.get("retroactive_possible")
    corroborated_risk = item.get("corroborated_risk") is True
    source_conflict = item.get("source_conflict") is True
    official_verified = item.get("official_url_verified") is True
    source_name = str(item.get("source_name") or "").strip()
    campaign_type = str(item.get("campaign_type") or "").strip().lower()
    official_binance_airdrop = (
        source_name == "binance-crypto-airdrop"
        and campaign_type == "airdrop"
        and official_verified
    )
    low_heat_count = int(item.get("low_heat_success_count") or 0)

    market_qualified = None
    if token_address:
        market_qualified = (
            pair_count is not None and pair_count >= 1
            and liquidity is not None and liquidity >= min_liquidity_usd
            and volume is not None and volume >= min_volume_24h_usd
        )

    score = None
    breakdown: dict[str, Any] = {
        "scoring_version": SCORING_VERSION,
        "source_heat": source_heat,
        "temperature": temperature,
        "opportunity_score": opportunity,
        "source_heat_rule": "max(temperature, opportunity_score); overlapping source metrics are not added",
        "market_signal": "not_applicable" if not token_address else ("qualified" if market_qualified else "insufficient_or_unknown"),
        "trust_status": trust,
        "gecko_trending": item.get("gecko_trending"),
        "official_binance_airdrop_evidence": official_binance_airdrop,
        "bonuses": {},
    }
    if source_heat is not None:
        score_value = source_heat * 0.8
        if official_verified:
            score_value += 10
            breakdown["bonuses"]["official_url_verified"] = 10
        if token_address and market_qualified:
            score_value += 7
            breakdown["bonuses"]["market_qualified"] = 7
        if trending:
            score_value += 3
            breakdown["bonuses"]["gecko_trending"] = 3
        score = max(0, min(100, round(score_value)))

    reasons: list[str] = []
    if status in {"ended", "expired", "closed"} and retroactive is False:
        tier = "archived"
        reasons.append("活动已结束，且来源明确没有追溯参与机会")
    elif temperature is not None and temperature < 40 and low_heat_count >= 3:
        tier = "archived"
        reasons.append("连续三次成功采集的热度均低于40")
    elif corroborated_risk:
        tier = "observation"
        reasons.append("存在已交叉核实的风险证据，暂停公开上架")
    elif source_conflict:
        tier = "observation"
        reasons.append("来源信息存在冲突，等待核验")
    elif trust in {"unverified", "low_confidence", "conflicted"}:
        tier = "observation"
        reasons.append("项目可信度尚未达到公开推荐标准")
    elif (
        requires_kyc is True
        and (difficulty in {"hard", "high"} or (cost is not None and cost >= high_cost_usd))
    ):
        tier = "observation"
        reasons.append("KYC 与高难度或高成本同时存在")
    elif official_binance_airdrop:
        tier = "ordinary"
        reasons.append("Binance 官方 Crypto Airdrop 分类及原始公告正文已核验；未明确的活动状态、链、奖励与资格字段保持未知")
    elif source_heat is not None and source_heat >= 70:
        kyc_clear = is_kyc_free is True or requires_kyc is False
        market_clear = not token_address or market_qualified is True
        if official_verified and kyc_clear and market_clear:
            tier = "featured"
            reasons.append("热度达到70，官方链接已核验，KYC条件明确且市场门槛满足或不适用")
        else:
            tier = "observation"
            if not official_verified:
                reasons.append("官方链接尚未核验")
            if not kyc_clear:
                reasons.append("KYC是否需要尚未得到明确证据")
            if not market_clear:
                reasons.append("已知代币合约缺少达到门槛的DEX市场信号")
    elif source_heat is not None and source_heat >= 40:
        if official_verified:
            tier = "ordinary"
            reasons.append("热度达到普通目录门槛，官方链接已核验且暂未发现明确的硬性风险")
        else:
            tier = "observation"
            reasons.append("官方链接尚未核验，不能进入公开目录")
    else:
        tier = "observation"
        reasons.append("热度或来源证据不足，保留观察状态")

    publishable = tier in {"featured", "ordinary"} and not corroborated_risk and not source_conflict
    if corroborated_risk:
        reasons.append("风险记录仅供审核，不进入公开目录")
    return {
        "score": score,
        "tier": tier,
        "is_published": publishable,
        "scoring_version": SCORING_VERSION,
        "score_breakdown": breakdown,
        "listing_reason": "；".join(reasons),
    }
