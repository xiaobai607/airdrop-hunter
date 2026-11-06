from typing import Annotated
from decimal import Decimal
from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.api.auth import get_current_user
from app.database import get_db
from app.models import Project, User

router = APIRouter(prefix="/api", tags=["statistics"])
@router.get("/stats")
async def user_stats(db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    row=(await db.execute(select(func.coalesce(func.sum(Project.actual_income),0),
        func.coalesce(func.sum(Project.expected_income),0),func.coalesce(func.sum(Project.gas_cost),0),
        func.count(Project.id)).where(Project.user_id==user.id))).one()
    actual,expected,gas,count=row
    actual,expected,gas=Decimal(str(actual or 0)),Decimal(str(expected or 0)),Decimal(str(gas or 0))
    return {"currency":"USD","project_count":count,"total_actual_income":float(actual),
        "total_expected_income":float(expected),"total_gas_cost":float(gas),
        "net_realized_income":float(actual-gas)}
