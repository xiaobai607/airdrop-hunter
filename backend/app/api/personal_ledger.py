import json
import math
import re
from datetime import datetime, timezone
from decimal import Decimal
from typing import Annotated, Any
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.models import PersonalLedgerEntry, User
from app.routers.auth import get_current_user

router = APIRouter(prefix="/api/personal-ledger", tags=["personal-ledger"])
MAX_AMOUNT = Decimal("999999999999.99999999")
MAX_EXTRA_BYTES = 16 * 1024
FORBIDDEN_EXTRA_KEYS = {
    "privatekey", "seedphrase", "mnemonic", "recoveryphrase", "recoverykey",
    "password", "passwordhash", "secret", "secretkey", "accesstoken",
    "refreshtoken", "apikey", "jwt", "walletseed",
}


def _validate_extra_value(value: Any, depth: int = 0) -> None:
    if depth > 6:
        raise ValueError("附加信息嵌套层级过深")
    if isinstance(value, dict):
        if len(value) > 40:
            raise ValueError("附加信息字段过多")
        for key, child in value.items():
            if not isinstance(key, str) or not key or len(key) > 64:
                raise ValueError("附加信息字段名称无效")
            normalized = re.sub(r"[^a-z0-9]", "", key.casefold())
            if any(token in normalized for token in FORBIDDEN_EXTRA_KEYS):
                raise ValueError("附加信息不能包含密码、私钥、助记词或访问凭据")
            _validate_extra_value(child, depth + 1)
    elif isinstance(value, list):
        if len(value) > 100:
            raise ValueError("附加信息列表过长")
        for child in value:
            _validate_extra_value(child, depth + 1)
    elif isinstance(value, str):
        if len(value) > 4096:
            raise ValueError("附加信息文本过长")
    elif isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("附加信息数值无效")
    elif isinstance(value, int) and not isinstance(value, bool):
        if abs(value) > 9_223_372_036_854_775_807:
            raise ValueError("附加信息数值超出范围")
    elif value is not None and not isinstance(value, bool):
        raise ValueError("附加信息包含不支持的数据类型")


class PersonalLedgerWrite(BaseModel):
    client_record_id: str = Field(min_length=1, max_length=64)
    project_id: str | None = Field(default=None, max_length=120)
    project_name: str = Field(default="", max_length=180)
    chain: str = Field(default="unknown", min_length=1, max_length=40)
    wallet_address: str = Field(default="", max_length=160)
    tx_hash: str = Field(default="", max_length=180)
    interaction_method: str = Field(default="", max_length=120)
    expected_gas_cost_usd: Decimal | None = Field(default=None, ge=0, le=MAX_AMOUNT)
    expected_reward_usd: Decimal | None = Field(default=None, ge=0, le=MAX_AMOUNT)
    actual_gas_cost_usd: Decimal = Field(default=Decimal("0"), ge=0, le=MAX_AMOUNT)
    actual_reward_usd: Decimal = Field(default=Decimal("0"), ge=0, le=MAX_AMOUNT)
    occurred_at: datetime
    extra: dict = Field(default_factory=dict)

    @field_validator("extra")
    @classmethod
    def validate_extra(cls, value: dict) -> dict:
        if len(value) > 40:
            raise ValueError("附加信息字段过多")
        _validate_extra_value(value)
        try:
            encoded = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        except (TypeError, ValueError, RecursionError) as exc:
            raise ValueError("附加信息格式无效") from exc
        if len(encoded) > MAX_EXTRA_BYTES:
            raise ValueError("附加信息不能超过16KB")
        return value

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
    expected_gas_cost_usd: Decimal | None
    expected_reward_usd: Decimal | None
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
    offset: int = Query(default=0, ge=0, le=1000000),
):
    statement = select(PersonalLedgerEntry).where(PersonalLedgerEntry.user_id == user.id)
    if project_id is not None:
        statement = statement.where(PersonalLedgerEntry.project_id == project_id)
    if before is not None:
        statement = statement.where(PersonalLedgerEntry.occurred_at < before)
    # Stable secondary ordering plus offset pagination prevents rows sharing the
    # same timestamp from disappearing between pages.
    rows = await db.scalars(
        statement.order_by(PersonalLedgerEntry.occurred_at.desc(), PersonalLedgerEntry.id.desc())
        .offset(offset)
        .limit(limit)
    )
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
