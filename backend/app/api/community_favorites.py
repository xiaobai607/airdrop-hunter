import re
import time
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import CommunityFavorite, User
from app.routers.auth import get_current_user

router = APIRouter(prefix="/api/community/favorites", tags=["community-favorites"])
PROJECT_ID = re.compile(r"^[a-z0-9][a-z0-9_-]{0,119}$")


class FavoriteState(BaseModel):
    project_official_id: str = Field(min_length=1, max_length=120)
    project_name: str = Field(min_length=1, max_length=180)
    is_favorite: bool
    updated_at: int = Field(ge=0)

    @field_validator("project_name")
    @classmethod
    def normalize_project_name(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("项目名称不能为空")
        return value

    @field_validator("updated_at")
    @classmethod
    def reject_future_timestamp(cls, value: int) -> int:
        if value > int(time.time() * 1000) + 5 * 60 * 1000:
            raise ValueError("收藏更新时间不能超过当前时间5分钟")
        return value


class FavoriteSyncRequest(BaseModel):
    items: list[FavoriteState] = Field(default_factory=list, max_length=2000)


def serialize(row: CommunityFavorite) -> dict:
    return {
        "project_official_id": row.project_official_id,
        "project_name": row.project_name,
        "is_favorite": row.is_favorite,
        "updated_at": row.updated_at,
    }


@router.get("", response_model=list[FavoriteState])
async def get_community_favorites(
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
):
    rows = await db.scalars(
        select(CommunityFavorite)
        .where(CommunityFavorite.user_id == user.id)
        .order_by(CommunityFavorite.project_official_id.asc())
    )
    return [serialize(row) for row in rows]


@router.put("/sync", response_model=list[FavoriteState])
async def sync_community_favorites(
    payload: FavoriteSyncRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
):
    seen: set[str] = set()
    for item in payload.items:
        project_id = item.project_official_id.strip()
        if not PROJECT_ID.fullmatch(project_id):
            raise HTTPException(status_code=422, detail="项目标识无效")
        if project_id in seen:
            raise HTTPException(status_code=422, detail="收藏列表包含重复项目")
        seen.add(project_id)
        row = await db.get(CommunityFavorite, (user.id, project_id))
        if row is None:
            db.add(CommunityFavorite(
                user_id=user.id,
                project_official_id=project_id,
                project_name=item.project_name.strip(),
                is_favorite=item.is_favorite,
                updated_at=item.updated_at,
            ))
        elif item.updated_at >= row.updated_at:
            row.project_name = item.project_name.strip()
            row.is_favorite = item.is_favorite
            row.updated_at = item.updated_at
    await db.commit()
    rows = await db.scalars(
        select(CommunityFavorite)
        .where(CommunityFavorite.user_id == user.id)
        .order_by(CommunityFavorite.project_official_id.asc())
    )
    return [serialize(row) for row in rows]
