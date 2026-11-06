from fastapi import APIRouter
from pydantic import BaseModel


router = APIRouter(prefix="/api", tags=["projects"])


class ProjectResponse(BaseModel):
    id: str
    name: str
    description: str
    status: str


# Temporary fixture data. Replace with database-backed queries after
# project/campaign schemas and migrations have been reviewed.
MOCK_PROJECTS = [
    ProjectResponse(
        id="demo-001",
        name="Example Protocol",
        description="演示项目：用于验证 Android 与 FastAPI 的数据通信。",
        status="tracking",
    ),
    ProjectResponse(
        id="demo-002",
        name="Sample Network",
        description="演示项目：真实活动数据源尚未接入。",
        status="upcoming",
    ),
]


@router.get("/projects", response_model=list[ProjectResponse])
async def list_projects() -> list[ProjectResponse]:
    return MOCK_PROJECTS
