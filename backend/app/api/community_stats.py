from datetime import datetime, timezone
from decimal import Decimal
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Path, Request
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import func, or_, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import CommunityContribution, CommunityProjectCatalogEntry, CommunityProjectRating, CommunityProjectStats, PersonalLedgerEntry, User
from app.routers.auth import get_current_user, get_optional_current_user, limiter
from app.services.community_privacy import anonymous_contributor_fingerprint
from app.services.transaction_verification import (
    VerificationPending,
    VerificationRejected,
    verify_community_transaction,
)

router = APIRouter(prefix="/api/community/projects", tags=["community"])
MAX_AMOUNT = Decimal("999999999999.99999999")


class CommunityAssetAmount(BaseModel):
    kind: str = Field(pattern="^(gas|reward)$")
    amount_native: Decimal = Field(ge=0, le=MAX_AMOUNT)
    symbol: str = Field(min_length=1, max_length=20, pattern="^[A-Za-z0-9._-]+$")
    decimals: int | None = Field(default=None, ge=0, le=36)
    usd_valuation: Decimal | None = Field(default=None, ge=0, le=MAX_AMOUNT)
    valuation_timestamp: datetime | None = None
    valuation_source: str | None = Field(default=None, max_length=64)

    @field_validator("valuation_source")
    @classmethod
    def sanitize_valuation_source(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip().lower()
        allowed = {
            "coinpaprika",
            "coingecko",
            "manual_usd_entry",
            "user_input",
            "user_reported",
            "legacy-recorded",
        }
        allowed.update({
            f"chainlink:{network}"
            for network in ("ethereum", "bsc", "polygon", "arbitrum", "optimism", "base", "avalanche")
        })
        return normalized if normalized in allowed else "user_reported"


class CommunityContributionRequest(BaseModel):
    event_id: UUID
    network: str = Field(min_length=1, max_length=40)
    wallet_address: str = Field(min_length=1, max_length=160)
    tx_hash: str = Field(min_length=1, max_length=180)
    public_contribution_opt_in: bool
    gas_cost_usd: Decimal = Field(ge=0, le=MAX_AMOUNT)
    actual_reward_usd: Decimal = Field(default=Decimal("0"), ge=0, le=MAX_AMOUNT)
    estimated_reward_usd: Decimal | None = Field(default=None, ge=0, le=MAX_AMOUNT)
    actual_reward_confirmed: bool = False
    contributor_id: UUID | None = None
    asset_breakdown: list[CommunityAssetAmount] = Field(default_factory=list, max_length=10)


def serialize_stats(
    project_official_id: str,
    row: CommunityProjectStats | None,
    average_rating=None,
    rating_count=0,
    gas_by_verification_tier: dict[str, float] | None = None,
) -> dict:
    return {
        "project_official_id": project_official_id,
        "total_gas_cost_usd": float(row.total_gas_cost_usd or 0) if row else 0.0,
        "total_actual_reward_usd": float(row.total_actual_reward_usd or 0) if row else 0.0,
        "total_estimated_reward_usd": float(row.total_estimated_reward_usd or 0) if row else 0.0,
        "total_reward_usd": float(row.total_reward_usd or 0) if row else 0.0,
        "gas_by_verification_tier": gas_by_verification_tier or {},
        "average_rating": float(average_rating) if average_rating is not None else None,
        "rating_count": int(rating_count or 0),
        "data_quality": "approximate_actual_and_estimated",
    }


async def serialize_stats_with_rating(
    db: AsyncSession, project_official_id: str, row: CommunityProjectStats | None
) -> dict:
    rating = (
        await db.execute(
            select(
                func.avg(CommunityProjectRating.score).label("average"),
                func.count(CommunityProjectRating.id).label("count"),
            ).where(CommunityProjectRating.project_official_id == project_official_id)
        )
    ).one()
    tier_result = await db.execute(
        select(
            CommunityContribution.verification_tier,
            func.coalesce(func.sum(CommunityContribution.gas_cost_usd), 0),
        )
        .where(CommunityContribution.project_official_id == project_official_id)
        .group_by(CommunityContribution.verification_tier)
    )
    tier_totals = {str(item[0]): float(item[1] or 0) for item in tier_result.all()}
    return serialize_stats(project_official_id, row, rating.average, rating.count, tier_totals)


def normalize_public_asset_breakdown(
    assets: list[CommunityAssetAmount],
    actual_reward_usd: Decimal,
    verification,
) -> list[dict]:
    """Keep token-level snapshot rows consistent with the authoritative contribution totals."""
    reward_assets = [asset for asset in assets if asset.kind == "reward"]
    if reward_assets:
        if any(asset.usd_valuation is None for asset in reward_assets):
            raise HTTPException(status_code=422, detail="奖励代币明细估值不完整，不能匿名贡献")
        reward_total = sum(
            (asset.usd_valuation for asset in reward_assets if asset.usd_valuation is not None),
            Decimal("0"),
        )
        if abs(reward_total - actual_reward_usd) > Decimal("0.00000001"):
            raise HTTPException(status_code=422, detail="奖励代币明细估值合计与本笔实际收益不一致")

    gas_assets = [asset for asset in assets if asset.kind == "gas"]
    if verification.tier != "amount_verified" and len(gas_assets) > 1:
        raise HTTPException(status_code=422, detail="单笔交易不能包含多条 Gas 原生币明细")

    serialized = [asset.model_dump(mode="json") for asset in assets]
    if verification.tier == "amount_verified":
        # Ignore every client-supplied Gas row and emit exactly one server-derived row.
        serialized = [asset for asset in serialized if asset.get("kind") != "gas"]
        quote_timestamp = datetime.fromtimestamp(
            int(verification.metadata["price_updated_at"]), tz=timezone.utc
        )
        serialized.insert(0, {
            "kind": "gas",
            "amount_native": str(verification.metadata["native_fee"]),
            "symbol": str(verification.metadata["native_symbol"]),
            "decimals": 18,
            "usd_valuation": str(verification.gas_cost_usd),
            "valuation_timestamp": quote_timestamp.isoformat(),
            "valuation_source": str(verification.metadata.get("price_source") or "chainlink"),
        })
    else:
        for asset in serialized:
            if asset.get("kind") == "gas":
                asset["valuation_source"] = "user_reported"
    return serialized


@router.post("/{project_official_id}/contributions")
@limiter.limit("60/hour")
async def add_community_contribution(
    request: Request,
    payload: CommunityContributionRequest,
    project_official_id: Annotated[str, Path(min_length=1, max_length=120)],
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User | None, Depends(get_optional_current_user)],
):
    if not payload.public_contribution_opt_in:
        raise HTTPException(status_code=403, detail="未获得匿名公共贡献授权")

    # Only currently active, explicitly curated official projects may receive
    # new public contributions. Deactivation never deletes historical aggregates.
    catalog_entry = await db.scalar(
        select(CommunityProjectCatalogEntry).where(
            CommunityProjectCatalogEntry.project_official_id == project_official_id,
            CommunityProjectCatalogEntry.is_active.is_(True),
        )
    )
    if catalog_entry is None:
        raise HTTPException(status_code=404, detail="该项目不在当前有效社区目录中，不能新增公共贡献")

    # Tx Hash/address are transient verification inputs only. The database stores
    # a server-keyed fingerprint and sanitized verification metadata, never raw values.
    try:
        verification = await verify_community_transaction(
            payload.network, payload.wallet_address, payload.tx_hash, payload.gas_cost_usd
        )
    except VerificationPending:
        raise HTTPException(status_code=503, detail="链上核验暂不可用，记录已保留并等待重试")
    except VerificationRejected as error:
        raise HTTPException(status_code=422, detail=str(error))

    public_gas_cost = verification.gas_cost_usd
    public_reward_usd = (
        payload.actual_reward_usd
        if payload.actual_reward_confirmed
        else (payload.estimated_reward_usd or Decimal("0"))
    )
    asset_breakdown = normalize_public_asset_breakdown(
        payload.asset_breakdown, public_reward_usd, verification
    )
    contributor_fingerprint = anonymous_contributor_fingerprint(payload.contributor_id)

    contribution = pg_insert(CommunityContribution).values(
        id=payload.event_id,
        # Public aggregates are intentionally unlinkable to a signed-in account.
        user_id=None,
        project_official_id=project_official_id,
        network=payload.network.strip().lower(),
        gas_cost_usd=public_gas_cost,
        actual_reward_usd=payload.actual_reward_usd,
        estimated_reward_usd=payload.estimated_reward_usd,
        actual_reward_confirmed=payload.actual_reward_confirmed,
        contributor_fingerprint=contributor_fingerprint,
        asset_breakdown=asset_breakdown,
        tx_fingerprint=verification.tx_fingerprint,
        verification_tier=verification.tier,
        verification_metadata=verification.metadata,
    ).on_conflict_do_nothing().returning(CommunityContribution.id)

    inserted_id = (await db.execute(contribution)).scalar_one_or_none()
    if inserted_id is None:
        existing = await db.scalar(
            select(CommunityContribution).where(
                or_(
                    CommunityContribution.id == payload.event_id,
                    CommunityContribution.tx_fingerprint == verification.tx_fingerprint,
                )
            )
        )
        same_contribution = (
            existing is not None
            and existing.id == payload.event_id
            and existing.user_id is None
            and existing.project_official_id == project_official_id
            and existing.gas_cost_usd == public_gas_cost
            and existing.actual_reward_usd == payload.actual_reward_usd
            and existing.estimated_reward_usd == payload.estimated_reward_usd
            and existing.actual_reward_confirmed == payload.actual_reward_confirmed
            and existing.network == payload.network.strip().lower()
            and existing.contributor_fingerprint == contributor_fingerprint
            and existing.tx_fingerprint == verification.tx_fingerprint
            and existing.verification_tier == verification.tier
            and (existing.asset_breakdown or []) == asset_breakdown
        )
        if not same_contribution:
            raise HTTPException(status_code=409, detail="该链上交易已贡献，或统计流水编号与原记录冲突")
    else:
        aggregate_insert = pg_insert(CommunityProjectStats).values(
            project_official_id=project_official_id,
            total_gas_cost_usd=public_gas_cost,
            total_actual_reward_usd=payload.actual_reward_usd if payload.actual_reward_confirmed else Decimal("0"),
            total_estimated_reward_usd=payload.estimated_reward_usd if not payload.actual_reward_confirmed and payload.estimated_reward_usd is not None else Decimal("0"),
            total_reward_usd=public_reward_usd,
        )
        excluded = aggregate_insert.excluded
        aggregate_upsert = aggregate_insert.on_conflict_do_update(
            index_elements=[CommunityProjectStats.project_official_id],
            set_={
                "total_gas_cost_usd": CommunityProjectStats.total_gas_cost_usd + excluded.total_gas_cost_usd,
                "total_actual_reward_usd": CommunityProjectStats.total_actual_reward_usd + excluded.total_actual_reward_usd,
                "total_estimated_reward_usd": CommunityProjectStats.total_estimated_reward_usd + excluded.total_estimated_reward_usd,
                "total_reward_usd": CommunityProjectStats.total_reward_usd + excluded.total_reward_usd,
            },
        )
        await db.execute(aggregate_upsert)

    await db.commit()
    row = await db.get(CommunityProjectStats, project_official_id)
    response = await serialize_stats_with_rating(db, project_official_id, row)
    response["verification_tier"] = verification.tier
    return response



