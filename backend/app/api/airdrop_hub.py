from datetime import datetime, timezone
import os
from uuid import UUID
from typing import Any
from fastapi import APIRouter, Depends, HTTPException, Query, Request
from pydantic import BaseModel, ConfigDict
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import AsyncSessionLocal, get_db
from app.routers.auth import limiter
from app.models import AirdropDiscoverySourceStatus, AirdropProject
from app.services.airdrop_hub_sync import OBSERVATION, fetch_binance_article_detail, get_sync_status, sync_airdrop_hub
from app.services.airdrop_campaign_scoring import recalculate_campaign_scores

# Compatibility alias for existing sync route/tests; this is the approved four-source pipeline.
sync_airdrops = sync_airdrop_hub

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
    published_at: datetime | None = None


class AirdropHubDetail(BaseModel):
    title: str
    content_html: str
    release_date: int | None = None
    catalog_name: str = ""
    original_url: str

class AirdropHubSyncStatus(BaseModel):
    last_attempt_at: str | None
    last_success_at: str | None
    imported_count: int
    source: str
    error: str | None

class AirdropHubSourceStatus(BaseModel):
    source_name: str
    label: str
    enabled: bool
    status: str
    last_attempt_at: datetime | None = None
    last_success_at: datetime | None = None
    last_item_count: int = 0
    last_error: str | None = None

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
            campaign_scoring = await recalculate_campaign_scores(db)
            status = get_sync_status()
            return {
                "ok": status.get("error") is None,
                "imported_count": count,
                "status": status,
                "campaign_scoring": campaign_scoring,
            }
        except Exception as exc:
            raise HTTPException(status_code=503, detail="空投数据源暂时不可用，请稍后重试") from exc

@router.get("", response_model=list[AirdropHubItem])
async def list_airdrop_hub(chain: str | None = Query(default=None, max_length=80), db: AsyncSession = Depends(get_db)):
    statement = select(AirdropProject).where(
        AirdropProject.source_name == "binance-announcements",
        AirdropProject.publication_status == OBSERVATION,
        AirdropProject.filter_status != "filtered",
    )
    if chain and chain.strip() and chain.strip().lower() != "all":
        statement = statement.where(AirdropProject.chain.ilike(f"%{chain.strip()}%"))
    rows = (await db.scalars(statement.order_by(AirdropProject.updated_at.desc(), AirdropProject.name.asc()))).all()
    return [
        AirdropHubItem(
            id=str(x.id), name=x.name, chain=x.chain, description=x.description,
            status=x.status, link=x.link,
            published_at=(x.filter_metadata or {}).get("published_at"),
        )
        for x in rows
    ]


@router.get("/status", response_model=AirdropHubSyncStatus)
async def airdrop_hub_sync_status() -> dict[str, Any]:
    return get_sync_status()


@router.get("/sources/status", response_model=list[AirdropHubSourceStatus])
async def airdrop_hub_source_status(db: AsyncSession = Depends(get_db)) -> list[dict[str, Any]]:
    rows = list(await db.scalars(
        select(AirdropDiscoverySourceStatus).order_by(
            AirdropDiscoverySourceStatus.last_attempt_at.desc(),
            AirdropDiscoverySourceStatus.id.desc(),
        )
    ))
    grouped: dict[str, list[AirdropDiscoverySourceStatus]] = {}
    for row in rows:
        grouped.setdefault(row.source_name, []).append(row)

    optional_enabled = {
        "coingecko-airdrop-category": bool(os.getenv("COINGECKO_DEMO_API_KEY", "").strip()),
        "github-airdrop-lists": bool(os.getenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", "").strip()),
    }
    definitions = (
        ("binance-announcements", "Binance 官方公告", True, "未配置 Binance 来源"),
        ("coingecko-airdrop-category", "CoinGecko 空投分类", optional_enabled["coingecko-airdrop-category"], "未配置免费 Demo API Key"),
        ("github-airdrop-lists", "GitHub 人工审核名单", optional_enabled["github-airdrop-lists"], "未配置已审核的 Raw 来源"),
        ("dexscreener-new-pools", "DexScreener 市场线索", True, "来源尚未运行"),
    )
    result: list[dict[str, Any]] = []
    for source_name, label, enabled, disabled_message in definitions:
        source_rows = grouped.get(source_name, [])
        latest = source_rows[0] if source_rows else None
        if not enabled:
            status = "disabled"
            last_error = disabled_message
        elif latest is None:
            status = "not_run"
            last_error = None
        else:
            status = latest.last_status
            last_error = latest.last_error
            if len(source_rows) > 1:
                successful = any(row.last_status == "success" for row in source_rows)
                failed = any(row.last_status == "failed" for row in source_rows)
                if successful and failed:
                    status = "partial"
        result.append({
            "source_name": source_name,
            "label": label,
            "enabled": enabled,
            "status": status,
            "last_attempt_at": latest.last_attempt_at if latest else None,
            "last_success_at": max(
                (row.last_success_at for row in source_rows if row.last_success_at is not None),
                default=None,
            ),
            "last_item_count": sum(max(0, row.last_item_count) for row in source_rows),
            "last_error": last_error,
        })
    return result


@router.get("/{item_id}", response_model=AirdropHubDetail)
async def get_airdrop_hub_detail(item_id: str, db: AsyncSession = Depends(get_db)) -> dict[str, Any]:
    try:
        parsed_id = UUID(item_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail="空投公告不存在") from exc
    item = await db.scalar(select(AirdropProject).where(AirdropProject.id == parsed_id))
    if (
        item is None
        or item.source_name != "binance-announcements"
        or item.publication_status != OBSERVATION
        or item.filter_status == "filtered"
    ):
        raise HTTPException(status_code=404, detail="空投公告不存在")
    prefix = "binance:"
    if not item.source_key.startswith(prefix):
        raise HTTPException(status_code=404, detail="空投公告编号无效")
    article_code = item.source_key[len(prefix):]
    try:
        return await fetch_binance_article_detail(
            article_code, fallback_title=item.name, fallback_url=item.link
        )
    except Exception as exc:
        raise HTTPException(
            status_code=502,
            detail="暂时无法获取 Binance 公告正文，请稍后重试或打开官方原文",
        ) from exc

@router.post("/refresh")
@limiter.limit("1/minute")
async def refresh_airdrop_hub(request: Request) -> dict[str, Any]:
    return await _sync_now()

@sync_router.post("/sync")
@limiter.limit("1/minute")
async def sync_airdrop_catalog(request: Request) -> dict[str, Any]:
    return await _sync_now()
