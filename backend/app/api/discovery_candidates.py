from datetime import datetime, timezone
from uuid import UUID
import hashlib
import re

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict, ValidationError, computed_field
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import AirdropDiscoveryCandidate, AirdropDiscoveryObservation, AirdropDiscoverySourceStatus, CommunityProjectCatalogEntry
from app.api.community_catalog import (
    normalize_project_url,
    normalized_identity,
    ProjectSubmission,
    require_catalog_admin,
)

router = APIRouter(prefix="/api/airdrops/discovery", tags=["airdrop-discovery"])


class DiscoveryCandidateResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    source_name: str
    source_url: str
    title: str
    item_url: str
    summary: str
    published_at: datetime | None
    first_seen_at: datetime
    last_seen_at: datetime


class CandidateReviewResponse(DiscoveryCandidateResponse):
    review_status: str
    reviewed_at: datetime | None
    reviewed_catalog_id: str | None
    filter_status: str
    filter_reasons: list[str]
    filter_metadata: dict
    project_identity_key: str | None
    campaign_identity_key: str | None
    identity_status: str
    identity_evidence: dict
    matched_source_key: str | None

    @computed_field
    @property
    def identity_group_type(self) -> str:
        return str((self.identity_evidence or {}).get("identity_group_type") or "unresolved")

    @computed_field
    @property
    def identity_group_key(self) -> str | None:
        value = (self.identity_evidence or {}).get("identity_group_key")
        return str(value) if value else None


class DiscoveryObservationResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    candidate_id: UUID
    source_name: str
    source_record_key: str
    source_url: str
    item_url: str
    observed_at: datetime
    payload_sha256: str
    raw_payload: dict
    normalized_payload: dict
    filter_decision: dict
    identity_decision: dict
    normalizer_version: str
    filter_version: str


class DiscoverySourceStatusResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    source_key: str
    source_name: str
    source_url: str
    last_attempt_at: datetime
    last_success_at: datetime | None
    last_status: str
    last_item_count: int
    last_error: str | None
    updated_at: datetime


@router.get(
    "/sources/status",
    response_model=list[DiscoverySourceStatusResponse],
    dependencies=[Depends(require_catalog_admin)],
)
async def list_discovery_source_status(
    limit: int = Query(default=100, ge=1, le=200),
    db: AsyncSession = Depends(get_db),
):
    statement = (
        select(AirdropDiscoverySourceStatus)
        .order_by(AirdropDiscoverySourceStatus.last_attempt_at.desc(), AirdropDiscoverySourceStatus.id.desc())
        .limit(limit)
    )
    return list(await db.scalars(statement))


