from datetime import datetime
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import or_, select
from urllib.parse import urlparse
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import Airdrop, User
from app.routers.auth import get_current_user, get_optional_current_user


router = APIRouter(prefix="/api/airdrops", tags=["airdrops"])
AirdropStatus = Literal["upcoming", "ongoing", "ended", "unknown"]
SourceType = Literal["official", "aggregator", "manual"]
VerificationStatus = Literal["unverified", "source_linked", "verified"]


class AirdropWrite(BaseModel):
    name: str = Field(min_length=1, max_length=160)
    description: str = Field(default="", max_length=10000)
    category: str = Field(default="other", max_length=80)
    chain: str = Field(default="", max_length=80)
    status: AirdropStatus = "unknown"
    reward_details: str = Field(default="", max_length=5000)
    eligibility: str = Field(default="", max_length=5000)
    official_url: str = Field(default="", max_length=1000)
    source_name: str = Field(default="手动录入", max_length=120)
    source_url: str = Field(default="", max_length=1000)
    source_type: SourceType = "manual"
    verification_status: VerificationStatus = "unverified"
    starts_at: datetime | None = None
    ends_at: datetime | None = None

    @field_validator("official_url", "source_url")
    @classmethod
    def validate_https_links(cls, value: str) -> str:
        value = value.strip()
        if not value:
            return ""
        parsed = urlparse(value)
        if (
            parsed.scheme.lower() != "https"
            or not parsed.hostname
            or parsed.username is not None
            or parsed.password is not None
        ):
            raise ValueError("项目链接必须是有效的 HTTPS 地址")
        return value


class AirdropResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    name: str
    description: str
    category: str
    chain: str
    status: AirdropStatus
    reward_details: str
    eligibility: str
    official_url: str
    source_name: str
    source_url: str
    source_type: SourceType
    verification_status: VerificationStatus
    starts_at: datetime | None
    ends_at: datetime | None
    last_checked_at: datetime | None
    created_at: datetime
    updated_at: datetime


def serialize_airdrop(item: Airdrop) -> AirdropResponse:
    return AirdropResponse(
        id=str(item.id),
        name=item.name,
        description=item.description,
        category=item.category,
        chain=item.chain,
        status=item.status,
        reward_details=item.reward_details,
        eligibility=item.eligibility,
        official_url=item.official_url,
        source_name=item.source_name,
        source_url=item.source_url,
        source_type=item.source_type,
        verification_status=item.verification_status,
        starts_at=item.starts_at,
        ends_at=item.ends_at,
        last_checked_at=item.last_checked_at,
        created_at=item.created_at,
        updated_at=item.updated_at,
    )


@router.get("", response_model=list[AirdropResponse])
async def list_airdrops(
    status_filter: AirdropStatus | None = Query(default=None, alias="status"),
    chain: str | None = Query(default=None, max_length=80),
    q: str | None = Query(default=None, max_length=160),
    db: AsyncSession = Depends(get_db),
    user: User | None = Depends(get_optional_current_user),
) -> list[AirdropResponse]:
    statement = select(Airdrop)
    if user is None:
        statement = statement.where(Airdrop.user_id.is_(None))
    else:
        statement = statement.where(
            or_(Airdrop.user_id.is_(None), Airdrop.user_id == user.id)
        )
    if status_filter:
        statement = statement.where(Airdrop.status == status_filter)
    if chain:
        statement = statement.where(Airdrop.chain.ilike(f"%{chain.strip()}%"))
    if q:
        statement = statement.where(Airdrop.name.ilike(f"%{q.strip()}%"))
    statement = statement.order_by(Airdrop.updated_at.desc())
    result = await db.execute(statement)
    return [serialize_airdrop(item) for item in result.scalars().all()]


@router.post("", response_model=AirdropResponse, status_code=status.HTTP_201_CREATED)
async def create_airdrop(
    payload: AirdropWrite,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
) -> AirdropResponse:
    name = payload.name.strip()
    if not name:
        raise HTTPException(status_code=422, detail="项目名称不能为空")
    if payload.starts_at and payload.ends_at and payload.ends_at < payload.starts_at:
        raise HTTPException(status_code=422, detail="结束时间不能早于开始时间")
    item = Airdrop(**payload.model_dump(exclude={"name"}), name=name, user_id=user.id)
    db.add(item)
    await db.commit()
    await db.refresh(item)
    return serialize_airdrop(item)


@router.put("/{airdrop_id}", response_model=AirdropResponse)
async def update_airdrop(
    airdrop_id: UUID,
    payload: AirdropWrite,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
) -> AirdropResponse:
    item = await db.scalar(
        select(Airdrop).where(Airdrop.id == airdrop_id, Airdrop.user_id == user.id)
    )
    if item is None:
        raise HTTPException(status_code=404, detail="空投项目不存在")
    name = payload.name.strip()
    if not name:
        raise HTTPException(status_code=422, detail="项目名称不能为空")
    if payload.starts_at and payload.ends_at and payload.ends_at < payload.starts_at:
        raise HTTPException(status_code=422, detail="结束时间不能早于开始时间")
    for key, value in payload.model_dump().items():
        setattr(item, key, name if key == "name" else value)
    await db.commit()
    await db.refresh(item)
    return serialize_airdrop(item)


@router.delete("/{airdrop_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_airdrop(
    airdrop_id: UUID,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
) -> Response:
    item = await db.scalar(
        select(Airdrop).where(Airdrop.id == airdrop_id, Airdrop.user_id == user.id)
    )
    if item is None:
        raise HTTPException(status_code=404, detail="空投项目不存在")
    await db.delete(item)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
