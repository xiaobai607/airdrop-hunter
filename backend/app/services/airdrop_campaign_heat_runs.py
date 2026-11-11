"""Record trustworthy, successful campaign-heat observations.

Call this only after one complete provider batch has been fetched and validated.
RSS intake and DEX market enrichment are not campaign-heat observations.
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
            heat = float(item["heat"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError("Each heat observation needs a campaign_id and numeric heat") from exc
        if not math.isfinite(heat) or not 0 <= heat <= 100:
            raise ValueError("Campaign heat must be a finite number from 0 to 100")
        if campaign_id in seen:
            raise ValueError("A campaign may appear only once in a heat batch")
        seen.add(campaign_id)
        normalized.append({"campaign_id": campaign_id, "heat": round(heat, 2)})
    return normalized


async def record_successful_campaign_heat_batch(
    db: AsyncSession,
    *,
    source_name: str,
    source_url: str,
    observations: list[dict],
    run_id: uuid.UUID | None = None,
) -> dict[str, int | bool]:
    """Persist one completed heat batch; reuse run_id when retrying the same run.

    A provider failure must never call this function. Unknown/missing campaign
    heat is omitted, not interpreted as zero. Only explicit observations update
    counters. A heat value below 40 increments the consecutive-success counter;
    a value of 40 or above resets it. The scoring engine performs archival only
    after the counter reaches three.
    """
    name = str(source_name or "").strip()
    parsed_url = urlparse(str(source_url or "").strip())
    if not name:
        raise ValueError("A heat source name is required")
    if parsed_url.scheme != "https" or not parsed_url.hostname:
        raise ValueError("A heat source URL must be HTTPS")

    rows = _validated_observations(observations)
    effective_run_id = run_id or uuid.uuid4()
    existing_run = await db.get(AirdropPipelineRun, effective_run_id)
    if existing_run is not None:
        return {
            "replayed": True,
            "observed": existing_run.items_seen,
            "updated": 0,
        }

    now = datetime.now(timezone.utc)
    run = AirdropPipelineRun(
        id=effective_run_id,
        source_name=name,
        status="success",
        items_seen=len(rows),
        items_created=0,
        items_updated=len(rows),
        items_rejected=0,
        started_at=now,
        finished_at=now,
    )
    db.add(run)

    updated = 0
    for row in rows:
        campaign = await db.get(AirdropCampaign, row["campaign_id"])
        if campaign is None:
            continue

        payload = {
            "campaign_id": str(row["campaign_id"]),
            "heat": row["heat"],
            "pipeline_run_id": str(effective_run_id),
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
        campaign.low_heat_success_count = (
            int(campaign.low_heat_success_count or 0) + 1
            if row["heat"] < 40
            else 0
        )
        campaign.last_source_success_at = now
        updated += 1

    await db.commit()
    return {"replayed": False, "observed": len(rows), "updated": updated}
