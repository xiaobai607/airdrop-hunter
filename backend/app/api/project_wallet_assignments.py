import re
from uuid import UUID
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.models import Project, ProjectWalletAssignment, User, Wallet
from app.routers.auth import get_current_user

router = APIRouter(prefix="/api/project-wallet-assignments", tags=["project-wallet-assignments"])
COMMUNITY_KEY = re.compile(r"^community:[a-z0-9_-]{1,120}$")


class AssignmentWrite(BaseModel):
    wallet_ids: list[UUID] = Field(default_factory=list, max_length=100)


def validate_project_key(value: str) -> str:
    key = value.strip()
    if COMMUNITY_KEY.fullmatch(key):
        return key
    try:
        UUID(key)
    except ValueError as exc:
        raise HTTPException(422, "项目标识无效") from exc
    return key


async def ensure_project_access(project_key: str, user: User, db: AsyncSession) -> None:
    if project_key.startswith("community:"):
        return
    project = await db.scalar(select(Project.id).where(Project.id == UUID(project_key), Project.user_id == user.id))
    if project is None:
        raise HTTPException(404, "项目不存在")


@router.get("/{project_key}")
async def get_project_wallet_assignments(
    project_key: str,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
):
    key = validate_project_key(project_key)
    await ensure_project_access(key, user, db)
    rows = await db.scalars(
        select(ProjectWalletAssignment.wallet_id)
        .where(ProjectWalletAssignment.user_id == user.id, ProjectWalletAssignment.project_key == key)
        .order_by(ProjectWalletAssignment.created_at.asc())
    )
    return {"project_key": key, "wallet_ids": [str(wallet_id) for wallet_id in rows]}


@router.put("/{project_key}")
async def replace_project_wallet_assignments(
    project_key: str,
    payload: AssignmentWrite,
    db: Annotated[AsyncSession, Depends(get_db)],
    user: Annotated[User, Depends(get_current_user)],
):
    key = validate_project_key(project_key)
    await ensure_project_access(key, user, db)
    wallet_ids = payload.wallet_ids
    if len(wallet_ids) != len(set(wallet_ids)):
        raise HTTPException(422, "钱包列表不能包含重复项")
    if wallet_ids:
        owned = set(await db.scalars(
            select(Wallet.id).where(Wallet.user_id == user.id, Wallet.id.in_(wallet_ids))
        ))
        if owned != set(wallet_ids):
            raise HTTPException(404, "部分钱包不存在或不属于当前账号")

    await db.execute(delete(ProjectWalletAssignment).where(
        ProjectWalletAssignment.user_id == user.id,
        ProjectWalletAssignment.project_key == key,
    ))
    db.add_all([
        ProjectWalletAssignment(user_id=user.id, project_key=key, wallet_id=wallet_id)
        for wallet_id in wallet_ids
    ])
    await db.commit()
    return {"project_key": key, "wallet_ids": [str(wallet_id) for wallet_id in wallet_ids]}
