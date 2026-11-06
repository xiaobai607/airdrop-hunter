from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response, status
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import Project


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


def serialize_project(project: Project) -> ProjectResponse:
    return ProjectResponse(
        id=str(project.id),
        name=project.name,
        description=project.description,
        status=project.status,
    )


@router.get("/projects", response_model=list[ProjectResponse])
async def list_projects(db: AsyncSession = Depends(get_db)) -> list[ProjectResponse]:
    result = await db.execute(select(Project).order_by(Project.created_at.desc()))
    return [serialize_project(project) for project in result.scalars().all()]


@router.post(
    "/projects",
    response_model=ProjectResponse,
    status_code=status.HTTP_201_CREATED,
)
async def create_project(
    payload: ProjectCreate,
    db: AsyncSession = Depends(get_db),
) -> ProjectResponse:
    name = payload.name.strip()
    if not name:
        raise HTTPException(status_code=422, detail="项目名称不能为空")
    project = Project(
        name=name,
        description=payload.description.strip(),
        status=payload.status,
    )
    db.add(project)
    await db.commit()
    await db.refresh(project)
    return serialize_project(project)


@router.put("/projects/{project_id}", response_model=ProjectResponse)
async def update_project(
    project_id: UUID,
    payload: ProjectUpdate,
    db: AsyncSession = Depends(get_db),
) -> ProjectResponse:
    project = await db.get(Project, project_id)
    if project is None:
        raise HTTPException(status_code=404, detail="项目不存在")
    name = payload.name.strip()
    if not name:
        raise HTTPException(status_code=422, detail="项目名称不能为空")
    project.name = name
    project.description = payload.description.strip()
    project.status = payload.status
    await db.commit()
    await db.refresh(project)
    return serialize_project(project)


@router.delete("/projects/{project_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_project(
    project_id: UUID,
    db: AsyncSession = Depends(get_db),
) -> Response:
    project = await db.get(Project, project_id)
    if project is None:
        raise HTTPException(status_code=404, detail="项目不存在")
    await db.delete(project)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
