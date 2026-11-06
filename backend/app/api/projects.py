from datetime import datetime
from decimal import Decimal
from typing import Annotated, Literal
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, Response, status
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.api.auth import get_current_user
from app.database import get_db
from app.models import Project, User

router = APIRouter(prefix="/api", tags=["projects"])
ProjectStatus = Literal["tracking", "upcoming", "completed"]

class ProjectWrite(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    description: str = Field(default="", max_length=5000)
    status: ProjectStatus = "upcoming"
    official_url: str = Field(default="", max_length=1000)
    twitter_url: str = Field(default="", max_length=1000)
    deadline: datetime | None = None
    expected_income: Decimal = Field(default=Decimal("0"), ge=0)
    gas_cost: Decimal = Field(default=Decimal("0"), ge=0)
    actual_income: Decimal = Field(default=Decimal("0"), ge=0)

class ProjectResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    name: str
    description: str
    status: ProjectStatus
    official_url: str
    twitter_url: str
    deadline: datetime | None
    expected_income: Decimal
    gas_cost: Decimal
    actual_income: Decimal

def serialize_project(p: Project) -> ProjectResponse:
    return ProjectResponse(id=str(p.id), name=p.name, description=p.description, status=p.status,
        official_url=p.official_url or "", twitter_url=p.twitter_url or "", deadline=p.deadline,
        expected_income=p.expected_income or Decimal("0"), gas_cost=p.gas_cost or Decimal("0"),
        actual_income=p.actual_income or Decimal("0"))

async def owned_project(project_id: UUID, user: User, db: AsyncSession) -> Project:
    p = await db.scalar(select(Project).where(Project.id == project_id, Project.user_id == user.id))
    if p is None:
        raise HTTPException(404, "项目不存在")
    return p

@router.get("/projects", response_model=list[ProjectResponse])
async def list_projects(db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    rows = await db.scalars(select(Project).where(Project.user_id == user.id).order_by(Project.created_at.desc()))
    return [serialize_project(p) for p in rows]

@router.get("/projects/{project_id}", response_model=ProjectResponse)
async def get_project(project_id: UUID, db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    return serialize_project(await owned_project(project_id, user, db))

@router.post("/projects", response_model=ProjectResponse, status_code=201)
async def create_project(payload: ProjectWrite, db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    data = payload.model_dump()
    data["name"] = payload.name.strip()
    data["description"] = payload.description.strip()
    if not data["name"]:
        raise HTTPException(422, "项目名称不能为空")
    p = Project(user_id=user.id, **data)
    db.add(p)
    await db.commit()
    await db.refresh(p)
    return serialize_project(p)

@router.put("/projects/{project_id}", response_model=ProjectResponse)
async def update_project(project_id: UUID, payload: ProjectWrite, db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    p = await owned_project(project_id, user, db)
    for key, value in payload.model_dump().items():
        setattr(p, key, value.strip() if isinstance(value, str) else value)
    if not p.name:
        raise HTTPException(422, "项目名称不能为空")
    await db.commit()
    await db.refresh(p)
    return serialize_project(p)

@router.delete("/projects/{project_id}", status_code=204)
async def delete_project(project_id: UUID, db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    p = await owned_project(project_id, user, db)
    await db.delete(p)
    await db.commit()
    return Response(status_code=204)
