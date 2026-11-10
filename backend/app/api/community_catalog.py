import hashlib
import ipaddress
import os
import re
import secrets
from typing import Annotated
from datetime import datetime
from urllib.parse import parse_qsl, urlencode, urlparse, urlunparse

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import AirdropDiscoveryCandidate, CommunityProjectCatalogEntry
from app.routers.auth import limiter

router = APIRouter(prefix="/api/community/catalog", tags=["community-catalog"])
PROJECT_ID = re.compile(r"^[a-z0-9][a-z0-9_-]{0,119}$")
TRACKING_QUERY_KEYS = {"fbclid", "gclid", "ref", "source"}
SHORTENER_HOSTS = {"bit.ly", "t.co", "tinyurl.com", "goo.gl", "ow.ly", "is.gd", "buff.ly", "cutt.ly"}


def normalize_project_url(value: str) -> str:
    parsed = urlparse(value.strip())
    hostname = (parsed.hostname or "").encode("idna").decode("ascii").lower().rstrip(".")
    port = parsed.port
    netloc = hostname if port in (None, 443) else f"{hostname}:{port}"
    path = re.sub(r"/{2,}", "/", parsed.path or "/").rstrip("/") or "/"
    query = urlencode(sorted(
        (key, item) for key, item in parse_qsl(parsed.query, keep_blank_values=True)
        if key.lower() not in TRACKING_QUERY_KEYS and not key.lower().startswith("utm_")
    ))
    return urlunparse(("https", netloc, path, "", query, ""))


def normalized_identity(value: str) -> str:
    return re.sub(r"[\W_]+", "", value.casefold())


def submission_review_reason(canonical_url: str) -> str | None:
    parsed = urlparse(canonical_url)
    hostname = (parsed.hostname or "").lower()
    if any(hostname == host or hostname.endswith("." + host) for host in SHORTENER_HOSTS):
        return "链接属于跳转短链接"
    if any(label.startswith("xn--") for label in hostname.split(".")):
        return "域名使用国际化编码，需要人工核验是否存在仿冒"
    if parsed.port not in (None, 443):
        return "链接使用非标准 HTTPS 端口"
    return None


class CatalogWrite(BaseModel):
    project_official_id: str = Field(min_length=1, max_length=120)
    name: str = Field(min_length=1, max_length=180)
    chain: str = Field(default="Unknown", max_length=120)
    logo: str = Field(default="", max_length=12)
    description: str = Field(default="", max_length=5000)
    tags: list[str] = Field(default_factory=list, max_length=20)
    website_url: str = Field(default="", max_length=1000)
    is_active: bool = False
    sort_order: int = Field(default=100, ge=0, le=100000)

    @field_validator("project_official_id")
    @classmethod
    def validate_id(cls, value: str) -> str:
        value = value.strip().lower()
        if not PROJECT_ID.fullmatch(value):
            raise ValueError("项目标识只能包含小写字母、数字、下划线和连字符")
        return value

    @field_validator("name", "chain", "description", "logo", mode="before")
    @classmethod
    def trim_text(cls, value):
        return value.strip() if isinstance(value, str) else value

    @field_validator("name")
    @classmethod
    def require_name(cls, value: str) -> str:
        if not value:
            raise ValueError("项目名称不能为空")
        return value

    @field_validator("tags")
    @classmethod
    def normalize_tags(cls, value: list[str]) -> list[str]:
        tags = list(dict.fromkeys(tag.strip() for tag in value if tag.strip()))
        if any(len(tag) > 32 for tag in tags):
            raise ValueError("单个标签不能超过32个字符")
        return tags

    @field_validator("website_url")
    @classmethod
    def https_only(cls, value: str) -> str:
        value = value.strip()
        if value:
            parsed = urlparse(value)
            if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
                raise ValueError("项目链接必须是有效的 HTTPS URL")
        return value


class ProjectSubmission(BaseModel):
    name: str = Field(min_length=1, max_length=180)
    url: str = Field(min_length=1, max_length=1000)
    logo: str = Field(default="", max_length=12)

    @field_validator("name", "logo", mode="before")
    @classmethod
    def trim_submission_text(cls, value):
        return value.strip() if isinstance(value, str) else value

    @field_validator("name")
    @classmethod
    def require_submission_name(cls, value: str) -> str:
        if not value:
            raise ValueError("项目名称不能为空")
        return value

    @field_validator("url")
    @classmethod
    def validate_submission_url(cls, value: str) -> str:
        value = value.strip()
        parsed = urlparse(value)
        if (
            parsed.scheme.lower() != "https"
            or not parsed.hostname
            or parsed.username is not None
            or parsed.password is not None
            or "\\" in value
            or any(ord(character) < 32 or ord(character) == 127 for character in value)
        ):
            raise ValueError("项目链接必须是有效的 HTTPS URL，不能包含账号、密码或控制字符")
        try:
            hostname = parsed.hostname.encode("idna").decode("ascii").lower().rstrip(".")
            parsed.port
        except (UnicodeError, ValueError) as exc:
            raise ValueError("项目链接域名或端口格式无效") from exc
        try:
            ipaddress.ip_address(hostname)
        except ValueError:
            pass
        else:
            raise ValueError("项目链接必须使用域名，不能直接填写 IP 地址")
        labels = hostname.split(".")
        if (
            len(hostname) > 253
            or len(labels) < 2
            or hostname == "localhost"
            or hostname.endswith((".local", ".internal", ".localhost"))
            or any(
                not label
                or len(label) > 63
                or not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", label)
                for label in labels
            )
            or not re.search(r"[a-z]", labels[-1])
        ):
            raise ValueError("项目链接域名无效")
        return normalize_project_url(value)


class CatalogResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    project_official_id: str
    name: str
    chain: str
    logo: str
    description: str
    tags: list[str]
    website_url: str
    created_at: datetime


def require_catalog_admin(token: Annotated[str | None, Header(alias="X-Catalog-Admin-Token")] = None) -> None:
    expected = os.getenv("COMMUNITY_CATALOG_ADMIN_TOKEN", "")
    if not expected:
        raise HTTPException(status_code=503, detail="社区目录管理尚未配置")
    if token is None or not secrets.compare_digest(token, expected):
        raise HTTPException(status_code=403, detail="无权管理社区目录")


@router.get("", response_model=list[CatalogResponse])
async def list_community_catalog(db: Annotated[AsyncSession, Depends(get_db)]):
    rows = await db.scalars(
        select(CommunityProjectCatalogEntry)
        .where(CommunityProjectCatalogEntry.is_active.is_(True))
        .order_by(CommunityProjectCatalogEntry.sort_order.asc(), CommunityProjectCatalogEntry.name.asc())
    )
    return list(rows)


@router.post("/submit")
@limiter.limit("10/hour")
@limiter.limit("10/minute")
async def submit_community_project(
    request: Request,
    payload: ProjectSubmission,
    db: Annotated[AsyncSession, Depends(get_db)],
):
    canonical_url = payload.url
    identity = normalized_identity(payload.name)
    projects = list(await db.scalars(select(CommunityProjectCatalogEntry)))
    for project in projects:
        if (
            normalize_project_url(project.website_url) == canonical_url
            if project.website_url else False
        ) or normalized_identity(project.name) == identity:
            return {
                "status": "duplicate",
                "message": "该项目已存在，已为你匹配现有项目",
                "existing_project": CatalogResponse.model_validate(project).model_dump(),
            }

    candidates = list(await db.scalars(select(AirdropDiscoveryCandidate)))
    for candidate in candidates:
        if normalize_project_url(candidate.item_url) == canonical_url or normalized_identity(candidate.title) == identity:
            return {
                "status": "pending_review",
                "message": "该项目已在待核验列表中，无需重复提交",
                "existing_project": None,
            }

    review_reason = submission_review_reason(canonical_url)
    if review_reason:
        source_key = hashlib.sha256(f"user-submission:{canonical_url}".encode()).hexdigest()
        db.add(AirdropDiscoveryCandidate(
            source_key=source_key,
            source_name="user-submission-review",
            source_url=canonical_url,
            title=payload.name,
            item_url=canonical_url,
            summary=f"用户提交的链接需要人工核验：{review_reason}；未抓取目标页面。",
        ))
        await db.commit()
        return {
            "status": "pending_review",
            "message": f"{review_reason}，已放入待核验列表，不会直接发布",
            "existing_project": None,
        }

    name_slug = re.sub(r"[^a-z0-9]+", "-", payload.name.casefold()).strip("-")[:80]
    if not name_slug:
        name_slug = "project"
    suffix = hashlib.sha256(f"{identity}:{canonical_url}".encode()).hexdigest()[:12]
    project_id = f"{name_slug}-{suffix}"[:120]
    entry = CommunityProjectCatalogEntry(
        project_official_id=project_id,
        name=payload.name,
        chain="Unknown",
        logo=payload.logo,
        description="",
        tags=[],
        website_url=canonical_url,
        is_active=True,
        sort_order=100000,
    )
    db.add(entry)
    await db.commit()
    await db.refresh(entry)
    return {
        "status": "published",
        "message": "提交成功，项目已加入社区目录",
        "existing_project": CatalogResponse.model_validate(entry).model_dump(),
    }


@router.post("", response_model=CatalogResponse, status_code=status.HTTP_201_CREATED,
             dependencies=[Depends(require_catalog_admin)])
async def create_catalog_entry(payload: CatalogWrite, db: Annotated[AsyncSession, Depends(get_db)]):
    existing = await db.get(CommunityProjectCatalogEntry, payload.project_official_id)
    if existing is not None:
        raise HTTPException(status_code=409, detail="项目标识已存在")
    entry = CommunityProjectCatalogEntry(**payload.model_dump())
    db.add(entry)
    await db.commit()
    await db.refresh(entry)
    return entry


@router.put("/{project_official_id}", response_model=CatalogResponse,
            dependencies=[Depends(require_catalog_admin)])
async def update_catalog_entry(project_official_id: str, payload: CatalogWrite,
                               db: Annotated[AsyncSession, Depends(get_db)]):
    if project_official_id != payload.project_official_id:
        raise HTTPException(status_code=409, detail="路径项目标识与提交内容不一致")
    entry = await db.get(CommunityProjectCatalogEntry, project_official_id)
    if entry is None:
        raise HTTPException(status_code=404, detail="社区项目不存在")
    for key, value in payload.model_dump().items():
        setattr(entry, key, value)
    await db.commit()
    await db.refresh(entry)
    return entry


@router.delete("/{project_official_id}", status_code=status.HTTP_204_NO_CONTENT,
               dependencies=[Depends(require_catalog_admin)])
async def deactivate_catalog_entry(project_official_id: str, db: Annotated[AsyncSession, Depends(get_db)]):
    entry = await db.get(CommunityProjectCatalogEntry, project_official_id)
    if entry is None:
        raise HTTPException(status_code=404, detail="社区项目不存在")
    entry.is_active = False
    await db.commit()
