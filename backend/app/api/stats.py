from typing import Annotated
from decimal import Decimal
from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.routers.auth import get_current_user
from app.database import get_db
from app.models import PersonalLedgerEntry, Project, Task, User, Wallet

router = APIRouter(prefix="/api", tags=["statistics"])

@router.get("/stats")
async def user_stats(db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    row = (await db.execute(select(
        func.coalesce(func.sum(Project.actual_income), 0),
        func.coalesce(func.sum(Project.expected_income), 0),
        func.coalesce(func.sum(Project.gas_cost), 0),
        func.count(Project.id)
    ).where(Project.user_id == user.id))).one()
    wallet_row = (await db.execute(select(
        func.coalesce(func.sum(Wallet.daily_gas_usd), 0),
        func.coalesce(func.sum(Wallet.recent_airdrop_income_usd), 0),
        func.count(Wallet.id)
    ).where(Wallet.user_id == user.id))).one()
    actual, expected, project_gas, count = row
    wallet_gas, wallet_income, wallet_count = wallet_row
    actual = Decimal(str(actual or 0)) + Decimal(str(wallet_income or 0))
    gas = Decimal(str(project_gas or 0)) + Decimal(str(wallet_gas or 0))
    return {
        "currency": "USD", "project_count": count, "wallet_count": wallet_count,
        "total_actual_income": float(actual), "total_expected_income": float(expected or 0),
        "total_gas_cost": float(gas), "project_gas_cost": float(project_gas or 0),
        "wallet_daily_gas": float(wallet_gas or 0), "wallet_airdrop_income": float(wallet_income or 0),
        "net_realized_income": float(actual - gas)
    }

def _ledger_usd_profit_complete(extra: dict | None) -> bool:
    """Interpret direct-entry valuation metadata; legacy rows remain compatible."""
    if not isinstance(extra, dict):
        return True
    explicit = extra.get("usd_profit_complete")
    if explicit is not None:
        return str(explicit).strip().lower() in {"true", "1", "yes"}

    raw_kind = extra.get("reward_kind")
    if raw_kind is None or not str(raw_kind).strip():
        # Older confirmed-bill rows predate reward-kind metadata.
        return True
    kind = str(raw_kind).strip().upper()
    if kind in {"POINTS", "UNISSUED", "UNKNOWN"}:
        return False
    if kind == "TOKEN":
        raw_valuation = extra.get("reward_usd_valuation")
        try:
            valuation = Decimal(str(raw_valuation))
        except Exception:
            return False
        return valuation.is_finite() and valuation >= 0
    return kind in {"USD", "MANUAL_USD_VALUATION"}


@router.get("/user/stats")
async def current_user_stats(db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    tracked = await db.scalar(select(func.count(Project.id)).where(Project.user_id == user.id))
    completed = await db.scalar(select(func.count(Project.id)).where(Project.user_id == user.id, Project.status == "completed"))
    income = await db.scalar(select(func.coalesce(func.sum(Project.actual_income), 0)).where(Project.user_id == user.id))
    # Preserve the existing task-based activity metric for compatibility.
    interaction_days = await db.scalar(select(func.count(func.distinct(func.date(Task.created_at)))).where(Task.user_id == user.id))
    ledger_totals = (await db.execute(select(
        func.count(PersonalLedgerEntry.id).label("interactions"),
        func.count(func.distinct(func.date(PersonalLedgerEntry.occurred_at))).label("interaction_days")
    ).where(PersonalLedgerEntry.user_id == user.id))).one()
    project_rows = (await db.execute(select(
        PersonalLedgerEntry.project_id.label("project_id"),
        PersonalLedgerEntry.actual_gas_cost_usd.label("gas"),
        PersonalLedgerEntry.actual_reward_usd.label("reward"),
        PersonalLedgerEntry.extra.label("extra")
    ).where(
        PersonalLedgerEntry.user_id == user.id,
        PersonalLedgerEntry.project_id.is_not(None),
        PersonalLedgerEntry.project_id != ""
    ))).all()

    # Match the local ledger rule: Gas remains countable, but a project with
    # any incomplete USD reward valuation is excluded from reward/P&L/win rate.
    projects: dict[str, dict] = {}
    for row in project_rows:
        project_id = str(row.project_id)
        totals = projects.setdefault(project_id, {
            "gas": Decimal("0"),
            "reward": Decimal("0"),
            "complete": True,
        })
        totals["gas"] += Decimal(str(row.gas or 0))
        totals["reward"] += Decimal(str(row.reward or 0))
        totals["complete"] = totals["complete"] and _ledger_usd_profit_complete(row.extra)

    ledger_gas = sum((item["gas"] for item in projects.values()), Decimal("0"))
    evaluated = [item for item in projects.values() if item["complete"]]
    ledger_reward = sum((item["reward"] for item in evaluated), Decimal("0"))
    ledger_net = sum((item["reward"] - item["gas"] for item in evaluated), Decimal("0"))
    evaluated_projects = len(evaluated)
    profitable_projects = sum(1 for item in evaluated if item["reward"] > item["gas"])
    win_rate = profitable_projects * 100.0 / evaluated_projects if evaluated_projects else 0.0
    return {
        "tracked_projects": int(tracked or 0), "completed_projects": int(completed or 0),
        "total_actual_income": float(income or 0), "interaction_days": int(interaction_days or 0),
        "ledger_project_count": len(projects), "ledger_total_gas_cost": float(ledger_gas),
        "ledger_total_actual_reward": float(ledger_reward), "ledger_net_profit": float(ledger_net),
        "profitable_projects": profitable_projects, "evaluated_projects": evaluated_projects,
        "win_rate": win_rate, "ledger_interaction_days": int(ledger_totals.interaction_days or 0),
        "total_interactions": int(ledger_totals.interactions or 0),
        "usd_profit_complete": all(item["complete"] for item in projects.values())
    }
