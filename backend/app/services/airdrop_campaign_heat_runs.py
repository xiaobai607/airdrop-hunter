"""Record trustworthy, successful campaign-heat observations.

Call only after one complete provider batch has been fetched and validated.
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
            raw_heat = float(item["heat"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError("Each heat observation needs a campaign_id and numeric heat") from exc
        if not math.isfinite(raw_heat) or not 0 <= raw_heat <= 100:
            raise ValueError("Campaign heat must be a finite number from 0 to 100")
        if campaign_id in seen:
            raise ValueError("A campaign may appear only once in a heat batch")
        seen.add(campaign_id)
        # The campaign's canonical temperature field is integer-valued.
        normalized.append({"campaign_id": campaign_id, "heat": int(round(raw_heat))})
    return normalized


async def record_successful_campaign_heat_batch(
    db: AsyncSession,
    *,
    source_name: str,
    source_url: str,
    observations: list[dict],
    run_id: uuid.UUID,
) -> dict[str, int | bool]:
    """Persist one completed heat batch exactly once.

    The caller must create/persist a pipeline run ID before fetching and reuse
    that same ID on retries. Provider failures must never call this success
    recorder; they must leave the campaign counters unchanged and mark their
    pipeline run failed in the caller's failure path.

    Unknown/missing campaign heat is omitted, never converted to zero. Each
    explicit successful observation updates the campaign temperature. Only
    consecutive low-heat successes from the same source increment the counter;
    a source change starts a new streak, and heat >= 40 resets it.
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
    existing_run = await db.get(AirdropPipelineRun, run_id)
    if existing_run is not None:
        return {
            "replayed": True,
            "observed": existing_run.items_seen,
            "updated": 0,
        }

    now = datetime.now(timezone.utc)
    campaigns: list[tuple[AirdropCampaign, dict]] = []
    for row in rows:
        campaign = await db.get(AirdropCampaign, row["campaign_id"])
        if campaign is not None:
            campaigns.append((campaign, row))

    db.add(AirdropPipelineRun(
        id=run_id,
        source_name=name,
        status="success",
        items_seen=len(rows),
        items_created=0,
        items_updated=len(campaigns),
        items_rejected=len(rows) - len(campaigns),
        started_at=now,
        finished_at=now,
    ))

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
