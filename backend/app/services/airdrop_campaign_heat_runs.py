"""Record trustworthy, successful campaign-heat observations.

Only complete, validated provider batches count. RSS intake and DEX market
enrichment are not campaign-heat observations.
"""
from datetime import datetime, timezone
import hashlib
import json
import math
from urllib.parse import urlparse
import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.models import (
    AirdropCampaign,
    AirdropCampaignSourceSnapshot,
    AirdropPipelineRun,
)


def _validated_observations(observations: list[dict]) -> list[dict]:
    normalized: list[dict] = []
    seen: set[uuid.UUID] = set()
    for item in observations:
        try:
            campaign_id = uuid.UUID(str(item["campaign_id"]))
            raw_heat = float(item["heat"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError("Each heat observation needs a campaign_id and numeric heat") from exc
        if not math.isfinite(raw_heat) or not 0 <= raw_heat <= 100:
            raise ValueError("Campaign heat must be a finite number from 0 to 100")
        if campaign_id in seen:
            raise ValueError("A campaign may appear only once in a heat batch")
        seen.add(campaign_id)
        normalized.append({"campaign_id": campaign_id, "heat": int(round(raw_heat))})
    return normalized


async def start_campaign_heat_run(
    db: AsyncSession,
    *,
    source_name: str,
    run_id: uuid.UUID,
) -> AirdropPipelineRun:
    """Persist a stable running marker before contacting the provider."""
    name = str(source_name or "").strip()
    if not name:
        raise ValueError("A heat source name is required")
    if not isinstance(run_id, uuid.UUID):
        raise ValueError("A stable pipeline run_id is required")

    existing = await db.get(AirdropPipelineRun, run_id, with_for_update=True)
    if existing is not None:
        if existing.source_name != name:
            raise ValueError("A pipeline run ID cannot be reused for another source")
        return existing

    now = datetime.now(timezone.utc)
    run = AirdropPipelineRun(
        id=run_id,
        source_name=name,
        status="running",
        items_seen=0,
        items_created=0,
        items_updated=0,
        items_rejected=0,
        started_at=now,
    )
    db.add(run)
    await db.commit()
    return run


async def fail_campaign_heat_run(
    db: AsyncSession,
    *,
    run_id: uuid.UUID,
    error_code: str,
) -> bool:
    """Mark a failed provider attempt without touching campaign heat counters."""
    run = await db.get(AirdropPipelineRun, run_id, with_for_update=True)
    if run is None:
        raise ValueError("Campaign heat run does not exist")
    if run.status == "success":
        return False
    if run.status == "failed":
        return False

    run.status = "failed"
    run.error_code = str(error_code or "provider_error")[:120]
    run.finished_at = datetime.now(timezone.utc)
    await db.commit()
    return True


async def record_successful_campaign_heat_batch(
    db: AsyncSession,
    *,
    source_name: str,
    source_url: str,
    observations: list[dict],
    run_id: uuid.UUID,
) -> dict[str, int | bool]:
    """Complete a previously started run exactly once.

    The caller starts the run before fetching, then calls this function only
    after the entire provider batch has been fetched and validated. On failure,
    call fail_campaign_heat_run instead. Replaying a successful run is a no-op.
    Unknown/missing heat is omitted, never synthesized as zero.
    """
    name = str(source_name or "").strip()
    parsed_url = urlparse(str(source_url or "").strip())
    if not name:
        raise ValueError("A heat source name is required")
    if parsed_url.scheme != "https" or not parsed_url.hostname:
        raise ValueError("A heat source URL must be HTTPS")
    if not isinstance(run_id, uuid.UUID):
        raise ValueError("A stable pipeline run_id is required for idempotency")

    rows = _validated_observations(observations)
    run = await db.get(AirdropPipelineRun, run_id, with_for_update=True)
    if run is None:
        raise ValueError("Start the campaign heat run before recording success")
    if run.source_name != name:
        raise ValueError("Pipeline run source does not match heat source")
    if run.status == "success":
        return {"replayed": True, "observed": run.items_seen, "updated": 0}
    if run.status != "running":
        raise ValueError("Only a running campaign heat run can be completed")

    now = datetime.now(timezone.utc)
    campaigns: list[tuple[AirdropCampaign, dict]] = []
    for row in rows:
        campaign = await db.get(AirdropCampaign, row["campaign_id"])
        if campaign is not None:
            campaigns.append((campaign, row))

    run.status = "success"
    run.items_seen = len(rows)
    run.items_created = 0
    run.items_updated = len(campaigns)
    run.items_rejected = len(rows) - len(campaigns)
    run.finished_at = now
    run.error_code = None

    for campaign, row in campaigns:
        heat = row["heat"]
        payload = {
            "campaign_id": str(campaign.id),
            "heat": heat,
            "pipeline_run_id": str(run_id),
        }
        snapshot_hash = hashlib.sha256(
            json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
        ).hexdigest()
        db.add(AirdropCampaignSourceSnapshot(
            campaign_id=campaign.id,
            source_name=name,
            source_url=str(source_url).strip(),
            snapshot_hash=snapshot_hash,
            normalized_payload=payload,
            captured_at=now,
        ))

        if heat >= 40:
            campaign.low_heat_success_count = 0
        elif campaign.last_heat_source_name == name:
            campaign.low_heat_success_count = int(campaign.low_heat_success_count or 0) + 1
        else:
            campaign.low_heat_success_count = 1

        campaign.temperature = heat
        campaign.last_heat_source_name = name
        campaign.last_source_success_at = now

    await db.commit()
    return {"replayed": False, "observed": len(rows), "updated": len(campaigns)}
