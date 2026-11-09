from datetime import datetime, timedelta, timezone
from decimal import Decimal
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Path, Request
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import CommunityContribution, CommunityProjectStats, User
from app.routers.auth import get_optional_current_user, limiter

router = APIRouter(prefix="/api/community/projects", tags=["community"])
MAX_AMOUNT = Decimal("999999999999.99999999")


class CommunityContributionRequest(BaseModel):
    event_id: UUID
    gas_cost_usd: Decimal = Field(ge=0, le=MAX_AMOUNT)
    actual_reward_usd: Decimal = Field(ge=0, le=MAX_AMOUNT)
    interaction_count: int = Field(default=1, ge=1, le=1)


def serialize_stats(project_official_id: str, row: CommunityProjectStats | None) -> dict:
    if row is None:
        return {
            "project_official_id": project_official_id,
            "total_gas_cost_usd": 0.0,
            "total_actual_reward_usd": 0.0,
            "total_interactions": 0,
            "contribution_count": 0,
            "contributions_24h": 0,
            "interactions_24h": 0,
            "data_quality": "user_confirmed",
        }
    return {
        "project_official_id": project_official_id,
        "total_gas_cost_usd": float(row.total_gas_cost_usd or 0),
        "total_actual_reward_usd": float(row.total_actual_reward_usd or 0),
        "total_interactions": int(row.total_interactions or 0),
        "contribution_count": int(row.contribution_count or 0),
        "contributions_24h": 0,
        "interactions_24h": 0,
        "data_quality": "user_confirmed",
    }


async def serialize_stats_with_activity(
    db: AsyncSession, project_official_id: str, row: CommunityProjectStats | None
) -> dict:
    cutoff = datetime.now(timezone.utc) - timedelta(hours=24)
    recent = (
        await db.execute(
            select(
                func.count(CommunityContribution.id).label("contributions"),
                func.coalesce(func.sum(CommunityContribution.interaction_count), 0).label("interactions"),
            ).where(
                CommunityContribution.project_official_id == project_official_id,
                CommunityContribution.created_at >= cutoff,
            )
        )
    ).one()
    result = serialize_stats(project_official_id, row)
    result["contributions_24h"] = int(recent.contributions or 0)
    result["interactions_24h"] = int(recent.interactions or 0)
    return result


@router.post("/{project_official_id}/contributions")
@limiter.limit("60/hour")
async def add_community_contribution(
    request: Request,
    payload: CommunityContributionRequest,
    project_official_id: Annotated[str, Path(min_length=1, max_length=120)],
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User | None, Depends(get_optional_current_user)],
):
    contribution = pg_insert(CommunityContribution).values(
        id=payload.event_id,
        # Public dashboard contributions are deliberately unlinkable to an account.
        # Authentication may validate a request, but no user ID or private ledger
        # identity is persisted with an aggregate contribution.
        user_id=None,
        project_official_id=project_official_id,
        gas_cost_usd=payload.gas_cost_usd,
        actual_reward_usd=payload.actual_reward_usd,
        interaction_count=payload.interaction_count,
    ).on_conflict_do_nothing(index_elements=[CommunityContribution.id]).returning(CommunityContribution.id)

    inserted_id = (await db.execute(contribution)).scalar_one_or_none()
    if inserted_id is None:
        existing = await db.scalar(
            select(CommunityContribution).where(CommunityContribution.id == payload.event_id)
        )
        same_contribution = (
            existing is not None
            and existing.user_id is None
            and existing.project_official_id == project_official_id
            and existing.gas_cost_usd == payload.gas_cost_usd
            and existing.actual_reward_usd == payload.actual_reward_usd
            and existing.interaction_count == payload.interaction_count
        )
        if not same_contribution:
            raise HTTPException(status_code=409, detail="统计流水编号冲突：同一编号不能对应不同的项目或金额")
    else:
        aggregate_insert = pg_insert(CommunityProjectStats).values(
            project_official_id=project_official_id,
            total_gas_cost_usd=payload.gas_cost_usd,
            total_actual_reward_usd=payload.actual_reward_usd,
            total_interactions=payload.interaction_count,
            contribution_count=1,
        )
        excluded = aggregate_insert.excluded
        aggregate_upsert = aggregate_insert.on_conflict_do_update(
            index_elements=[CommunityProjectStats.project_official_id],
            set_={
                "total_gas_cost_usd": CommunityProjectStats.total_gas_cost_usd + excluded.total_gas_cost_usd,
                "total_actual_reward_usd": CommunityProjectStats.total_actual_reward_usd + excluded.total_actual_reward_usd,
                "total_interactions": CommunityProjectStats.total_interactions + excluded.total_interactions,
                "contribution_count": CommunityProjectStats.contribution_count + 1,
            },
        )
        await db.execute(aggregate_upsert)

    await db.commit()
    row = await db.get(CommunityProjectStats, project_official_id)
    return await serialize_stats_with_activity(db, project_official_id, row)


@router.get("/{project_official_id}/stats")
async def get_community_project_stats(
    project_official_id: Annotated[str, Path(min_length=1, max_length=120)],
    db: Annotated[AsyncSession, Depends(get_db)],
):
    row = await db.get(CommunityProjectStats, project_official_id)
    return await serialize_stats_with_activity(db, project_official_id, row)