@router.get("/candidates", response_model=list[DiscoveryCandidateResponse])
async def list_discovery_candidates(
    q: str | None = Query(default=None, max_length=160),
    limit: int = Query(default=100, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    statement = select(AirdropDiscoveryCandidate).where(
        AirdropDiscoveryCandidate.review_status == "pending"
    )
    if q and q.strip():
        statement = statement.where(AirdropDiscoveryCandidate.title.ilike(f"%{q.strip()}%"))
    statement = statement.order_by(
        AirdropDiscoveryCandidate.published_at.desc().nullslast(),
        AirdropDiscoveryCandidate.last_seen_at.desc(),
        AirdropDiscoveryCandidate.id.asc(),
    ).offset(offset).limit(limit)
    rows = await db.scalars(statement)
    return list(rows)


@router.get(
    "/candidates/review-queue",
    response_model=list[CandidateReviewResponse],
    dependencies=[Depends(require_catalog_admin)],
)
async def list_candidate_review_queue(
    status: str = Query(default="pending", pattern="^(pending|approved|rejected|all)$"),
    limit: int = Query(default=100, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    statement = select(AirdropDiscoveryCandidate)
    if status != "all":
        statement = statement.where(AirdropDiscoveryCandidate.review_status == status)
    statement = statement.order_by(
        AirdropDiscoveryCandidate.first_seen_at.asc(),
        AirdropDiscoveryCandidate.id.asc(),
    ).offset(offset).limit(limit)
    return list(await db.scalars(statement))


@router.get(
    "/candidates/{candidate_id}/observations",
    dependencies=[Depends(require_catalog_admin)],
)
async def list_discovery_candidate_observations(
    candidate_id: UUID,
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    """Return append-only, per-refresh source evidence for one review candidate."""
    candidate = await db.get(AirdropDiscoveryCandidate, candidate_id)
    if candidate is None:
        raise HTTPException(status_code=404, detail="待审核项目不存在")
    base = select(AirdropDiscoveryObservation).where(
        AirdropDiscoveryObservation.candidate_id == candidate_id
    )
    total = await db.scalar(select(func.count()).select_from(base.subquery()))
    statement = (
        base.order_by(
            AirdropDiscoveryObservation.observed_at.desc(),
            AirdropDiscoveryObservation.id.desc(),
        )
        .offset(offset)
        .limit(limit)
    )
    observations = list(await db.scalars(statement))
    return {
        "candidate_id": str(candidate_id),
        "total_count": int(total or 0),
        "offset": offset,
        "limit": limit,
        "has_more": offset + len(observations) < int(total or 0),
        "next_offset": offset + len(observations) if offset + len(observations) < int(total or 0) else None,
        "observations": [
            DiscoveryObservationResponse.model_validate(item).model_dump(mode="json")
            for item in observations
        ],
    }


@router.get(
    "/identity-groups/{group_type}/{group_key}",
    dependencies=[Depends(require_catalog_admin)],
)
async def get_discovery_identity_group(
    group_type: str,
    group_key: str,
    status: str = Query(default="all", pattern="^(pending|approved|rejected|all)$"),
    limit: int = Query(default=100, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    """Return paged, complete membership for one strong identity group.

    Campaign groups are exact-activity groups. Project groups are parent
    relationships and deliberately include separate campaigns under a project.
    """
    if group_type not in {"campaign", "project"} or not re.fullmatch(r"[a-f0-9]{64}", group_key):
        raise HTTPException(status_code=422, detail="身份分组类型或标识无效")

    identity_column = (
        AirdropDiscoveryCandidate.campaign_identity_key
        if group_type == "campaign"
        else AirdropDiscoveryCandidate.project_identity_key
    )
    base = select(AirdropDiscoveryCandidate).where(
        identity_column == group_key,
        AirdropDiscoveryCandidate.identity_status != "identity_conflict_needs_review",
    )
    # A campaign member's evidence points to its exact campaign group. A
    # project parent group intentionally spans multiple campaigns, so it must
    # be derived from project_identity_key rather than the per-row evidence
    # group marker. Keep the stricter evidence check for exact campaign groups.
    if group_type == "campaign":
        base = base.where(
            AirdropDiscoveryCandidate.identity_evidence["identity_group_type"].as_string() == group_type,
            AirdropDiscoveryCandidate.identity_evidence["identity_group_key"].as_string() == group_key,
        )
    if status != "all":
        base = base.where(AirdropDiscoveryCandidate.review_status == status)

    total = await db.scalar(select(func.count()).select_from(base.subquery()))
    statement = (
        base.order_by(
            AirdropDiscoveryCandidate.first_seen_at.asc(),
            AirdropDiscoveryCandidate.id.asc(),
        )
        .offset(offset)
        .limit(limit)
    )
    members = list(await db.scalars(statement))
    return {
        "identity_group_type": group_type,
        "identity_group_key": group_key,
        "membership_scope": "same_campaign" if group_type == "campaign" else "same_project_parent",
        "total_count": int(total or 0),
        "offset": offset,
        "limit": limit,
        "has_more": offset + len(members) < int(total or 0),
        "next_offset": offset + len(members) if offset + len(members) < int(total or 0) else None,
        "members": [CandidateReviewResponse.model_validate(item).model_dump(mode="json") for item in members],
    }


@router.post(
    "/candidates/{candidate_id}/approve",
    dependencies=[Depends(require_catalog_admin)],
)
async def approve_discovery_candidate(
    candidate_id: UUID,
    db: AsyncSession = Depends(get_db),
):
    candidate = await db.get(AirdropDiscoveryCandidate, candidate_id)
    if candidate is None:
        raise HTTPException(status_code=404, detail="待审核项目不存在")
    if candidate.review_status != "pending":
        raise HTTPException(status_code=409, detail="该候选项目已经审核，不能重复操作")
    if candidate.filter_status == "filtered":
        raise HTTPException(status_code=409, detail="该候选已被中央筛选器过滤，不能直接加入社区目录")

    try:
        submission = ProjectSubmission(name=candidate.title, url=candidate.item_url)
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail="项目名称或链接未通过安全校验") from exc

    identity = normalized_identity(submission.name)
    canonical_url = submission.url
    projects = list(await db.scalars(select(CommunityProjectCatalogEntry)))
    for project in projects:
        same_url = bool(project.website_url) and normalize_project_url(project.website_url) == canonical_url
        if same_url or normalized_identity(project.name) == identity:
            candidate.review_status = "approved"
            candidate.reviewed_catalog_id = project.project_official_id
            candidate.reviewed_at = datetime.now(timezone.utc)
            await db.commit()
            return {
                "status": "duplicate",
                "message": "目录中已存在相同项目，已关联现有项目",
                "project_official_id": project.project_official_id,
            }

    slug = re.sub(r"[^a-z0-9]+", "-", submission.name.casefold()).strip("-")[:80] or "project"
    suffix = hashlib.sha256(f"{candidate.id}:{identity}:{canonical_url}".encode()).hexdigest()[:12]
    project_id = f"{slug}-{suffix}"[:120]
    entry = CommunityProjectCatalogEntry(
        project_official_id=project_id,
        name=submission.name,
        chain="Unknown",
        logo="",
        description=(candidate.summary or "")[:5000],
        tags=[],
        website_url=canonical_url,
        is_active=True,
        sort_order=100000,
    )
    db.add(entry)
    candidate.review_status = "approved"
    candidate.reviewed_catalog_id = project_id
    candidate.reviewed_at = datetime.now(timezone.utc)
    await db.commit()
    return {
        "status": "approved",
        "message": "审核通过，项目已加入社区目录",
        "project_official_id": project_id,
    }


@router.post(
    "/candidates/{candidate_id}/reject",
    dependencies=[Depends(require_catalog_admin)],
)
async def reject_discovery_candidate(
    candidate_id: UUID,
    db: AsyncSession = Depends(get_db),
):
    candidate = await db.get(AirdropDiscoveryCandidate, candidate_id)
    if candidate is None:
        raise HTTPException(status_code=404, detail="待审核项目不存在")
    if candidate.review_status != "pending":
        raise HTTPException(status_code=409, detail="该候选项目已经审核，不能重复操作")
    candidate.review_status = "rejected"
    candidate.reviewed_at = datetime.now(timezone.utc)
    await db.commit()
    return {"status": "rejected", "message": "候选项目已拒绝"}
