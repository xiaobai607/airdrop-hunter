from typing import Annotated
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.routers.auth import get_current_user
from app.database import get_db
from app.models import Project, Task, User

router = APIRouter(prefix="/api/projects/{project_id}/tasks", tags=["tasks"])
class TaskCreate(BaseModel):
    task_name: str = Field(min_length=1, max_length=240)
    step_order: int | None = Field(default=None, ge=1, le=10000)
class TaskUpdate(BaseModel):
    is_completed: bool
class TaskResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    project_id: str
    task_name: str
    is_completed: bool
    step_order: int

async def owned_project(project_id: UUID, user: User, db: AsyncSession):
    p = await db.scalar(select(Project).where(Project.id == project_id, Project.user_id == user.id))
    if p is None:
        raise HTTPException(404, "项目不存在")
    return p

def serialize(t: Task):
    return TaskResponse(id=str(t.id), project_id=str(t.project_id), task_name=t.task_name, is_completed=t.is_completed, step_order=t.step_order)

@router.get("", response_model=list[TaskResponse])
async def list_tasks(project_id: UUID, db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    await owned_project(project_id, user, db)
    rows = await db.scalars(select(Task).where(Task.project_id == project_id, Task.user_id == user.id).order_by(Task.step_order, Task.created_at))
    return [serialize(t) for t in rows]

@router.post("", response_model=TaskResponse, status_code=201)
async def create_task(project_id: UUID, payload: TaskCreate, db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    await owned_project(project_id, user, db)
    name = payload.task_name.strip()
    if not name:
        raise HTTPException(422, "任务名称不能为空")
    next_order = payload.step_order
    if next_order is None:
        current = await db.scalar(select(func.max(Task.step_order)).where(Task.project_id == project_id, Task.user_id == user.id))
        next_order = int(current or 0) + 1
    t = Task(user_id=user.id, project_id=project_id, task_name=name, step_order=next_order)
    db.add(t)
    await db.commit()
    await db.refresh(t)
    return serialize(t)

@router.patch("/{task_id}", response_model=TaskResponse)
async def update_task(project_id: UUID, task_id: UUID, payload: TaskUpdate, db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    await owned_project(project_id, user, db)
    t = await db.scalar(select(Task).where(Task.id == task_id, Task.project_id == project_id, Task.user_id == user.id))
    if t is None:
        raise HTTPException(404, "任务不存在")
    t.is_completed = payload.is_completed
    await db.commit()
    await db.refresh(t)
    return serialize(t)

@router.delete("/{task_id}", status_code=204)
async def delete_task(project_id: UUID, task_id: UUID, db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    await owned_project(project_id, user, db)
    t = await db.scalar(select(Task).where(Task.id == task_id, Task.project_id == project_id, Task.user_id == user.id))
    if t is None:
        raise HTTPException(404, "任务不存在")
    await db.delete(t)
    await db.commit()
    return Response(status_code=204)
