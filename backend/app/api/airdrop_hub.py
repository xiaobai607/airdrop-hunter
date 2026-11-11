from datetime import datetime, timezone
from typing import Any
from fastapi import APIRouter, Depends, HTTPException, Query, Request
from pydantic import BaseModel, ConfigDict
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import AsyncSessionLocal, get_db
from app.routers.auth import limiter
from app.models import AirdropProject
from app.services.airdrop_hub_sync import TRUSTED_AIRDROP_PROVIDER_LABELS, get_sync_status
from app.services.airdrop_sync import sync_airdrops

router = APIRouter(prefix="/api/airdrops/hub", tags=["airdrop-hub"])
sync_router = APIRouter(prefix="/api/airdrops", tags=["airdrop-sync"])

class AirdropHubItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    name: str
    chain: str
    description: str
    status: str
    link: str
    data_source: str
    trust_level: str
    publication_status: str

class AirdropHubSyncStatus(BaseModel):
    last_attempt_at: str | None
    last_success_at: str | None
    imported_count: int
    source: str
    error: str | None

async def _sync_now() -> dict[str, Any]:
    last = get_sync_status().get("last_attempt_at")
    if last:
        try:
            elapsed = (datetime.now(timezone.utc) - datetime.fromisoformat(last)).total_seconds()
            if elapsed < 300:
                raise HTTPException(status_code=429, detail="刷新过于频繁，请 5 分钟后重试")
        except ValueError:
            pass
    async with AsyncSessionLocal() as db:
        try:
            count = await sync_airdrops(db)
            status = get_sync_status()
            return {"ok": status.get("error") is None, "imported_count": count, "status": status}
        except Exception as exc:
            raise HTTPException(status_code=503, detail="空投数据源暂时不可用，请稍后重试") from exc

@router.get("", response_model=list[AirdropHubItem])
async def list_airdrop_hub(chain: str | None = Query(default=None, max_length=80), db: AsyncSession = Depends(get_db)):
    statement = select(AirdropProject).where(AirdropProject.publication_status == "PUBLISHED_SAFE")
    if chain and chain.strip() and chain.strip().lower() != "all":
        statement = statement.where(AirdropProject.chain.ilike(f"%{chain.strip()}%"))
    rows = (await db.scalars(statement.order_by(AirdropProject.updated_at.desc(), AirdropProject.name.asc()))).all()
    return [AirdropHubItem(id=str(x.id), name=x.name, chain=x.chain, description=x.description, status=x.status, link=x.link, data_source=f"Source: {TRUSTED_AIRDROP_PROVIDER_LABELS.get(x.source_name, x.source_name)}", trust_level=x.trust_level, publication_status=x.publication_status) for x in rows]

@router.get("/status", response_model=AirdropHubSyncStatus)
async def airdrop_hub_sync_status() -> dict[str, Any]:
    return get_sync_status()

@router.post("/refresh")
@limiter.limit("1/minute")
async def refresh_airdrop_hub(request: Request) -> dict[str, Any]:
    return await _sync_now()

@sync_router.post("/sync")
@limiter.limit("1/minute")
async def sync_airdrop_catalog(request: Request) -> dict[str, Any]:
    return await _sync_now()
