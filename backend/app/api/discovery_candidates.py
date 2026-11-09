from datetime import datetime
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, ConfigDict
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import AirdropDiscoveryCandidate

router = APIRouter(prefix="/api/airdrops/discovery", tags=["airdrop-discovery"])


class DiscoveryCandidateResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    source_name: str
    source_url: str
    title: str
    item_url: str
    summary: str
    published_at: datetime | None
    first_seen_at: datetime
    last_seen_at: datetime


@router.get("/candidates", response_model=list[DiscoveryCandidateResponse])
async def list_discovery_candidates(
    q: str | None = Query(default=None, max_length=160),
    limit: int = Query(default=100, ge=1, le=200),
    db: AsyncSession = Depends(get_db),
):
    statement = select(AirdropDiscoveryCandidate)
    if q and q.strip():
        statement = statement.where(AirdropDiscoveryCandidate.title.ilike(f"%{q.strip()}%"))
    statement = statement.order_by(
        AirdropDiscoveryCandidate.published_at.desc().nullslast(),
        AirdropDiscoveryCandidate.last_seen_at.desc(),
    ).limit(limit)
    rows = await db.scalars(statement)
    return list(rows)
