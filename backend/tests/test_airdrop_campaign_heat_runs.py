import asyncio
from datetime import datetime, timezone
from uuid import uuid4

from app.models import (
    AirdropCampaign,
    AirdropCampaignSourceSnapshot,
    AirdropPipelineRun,
)
from app.services.airdrop_campaign_heat_runs import (
    record_successful_campaign_heat_batch,
)


def _campaign(**overrides):
    values = dict(
        id=uuid4(),
        source_key=f"heat-test:{uuid4()}",
        source_name="test",
        source_url="https://source.example/feed",
        name="Heat test",
        description="",
        campaign_type="unknown",
        campaign_status="ongoing",
        low_heat_success_count=0,
    )
    values.update(overrides)
    return AirdropCampaign(**values)


class _FakeDb:
    def __init__(self, campaign):
        self.rows = {campaign.id: campaign}
        self.runs = {}
        self.added = []
        self.commits = 0

    async def get(self, model, row_id):
        if model is AirdropPipelineRun:
            return self.runs.get(row_id)
        if model is AirdropCampaign:
            return self.rows.get(row_id)
        return None

    def add(self, row):
        self.added.append(row)
        if isinstance(row, AirdropPipelineRun):
            self.runs[row.id] = row

    async def commit(self):
        self.commits += 1


def _record(db, campaign, heat, run_id=None):
    return asyncio.run(record_successful_campaign_heat_batch(
        db,
        source_name="approved-heat-provider",
        source_url="https://provider.example/api/heat",
        observations=[{"campaign_id": str(campaign.id), "heat": heat}],
        run_id=run_id,
    ))


def test_only_three_distinct_successful_low_heat_runs_archive_counter():
    campaign = _campaign()
    db = _FakeDb(campaign)

    for expected in (1, 2, 3):
        result = _record(db, campaign, 12 + expected)
        assert result["replayed"] is False
        assert campaign.low_heat_success_count == expected

    snapshots = [row for row in db.added if isinstance(row, AirdropCampaignSourceSnapshot)]
    runs = [row for row in db.added if isinstance(row, AirdropPipelineRun)]
    assert len(snapshots) == 3
    assert len(runs) == 3
    assert all(row.status == "success" for row in runs)


def test_heat_recovery_resets_consecutive_low_heat_counter():
    campaign = _campaign(low_heat_success_count=2)
    db = _FakeDb(campaign)

    _record(db, campaign, 40)
    assert campaign.low_heat_success_count == 0

    _record(db, campaign, 39)
    assert campaign.low_heat_success_count == 1


def test_replaying_same_pipeline_run_does_not_increment_twice():
    campaign = _campaign()
    db = _FakeDb(campaign)
    run_id = uuid4()

    first = _record(db, campaign, 10, run_id)
    second = _record(db, campaign, 10, run_id)

    assert first["replayed"] is False
    assert second["replayed"] is True
    assert campaign.low_heat_success_count == 1
    assert len([row for row in db.added if isinstance(row, AirdropCampaignSourceSnapshot)]) == 1


def test_invalid_heat_batch_fails_before_writing_or_counting():
    campaign = _campaign()
    db = _FakeDb(campaign)

    async def run():
        return await record_successful_campaign_heat_batch(
            db,
            source_name="approved-heat-provider",
            source_url="https://provider.example/api/heat",
            observations=[{"campaign_id": str(campaign.id), "heat": 101}],
        )

    try:
        asyncio.run(run())
    except ValueError as error:
        assert "0 to 100" in str(error)
    else:
        raise AssertionError("out-of-range heat must be rejected")

    assert campaign.low_heat_success_count == 0
    assert db.added == []
    assert db.commits == 0


def test_missing_campaign_heat_is_not_synthesized_as_zero():
    campaign = _campaign()
    db = _FakeDb(campaign)

    async def run():
        return await record_successful_campaign_heat_batch(
            db,
            source_name="approved-heat-provider",
            source_url="https://provider.example/api/heat",
            observations=[],
        )

    result = asyncio.run(run())
    assert result == {"replayed": False, "observed": 0, "updated": 0}
    assert campaign.low_heat_success_count == 0
