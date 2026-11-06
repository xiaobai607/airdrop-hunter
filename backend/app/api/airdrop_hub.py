from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import AsyncSessionLocal, get_db
from app.models import AirdropProject
from app.services.airdrop_hub_sync import get_sync_status, sync_airdrop_hub

router = APIRouter(prefix="/api/airdrops/hub", tags=["airdrop-hub"])


class AirdropHubItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    name: str
    chain: str
    description: str
    status: str
    link: str


class AirdropHubSyncStatus(BaseModel):
    last_attempt_at: str | None
    last_success_at: str | None
    imported_count: int
    source: str
    error: str | None


@router.get("", response_model=list[AirdropHubItem])
async def list_airdrop_hub(
    chain: str | None = Query(default=None, max_length=80),
    db: AsyncSession = Depends(get_db),
) -> list[AirdropHubItem]:
    statement = select(AirdropProject)
    if chain and chain.strip() and chain.strip().lower() != "all":
        statement = statement.where(AirdropProject.chain.ilike(f"%{chain.strip()}%"))
    statement = statement.order_by(AirdropProject.updated_at.desc(), AirdropProject.name.asc())
    rows = (await db.scalars(statement)).all()
    return [
        AirdropHubItem(id=str(item.id), name=item.name, chain=item.chain,
                       description=item.description, status=item.status, link=item.link)
        for item in rows
    ]


@router.get("/status", response_model=AirdropHubSyncStatus)
async def airdrop_hub_sync_status() -> dict[str, Any]:
    return get_sync_status()


@router.post("/refresh")
async def refresh_airdrop_hub() -> dict[str, Any]:
    status = get_sync_status()
    from datetime import datetime, timezone
    last = status.get("last_attempt_at")
    if last:
        try:
            elapsed = (datetime.now(timezone.utc) - datetime.fromisoformat(last)).total_seconds()
            if elapsed < 300:
                raise HTTPException(status_code=429, detail="刷新过于频繁，请 5 分钟后重试")
        except ValueError:
            pass
    async with AsyncSessionLocal() as db:
        try:
            count = await sync_airdrop_hub(db)
            return {"ok": True, "imported_count": count, "status": get_sync_status()}
        except Exception as exc:
            raise HTTPException(status_code=503, detail=f"空投数据源暂时不可用：{exc}") from exc
