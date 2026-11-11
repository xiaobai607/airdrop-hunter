"""Read-only public API for approved campaign-level airdrop discovery."""
from datetime import datetime
from decimal import Decimal
from typing import Any, Literal
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import AirdropCampaign

router = APIRouter(prefix="/api/airdrop-campaigns", tags=["airdrop-campaigns"])

Tier = Literal["featured", "ordinary"]
CampaignStatus = Literal["upcoming", "ongoing", "snapshot", "claim_open", "ended", "unknown"]


class PublicCampaign(BaseModel):
    id: UUID
    name: str
    chain: str | None
    description: str
    official_url: str | None
    campaign_type: str
    campaign_status: str
    starts_at: datetime | None
    ends_at: datetime | None
    snapshot_at: datetime | None
    claim_starts_at: datetime | None
    claim_ends_at: datetime | None
    retroactive_possible: bool | None
    temperature: int | None
    opportunity_score: int | None
    is_kyc_free: bool | None
    requires_kyc: bool | None
    requires_phone: bool | None
    difficulty: str | None
    cost_to_farm_usd: Decimal | None
    token_network: str | None
    token_contract_address: str | None
    trust_status: str
    dex_pair_count: int | None
    dex_liquidity_usd: Decimal | None
    dex_volume_24h_usd: Decimal | None
    gecko_trending: bool | None
    score: int | None
    scoring_version: str
    tier: str
    listing_reason: str
    last_verified_at: datetime | None
    updated_at: datetime


@router.get("", response_model=list[PublicCampaign])
async def list_public_campaigns(
    tier: Tier | None = Query(default=None),
    chain: str | None = Query(default=None, max_length=80),
    status: CampaignStatus | None = Query(default=None),
    campaign_type: str | None = Query(default=None, max_length=40),
    kyc: Literal["required", "free", "unknown"] | None = Query(default=None),
    q: str | None = Query(default=None, max_length=120),
    limit: int = Query(default=40, ge=1, le=100),
    offset: int = Query(default=0, ge=0, le=10000),
    db: AsyncSession = Depends(get_db),
) -> list[PublicCampaign]:
    """Return only explicitly published, approved campaigns; observation rows stay private."""
    statement = select(AirdropCampaign).where(
        AirdropCampaign.is_published.is_(True),
        AirdropCampaign.tier.in_(("featured", "ordinary")),
    )
    if tier:
        statement = statement.where(AirdropCampaign.tier == tier)
    if chain and chain.strip():
        statement = statement.where(AirdropCampaign.chain.ilike(f"%{chain.strip()}%"))
    if status:
        statement = statement.where(AirdropCampaign.campaign_status == status)
    if campaign_type and campaign_type.strip():
        statement = statement.where(AirdropCampaign.campaign_type == campaign_type.strip())
    if kyc == "required":
        statement = statement.where(AirdropCampaign.requires_kyc.is_(True))
    elif kyc == "free":
        statement = statement.where(
            (AirdropCampaign.is_kyc_free.is_(True))
            | (AirdropCampaign.requires_kyc.is_(False))
        )
    elif kyc == "unknown":
        statement = statement.where(
            AirdropCampaign.is_kyc_free.is_(None)
            & AirdropCampaign.requires_kyc.is_(None)
        )
    if q and q.strip():
        term = f"%{q.strip()}%"
        statement = statement.where(
            AirdropCampaign.name.ilike(term)
            | AirdropCampaign.description.ilike(term)
        )
    statement = statement.order_by(
        AirdropCampaign.tier.asc(),
        AirdropCampaign.score.desc().nullslast(),
        AirdropCampaign.updated_at.desc(),
    ).limit(limit).offset(offset)
    rows = (await db.scalars(statement)).all()
    return [
        PublicCampaign(
            id=row.id,
            name=row.name,
            chain=row.chain,
            description=row.description,
            official_url=row.official_url,
            campaign_type=row.campaign_type,
            campaign_status=row.campaign_status,
            starts_at=row.starts_at,
            ends_at=row.ends_at,
            snapshot_at=row.snapshot_at,
            claim_starts_at=row.claim_starts_at,
            claim_ends_at=row.claim_ends_at,
            retroactive_possible=row.retroactive_possible,
            temperature=row.temperature,
            opportunity_score=row.opportunity_score,
            is_kyc_free=row.is_kyc_free,
            requires_kyc=row.requires_kyc,
            requires_phone=row.requires_phone,
            difficulty=row.difficulty,
            cost_to_farm_usd=row.cost_to_farm_usd,
            token_network=row.token_network,
            token_contract_address=row.token_contract_address,
            trust_status=row.trust_status,
            dex_pair_count=row.dex_pair_count,
            dex_liquidity_usd=row.dex_liquidity_usd,
            dex_volume_24h_usd=row.dex_volume_24h_usd,
            gecko_trending=row.gecko_trending,
            score=row.score,
            scoring_version=row.scoring_version,
            tier=row.tier,
            listing_reason=row.listing_reason,
            last_verified_at=row.last_verified_at,
            updated_at=row.updated_at,
        )
        for row in rows
    ]
