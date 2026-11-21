from datetime import datetime
from decimal import Decimal
from typing import Annotated, Literal
from uuid import UUID
from urllib.parse import urlencode, urlparse, urlunparse, parse_qsl
from fastapi import APIRouter, Depends, HTTPException, Response, status
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from app.routers.auth import get_current_user
from app.database import get_db
from app.models import Project, ProjectWalletAssignment, User, Wallet

router = APIRouter(prefix="/api", tags=["projects"])
ProjectStatus = Literal["tracking", "upcoming", "completed"]
MAX_AMOUNT = Decimal("999999999999.99999999")

class ProjectWrite(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    description: str = Field(default="", max_length=5000)
    status: ProjectStatus = "upcoming"
    official_url: str = Field(default="", max_length=1000)
    claim_url: str = Field(default="", max_length=1000)
    twitter_url: str = Field(default="", max_length=1000)
    deadline: datetime | None = None
    expected_income: Decimal = Field(default=Decimal("0"), ge=0, le=MAX_AMOUNT)
    gas_cost: Decimal = Field(default=Decimal("0"), ge=0, le=MAX_AMOUNT)
    actual_income: Decimal = Field(default=Decimal("0"), ge=0, le=MAX_AMOUNT)
    client_project_id: UUID | None = None

    @field_validator("official_url", "claim_url", "twitter_url")
    @classmethod
    def validate_https_project_links(cls, value: str) -> str:
        normalized = (value or "").strip()
        if not normalized:
            return ""
        parsed = urlparse(normalized)
        try:
            valid_port = parsed.port is None or 1 <= parsed.port <= 65535
        except ValueError:
            valid_port = False
        if (
            parsed.scheme.lower() != "https"
            or not parsed.hostname
            or parsed.username is not None
            or parsed.password is not None
            or not valid_port
        ):
            raise ValueError("项目链接必须是有效的 HTTPS URL")
        return normalized

class ProjectResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    name: str
    description: str
    status: ProjectStatus
    official_url: str
    claim_url: str
    twitter_url: str
    deadline: datetime | None
    expected_income: Decimal
    gas_cost: Decimal
    actual_income: Decimal
    client_project_id: str | None = None

def serialize_project(p: Project) -> ProjectResponse:
    return ProjectResponse(id=str(p.id), name=p.name, description=p.description, status=p.status,
        official_url=p.official_url or "", claim_url=p.claim_url or "", twitter_url=p.twitter_url or "",
        deadline=p.deadline, expected_income=p.expected_income or Decimal("0"),
        gas_cost=p.gas_cost or Decimal("0"), actual_income=p.actual_income or Decimal("0"),
        client_project_id=str(p.client_project_id) if p.client_project_id else None)

async def owned_project(project_id: UUID, user: User, db: AsyncSession) -> Project:
    p = await db.scalar(select(Project).where(Project.id == project_id, Project.user_id == user.id))
    if p is None: raise HTTPException(404, "项目不存在")
    return p

@router.get("/user/projects", response_model=list[ProjectResponse], include_in_schema=False)
@router.get("/projects", response_model=list[ProjectResponse])
async def list_projects(db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    rows = await db.scalars(select(Project).where(Project.user_id == user.id).order_by(Project.created_at.desc()))
    return [serialize_project(p) for p in rows]

@router.get("/projects/{project_id}/claim-url")
async def build_claim_url(project_id: UUID, wallet_id: UUID, db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    project = await owned_project(project_id, user, db)
    wallet = await db.scalar(select(Wallet).where(Wallet.id == wallet_id, Wallet.user_id == user.id))
    if wallet is None: raise HTTPException(404, "钱包不存在")
    raw_url = (project.claim_url or "").strip()
    parsed = urlparse(raw_url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise HTTPException(409, "该项目尚未配置经过确认的 HTTPS 资格查询入口")
    query = dict(parse_qsl(parsed.query, keep_blank_values=True))
    query["address"] = wallet.address or wallet.public_key
    final_url = urlunparse(parsed._replace(query=urlencode(query)))
    return {"url": final_url, "project_name": project.name, "wallet_name": wallet.wallet_name,
            "address": wallet.address or wallet.public_key}

@router.get("/projects/{project_id}", response_model=ProjectResponse)
async def get_project(project_id: UUID, db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    return serialize_project(await owned_project(project_id, user, db))

@router.post("/projects", response_model=ProjectResponse, status_code=status.HTTP_201_CREATED)
async def create_project(payload: ProjectWrite, db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    data = payload.model_dump(); data["name"] = payload.name.strip(); data["description"] = payload.description.strip()
    if not data["name"]: raise HTTPException(422, "项目名称不能为空")
    if data["claim_url"] and (urlparse(data["claim_url"]).scheme != "https" or not urlparse(data["claim_url"]).hostname):
        raise HTTPException(422, "资格查询入口必须是 HTTPS URL")
    client_project_id = data.pop("client_project_id", None)
    if client_project_id is not None:
        existing = await db.scalar(select(Project).where(
            Project.user_id == user.id,
            Project.client_project_id == client_project_id,
        ))
        if existing is not None:
            return serialize_project(existing)

    p = Project(user_id=user.id, client_project_id=client_project_id, **data)
    db.add(p)
    try:
        await db.commit()
    except IntegrityError:
        # A concurrent retry may have won the unique-key race. Resolve to that
        # already-created row instead of creating a duplicate or failing ambiguously.
        await db.rollback()
        if client_project_id is not None:
            existing = await db.scalar(select(Project).where(
                Project.user_id == user.id,
                Project.client_project_id == client_project_id,
            ))
            if existing is not None:
                return serialize_project(existing)
        raise HTTPException(status_code=409, detail="项目创建冲突，请刷新后重试")
    await db.refresh(p)
    return serialize_project(p)

@router.put("/projects/{project_id}", response_model=ProjectResponse)
async def update_project(project_id: UUID, payload: ProjectWrite, db: Annotated[AsyncSession, Depends(get_db)], user: User = Depends(get_current_user)):
    p = await owned_project(project_id, user, db)
    for key, value in payload.model_dump(exclude={"client_project_id"}).items(): setattr(p, key, value.strip() if isinstance(value, str) else value)
    if not p.name: raise HTTPException(422, "项目名称不能为空")
    if p.claim_url and (urlparse(p.claim_url).scheme != "https" or not urlparse(p.claim_url).hostname):
        raise HTTPException(422, "资格查询入口必须是 HTTPS URL")
    await db.commit(); await db.refresh(p); return serialize_project(p)

@router.delete("/projects/{project_id}", status_code=204)
async def delete_project(project_id: UUID, db: Annotated[AsyncSession, Depends(get_db)], user: User = Depends(get_current_user)):
    p = await owned_project(project_id, user, db)
    await db.execute(delete(ProjectWalletAssignment).where(
        ProjectWalletAssignment.user_id == user.id,
        ProjectWalletAssignment.project_key == str(project_id),
    ))
    await db.delete(p)
    await db.commit()
    return Response(status_code=204)
