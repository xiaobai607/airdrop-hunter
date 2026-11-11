"""Convert unreviewed RSS candidates into non-public campaign observations.

A feed item's link is an article/source link, not an official campaign URL.
This adapter deliberately leaves identity, chain, campaign terms and trust unknown.
"""
import hashlib
import json
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import (
    AirdropCampaign,
    AirdropCampaignSourceSnapshot,
    AirdropDiscoveryCandidate,
)

SOURCE_KEY_PREFIX = "rss-candidate:"


def candidate_observation_payload(candidate: AirdropDiscoveryCandidate) -> dict[str, Any]:
    """Build a safe, evidence-preserving observation without asserting campaign facts."""
    published_at = candidate.published_at
    return {
        "source_key": f"{SOURCE_KEY_PREFIX}{candidate.source_key}",
        "source_name": candidate.source_name,
        "source_url": candidate.source_url,
        "name": candidate.title[:240],
        "description": (candidate.summary or "")[:5000],
        "item_url": candidate.item_url,
        "published_at": published_at.isoformat() if published_at else None,
        "chain": None,
        "official_url": None,
        "campaign_type": "unknown",
        "campaign_status": "unknown",
        "retroactive_possible": None,
        "temperature": None,
        "opportunity_score": None,
        "is_kyc_free": None,
        "requires_kyc": None,
        "requires_phone": None,
        "difficulty": None,
        "cost_to_farm_usd": None,
        "token_network": None,
        "token_contract_address": None,
        "trust_status": "unverified",
        "source_conflict": False,
        "corroborated_risk": False,
        "risk_flags": [],
        "score": None,
        "score_breakdown": {},
        "scoring_version": "v1",
        "tier": "observation",
        "is_published": False,
        "listing_reason": "RSS/Atom 候选仅代表来源文章，尚未核验项目身份和官方活动信息",
        "review_reason": "需要人工核验官方项目及活动链接、链、活动状态和参与条件",
    }


def _snapshot_hash(payload: dict[str, Any]) -> str:
    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


async def promote_pending_candidates_to_campaign_observations(db: AsyncSession) -> int:
    """Idempotently mirror pending candidates into unpublished observation campaigns."""
    candidates = list(await db.scalars(
        select(AirdropDiscoveryCandidate)
        .where(AirdropDiscoveryCandidate.review_status == "pending")
        .order_by(AirdropDiscoveryCandidate.first_seen_at.asc())
    ))
    now = datetime.now(timezone.utc)
    created = 0

    for candidate in candidates:
        payload = candidate_observation_payload(candidate)
        source_key = payload["source_key"]
        campaign = await db.scalar(
            select(AirdropCampaign).where(AirdropCampaign.source_key == source_key)
        )
        if campaign is None:
            campaign = AirdropCampaign(
                source_key=source_key,
                source_name=candidate.source_name,
                source_url=candidate.source_url,
                name=payload["name"],
                description=payload["description"],
                official_url=None,
                chain=None,
                campaign_type="unknown",
                campaign_status="unknown",
                retroactive_possible=None,
                temperature=None,
                opportunity_score=None,
                is_kyc_free=None,
                requires_kyc=None,
                requires_phone=None,
                difficulty=None,
                cost_to_farm_usd=None,
                token_network=None,
                token_contract_address=None,
                trust_status="unverified",
                source_conflict=False,
                corroborated_risk=False,
                risk_flags=[],
                score=None,
                score_breakdown={},
                scoring_version="v1",
                tier="observation",
                is_published=False,
                listing_reason=payload["listing_reason"],
                review_reason=payload["review_reason"],
                last_seen_at=now,
            )
            db.add(campaign)
            await db.flush()
            created += 1
        else:
            # Never upgrade trust or publication state from an RSS re-fetch.
            campaign.source_name = candidate.source_name
            campaign.source_url = candidate.source_url
            campaign.name = payload["name"]
            campaign.description = payload["description"]
            campaign.last_seen_at = now
            campaign.official_url = None
            campaign.trust_status = "unverified"
            campaign.tier = "observation"
            campaign.is_published = False
            campaign.listing_reason = payload["listing_reason"]
            campaign.review_reason = payload["review_reason"]

        snapshot_hash = _snapshot_hash(payload)
        snapshot_exists = await db.scalar(
            select(AirdropCampaignSourceSnapshot.id).where(
                AirdropCampaignSourceSnapshot.campaign_id == campaign.id,
                AirdropCampaignSourceSnapshot.source_name == candidate.source_name,
                AirdropCampaignSourceSnapshot.snapshot_hash == snapshot_hash,
            )
        )
        if snapshot_exists is None:
            db.add(AirdropCampaignSourceSnapshot(
                campaign_id=campaign.id,
                source_name=candidate.source_name,
                source_url=candidate.source_url,
                snapshot_hash=snapshot_hash,
                normalized_payload=payload,
                captured_at=now,
            ))

    await db.commit()
    return created
