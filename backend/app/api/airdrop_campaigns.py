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
    source_name: str
    source_url: str
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
    is_kyc_free: bool | None
    requires_kyc: bool | None
    requires_phone: bool | None
    difficulty: str | None
    cost_to_farm_usd: Decimal | None
    tier: str
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
    """Return only published campaign facts; internal trust, scoring and market telemetry stay private."""
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
            source_name=row.source_name,
            source_url=row.source_url,
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
            is_kyc_free=row.is_kyc_free,
            requires_kyc=row.requires_kyc,
            requires_phone=row.requires_phone,
            difficulty=row.difficulty,
            cost_to_farm_usd=row.cost_to_farm_usd,
            tier=row.tier,
            last_verified_at=row.last_verified_at,
            updated_at=row.updated_at,
        )
        for row in rows
    ]