class CommunityProjectRatingRequest(BaseModel):
    score: int = Field(ge=1, le=5)


@router.post("/{project_official_id}/ratings")
async def submit_community_project_rating(
    payload: CommunityProjectRatingRequest,
    project_official_id: Annotated[str, Path(min_length=1, max_length=120)],
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
):
    project_id = PersonalLedgerEntry.extra["project_official_id"].as_string()
    demo_flag = PersonalLedgerEntry.extra["is_demo"].as_string()
    participation_id = await db.scalar(
        select(PersonalLedgerEntry.id)
        .where(
            PersonalLedgerEntry.user_id == user.id,
            project_id == project_official_id,
            or_(demo_flag.is_(None), demo_flag != "true"),
            ~PersonalLedgerEntry.tx_hash.like("MOCK-%"),
        )
        .limit(1)
    )
    if participation_id is None:
        raise HTTPException(status_code=403, detail="只有已确认参与该项目的账号才能评分")

    rating_insert = pg_insert(CommunityProjectRating).values(
        user_id=user.id,
        project_official_id=project_official_id,
        score=payload.score,
    )
    rating_upsert = rating_insert.on_conflict_do_update(
        index_elements=[
            CommunityProjectRating.user_id,
            CommunityProjectRating.project_official_id,
        ],
        set_={"score": rating_insert.excluded.score, "updated_at": func.now()},
    )
    await db.execute(rating_upsert)
    await db.commit()
    row = await db.get(CommunityProjectStats, project_official_id)
    return await serialize_stats_with_rating(db, project_official_id, row)


@router.get("/{project_official_id}/stats")
async def get_community_project_stats(
    project_official_id: Annotated[str, Path(min_length=1, max_length=120)],
    db: Annotated[AsyncSession, Depends(get_db)],
):
    row = await db.get(CommunityProjectStats, project_official_id)
    return await serialize_stats_with_rating(db, project_official_id, row)
