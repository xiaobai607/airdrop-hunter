from datetime import datetime, timezone
from decimal import Decimal
from typing import Annotated
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.models import PersonalLedgerEntry, User
from app.routers.auth import get_current_user

router = APIRouter(prefix="/api/personal-ledger", tags=["personal-ledger"])
MAX_AMOUNT = Decimal("999999999999.99999999")

class PersonalLedgerWrite(BaseModel):
    client_record_id: str = Field(min_length=1, max_length=64)
    project_id: str | None = Field(default=None, max_length=120)
    project_name: str = Field(default="", max_length=180)
    chain: str = Field(default="unknown", min_length=1, max_length=40)
    wallet_address: str = Field(default="", max_length=160)
    tx_hash: str = Field(default="", max_length=180)
    interaction_method: str = Field(default="", max_length=120)
    expected_gas_cost_usd: Decimal = Field(default=Decimal("0"), ge=0, le=MAX_AMOUNT)
    expected_reward_usd: Decimal = Field(default=Decimal("0"), ge=0, le=MAX_AMOUNT)
    actual_gas_cost_usd: Decimal = Field(default=Decimal("0"), ge=0, le=MAX_AMOUNT)
    actual_reward_usd: Decimal = Field(default=Decimal("0"), ge=0, le=MAX_AMOUNT)
    occurred_at: datetime
    extra: dict = Field(default_factory=dict)

class PersonalLedgerResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    client_record_id: str
    project_id: str | None
    project_name: str
    chain: str
    wallet_address: str
    tx_hash: str
    interaction_method: str
    expected_gas_cost_usd: Decimal
    expected_reward_usd: Decimal
    actual_gas_cost_usd: Decimal
    actual_reward_usd: Decimal
    occurred_at: datetime
    extra: dict

def serialize(row: PersonalLedgerEntry) -> PersonalLedgerResponse:
    return PersonalLedgerResponse.model_validate(row)

@router.get("", response_model=list[PersonalLedgerResponse])
async def list_personal_ledger(
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
    project_id: str | None = Query(default=None, max_length=120),
    limit: int = Query(default=200, ge=1, le=500),
    before: datetime | None = None,
):
    statement = select(PersonalLedgerEntry).where(PersonalLedgerEntry.user_id == user.id)
    if project_id is not None:
        statement = statement.where(PersonalLedgerEntry.project_id == project_id)
    if before is not None:
        statement = statement.where(PersonalLedgerEntry.occurred_at < before)
    rows = await db.scalars(statement.order_by(PersonalLedgerEntry.occurred_at.desc()).limit(limit))
    return [serialize(row) for row in rows]

@router.put("/{client_record_id}", response_model=PersonalLedgerResponse)
async def upsert_personal_ledger(
    client_record_id: str,
    payload: PersonalLedgerWrite,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
):
    if client_record_id != payload.client_record_id:
        raise HTTPException(status_code=422, detail="记录标识不匹配")
    if payload.occurred_at.tzinfo is None:
        raise HTTPException(status_code=422, detail="记录时间必须包含时区")
    if len(payload.extra) > 40:
        raise HTTPException(status_code=422, detail="附加信息字段过多")
    values = payload.model_dump()
    values["user_id"] = user.id
    statement = insert(PersonalLedgerEntry).values(**values).on_conflict_do_update(
        constraint="uq_personal_ledger_user_client_record",
        set_={key: value for key, value in values.items() if key not in ("user_id", "client_record_id")},
    ).returning(PersonalLedgerEntry)
    row = (await db.execute(statement)).scalar_one()
    await db.commit()
    await db.refresh(row)
    return serialize(row)

@router.delete("/{client_record_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_personal_ledger(
    client_record_id: str,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
):
    row = await db.scalar(select(PersonalLedgerEntry).where(
        PersonalLedgerEntry.user_id == user.id,
        PersonalLedgerEntry.client_record_id == client_record_id,
    ))
    if row is None:
        raise HTTPException(status_code=404, detail="记录不存在")
    await db.delete(row)
    await db.commit()
