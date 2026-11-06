from typing import Annotated
from decimal import Decimal
from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.routers.auth import get_current_user
from app.database import get_db
from app.models import Project, Task, User, Wallet

router = APIRouter(prefix="/api", tags=["statistics"])

@router.get("/stats")
async def user_stats(db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    row = (await db.execute(select(func.coalesce(func.sum(Project.actual_income), 0), func.coalesce(func.sum(Project.expected_income), 0), func.coalesce(func.sum(Project.gas_cost), 0), func.count(Project.id)).where(Project.user_id == user.id))).one()
    wallet_row = (await db.execute(select(func.coalesce(func.sum(Wallet.daily_gas_usd), 0), func.coalesce(func.sum(Wallet.recent_airdrop_income_usd), 0), func.count(Wallet.id)).where(Wallet.user_id == user.id))).one()
    actual, expected, project_gas, count = row
    wallet_gas, wallet_income, wallet_count = wallet_row
    actual = Decimal(str(actual or 0)) + Decimal(str(wallet_income or 0))
    gas = Decimal(str(project_gas or 0)) + Decimal(str(wallet_gas or 0))
    return {"currency": "USD", "project_count": count, "wallet_count": wallet_count, "total_actual_income": float(actual), "total_expected_income": float(expected or 0), "total_gas_cost": float(gas), "project_gas_cost": float(project_gas or 0), "wallet_daily_gas": float(wallet_gas or 0), "wallet_airdrop_income": float(wallet_income or 0), "net_realized_income": float(actual - gas)}

@router.get("/user/stats")
async def current_user_stats(db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    tracked = await db.scalar(select(func.count(Project.id)).where(Project.user_id == user.id))
    completed = await db.scalar(select(func.count(Project.id)).where(Project.user_id == user.id, Project.status == "completed"))
    income = await db.scalar(select(func.coalesce(func.sum(Project.actual_income), 0)).where(Project.user_id == user.id))
    # Only persisted task creation dates are counted as interaction history.
    interaction_days = await db.scalar(select(func.count(func.distinct(func.date(Task.created_at)))).where(Task.user_id == user.id))
    return {"tracked_projects": int(tracked or 0), "completed_projects": int(completed or 0), "total_actual_income": float(income or 0), "interaction_days": int(interaction_days or 0)}
