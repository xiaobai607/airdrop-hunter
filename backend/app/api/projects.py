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

class ProjectCreate(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    description: str = Field(default="", max_length=5000)
    status: ProjectStatus = "upcoming"

class ProjectUpdate(ProjectCreate):
    pass

class ProjectResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    name: str
    description: str
    status: ProjectStatus

def serialize_project(p: Project) -> ProjectResponse:
    return ProjectResponse(id=str(p.id), name=p.name, description=p.description, status=p.status)

@router.get("/projects", response_model=list[ProjectResponse])
async def list_projects(db: Annotated[AsyncSession, Depends(get_db)],
                        user: Annotated[User, Depends(get_current_user)]) -> list[ProjectResponse]:
    result = await db.execute(select(Project).where(Project.user_id == user.id).order_by(Project.created_at.desc()))
    return [serialize_project(p) for p in result.scalars().all()]

@router.post("/projects", response_model=ProjectResponse, status_code=status.HTTP_201_CREATED)
async def create_project(payload: ProjectCreate, db: Annotated[AsyncSession, Depends(get_db)],
                         user: Annotated[User, Depends(get_current_user)]) -> ProjectResponse:
    name = payload.name.strip()
    if not name:
        raise HTTPException(422, "项目名称不能为空")
    p = Project(user_id=user.id, name=name, description=payload.description.strip(), status=payload.status)
    db.add(p)
    await db.commit()
    await db.refresh(p)
    return serialize_project(p)

@router.put("/projects/{project_id}", response_model=ProjectResponse)
async def update_project(project_id: UUID, payload: ProjectUpdate,
                         db: Annotated[AsyncSession, Depends(get_db)],
                         user: Annotated[User, Depends(get_current_user)]) -> ProjectResponse:
    p = await db.scalar(select(Project).where(Project.id == project_id, Project.user_id == user.id))
    if p is None:
        raise HTTPException(404, "项目不存在")
    name = payload.name.strip()
    if not name:
        raise HTTPException(422, "项目名称不能为空")
    p.name, p.description, p.status = name, payload.description.strip(), payload.status
    await db.commit()
    await db.refresh(p)
    return serialize_project(p)

@router.delete("/projects/{project_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_project(project_id: UUID, db: Annotated[AsyncSession, Depends(get_db)],
                         user: Annotated[User, Depends(get_current_user)]) -> Response:
    p = await db.scalar(select(Project).where(Project.id == project_id, Project.user_id == user.id))
    if p is None:
        raise HTTPException(404, "项目不存在")
    await db.delete(p)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
