"""Persist deterministic campaign scoring decisions and score history."""
from urllib.parse import urlparse

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AirdropCampaign, AirdropCampaignScoreHistory
from app.services.airdrop_scoring import evaluate_campaign


def _has_verified_official_url(campaign: AirdropCampaign) -> bool:
    """Trust verification is necessary but an absent/unsafe URL is never verified."""
    if str(campaign.trust_status or "").lower() != "verified":
        return False
    raw_url = str(campaign.official_url or "").strip()
    parsed = urlparse(raw_url)
    return bool(
        parsed.scheme == "https"
        and parsed.hostname
        and not parsed.username
        and not parsed.password
    )


def build_campaign_scoring_input(campaign: AirdropCampaign) -> dict:
    """Translate persisted campaign facts into the pure scoring contract."""
    return {
        "source_name": campaign.source_name,
        "campaign_type": campaign.campaign_type,
        "temperature": campaign.temperature,
        "opportunity_score": campaign.opportunity_score,
        "trust_status": campaign.trust_status,
        "official_url_verified": _has_verified_official_url(campaign),
        "campaign_status": campaign.campaign_status,
        "retroactive_possible": campaign.retroactive_possible,
        "requires_kyc": campaign.requires_kyc,
        "is_kyc_free": campaign.is_kyc_free,
        "difficulty": campaign.difficulty,
        "cost_to_farm_usd": campaign.cost_to_farm_usd,
        "token_contract_address": campaign.token_contract_address,
        "dex_pair_count": campaign.dex_pair_count,
        "dex_liquidity_usd": campaign.dex_liquidity_usd,
        "dex_volume_24h_usd": campaign.dex_volume_24h_usd,
        "gecko_trending": campaign.gecko_trending,
        "corroborated_risk": campaign.corroborated_risk,
        "source_conflict": campaign.source_conflict,
        "low_heat_success_count": campaign.low_heat_success_count,
    }


async def recalculate_campaign_scores(db: AsyncSession) -> dict[str, int]:
    """Re-evaluate stored campaigns; publish only when the evidence gates pass."""
    campaigns = list(await db.scalars(
        select(AirdropCampaign).order_by(AirdropCampaign.created_at.asc())
    ))
    updated = 0
    published = 0
    held = 0

    for campaign in campaigns:
        decision = evaluate_campaign(build_campaign_scoring_input(campaign))
        changed = any((
            campaign.score != decision["score"],
            campaign.tier != decision["tier"],
            campaign.is_published != decision["is_published"],
            campaign.scoring_version != decision["scoring_version"],
            campaign.score_breakdown != decision["score_breakdown"],
            campaign.listing_reason != decision["listing_reason"],
        ))
        if not changed:
            continue

        campaign.score = decision["score"]
        campaign.tier = decision["tier"]
        campaign.is_published = decision["is_published"]
        campaign.scoring_version = decision["scoring_version"]
        campaign.score_breakdown = decision["score_breakdown"]
        campaign.listing_reason = decision["listing_reason"]
        db.add(AirdropCampaignScoreHistory(
            campaign_id=campaign.id,
            scoring_version=decision["scoring_version"],
            score=decision["score"],
            tier=decision["tier"],
            score_breakdown=decision["score_breakdown"],
            listing_reason=decision["listing_reason"],
        ))
        updated += 1
        if decision["is_published"]:
            published += 1
        else:
            held += 1

    await db.commit()
    return {"evaluated": len(campaigns), "updated": updated, "publishable": published, "held": held}
