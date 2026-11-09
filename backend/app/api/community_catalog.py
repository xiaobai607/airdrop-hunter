import os
import re
import secrets
from typing import Annotated

from fastapi import APIRouter, Depends, Header, HTTPException, status
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import CommunityProjectCatalogEntry

router = APIRouter(prefix="/api/community/catalog", tags=["community-catalog"])
PROJECT_ID = re.compile(r"^[a-z0-9][a-z0-9_-]{0,119}$")


class CatalogWrite(BaseModel):
    project_official_id: str = Field(min_length=1, max_length=120)
    name: str = Field(min_length=1, max_length=180)
    chain: str = Field(default="Unknown", max_length=120)
    logo: str = Field(default="", max_length=12)
    description: str = Field(default="", max_length=5000)
    tags: list[str] = Field(default_factory=list, max_length=20)
    website_url: str = Field(default="", max_length=1000)
    is_active: bool = False
    sort_order: int = Field(default=100, ge=0, le=100000)

    @field_validator("project_official_id")
    @classmethod
    def validate_id(cls, value: str) -> str:
        value = value.strip().lower()
        if not PROJECT_ID.fullmatch(value):
            raise ValueError("项目标识只能包含小写字母、数字、下划线和连字符")
        return value

    @field_validator("name", "chain", "description", "logo", mode="before")
    @classmethod
    def trim_text(cls, value):
        return value.strip() if isinstance(value, str) else value

    @field_validator("name")
    @classmethod
    def require_name(cls, value: str) -> str:
        if not value:
            raise ValueError("项目名称不能为空")
        return value

    @field_validator("tags")
    @classmethod
    def normalize_tags(cls, value: list[str]) -> list[str]:
        tags = list(dict.fromkeys(tag.strip() for tag in value if tag.strip()))
        if any(len(tag) > 32 for tag in tags):
            raise ValueError("单个标签不能超过32个字符")
        return tags

    @field_validator("website_url")
    @classmethod
    def https_only(cls, value: str) -> str:
        value = value.strip()
        if value and not value.startswith("https://"):
            raise ValueError("项目官网必须使用 HTTPS")
        return value


class CatalogResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    project_official_id: str
    name: str
    chain: str
    logo: str
    description: str
    tags: list[str]
    website_url: str


def require_catalog_admin(token: Annotated[str | None, Header(alias="X-Catalog-Admin-Token")] = None) -> None:
    expected = os.getenv("COMMUNITY_CATALOG_ADMIN_TOKEN", "")
    if not expected:
        raise HTTPException(status_code=503, detail="社区目录管理尚未配置")
    if token is None or not secrets.compare_digest(token, expected):
        raise HTTPException(status_code=403, detail="无权管理社区目录")


@router.get("", response_model=list[CatalogResponse])
async def list_community_catalog(db: Annotated[AsyncSession, Depends(get_db)]):
    rows = await db.scalars(
        select(CommunityProjectCatalogEntry)
        .where(CommunityProjectCatalogEntry.is_active.is_(True))
        .order_by(CommunityProjectCatalogEntry.sort_order.asc(), CommunityProjectCatalogEntry.name.asc())
    )
    return list(rows)


@router.post("", response_model=CatalogResponse, status_code=status.HTTP_201_CREATED,
             dependencies=[Depends(require_catalog_admin)])
async def create_catalog_entry(payload: CatalogWrite, db: Annotated[AsyncSession, Depends(get_db)]):
    existing = await db.get(CommunityProjectCatalogEntry, payload.project_official_id)
    if existing is not None:
        raise HTTPException(status_code=409, detail="项目标识已存在")
    entry = CommunityProjectCatalogEntry(**payload.model_dump())
    db.add(entry)
    await db.commit()
    await db.refresh(entry)
    return entry


@router.put("/{project_official_id}", response_model=CatalogResponse,
            dependencies=[Depends(require_catalog_admin)])
async def update_catalog_entry(project_official_id: str, payload: CatalogWrite,
                               db: Annotated[AsyncSession, Depends(get_db)]):
    if project_official_id != payload.project_official_id:
        raise HTTPException(status_code=409, detail="路径项目标识与提交内容不一致")
    entry = await db.get(CommunityProjectCatalogEntry, project_official_id)
    if entry is None:
        raise HTTPException(status_code=404, detail="社区项目不存在")
    for key, value in payload.model_dump().items():
        setattr(entry, key, value)
    await db.commit()
    await db.refresh(entry)
    return entry


@router.delete("/{project_official_id}", status_code=status.HTTP_204_NO_CONTENT,
               dependencies=[Depends(require_catalog_admin)])
async def deactivate_catalog_entry(project_official_id: str, db: Annotated[AsyncSession, Depends(get_db)]):
    entry = await db.get(CommunityProjectCatalogEntry, project_official_id)
    if entry is None:
        raise HTTPException(status_code=404, detail="社区项目不存在")
    entry.is_active = False
    await db.commit()
