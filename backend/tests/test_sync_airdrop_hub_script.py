import asyncio

import pytest

from scripts import sync_airdrop_hub as job


class _FakeSessionContext:
    async def __aenter__(self):
        return object()

    async def __aexit__(self, exc_type, exc, traceback):
        return False


def _patch_successful_stages(monkeypatch):
    monkeypatch.setattr(job, "AsyncSessionLocal", _FakeSessionContext)
    monkeypatch.setattr(job, "sync_airdrop_hub", _async_value(3))
    monkeypatch.setattr(job, "refresh_campaign_market_data", _async_value({
        "campaigns_considered": 2, "dex_updated": 1, "gecko_updated": 1,
    }))
    monkeypatch.setattr(job, "recalculate_campaign_scores", _async_value({
        "evaluated": 2, "updated": 1, "publishable": 1, "held": 1,
    }))


def _async_value(value):
    async def run(_db):
        return value
    return run


def test_sync_job_exits_successfully_when_all_stages_succeed(monkeypatch):
    _patch_successful_stages(monkeypatch)
    asyncio.run(job.main())


def test_sync_job_exits_nonzero_when_all_sources_fail_even_if_later_stages_succeed(monkeypatch):
    _patch_successful_stages(monkeypatch)

    async def fail_discovery(_db):
        raise RuntimeError("all providers failed")

    monkeypatch.setattr(job, "sync_airdrop_hub", fail_discovery)
    with pytest.raises(SystemExit) as error:
        asyncio.run(job.main())

    assert error.value.code == 1


def test_sync_job_exits_nonzero_when_market_enrichment_fails(monkeypatch):
    _patch_successful_stages(monkeypatch)

    async def fail_market(_db):
        raise RuntimeError("market provider failed")

    monkeypatch.setattr(job, "refresh_campaign_market_data", fail_market)
    with pytest.raises(SystemExit) as error:
        asyncio.run(job.main())

    assert error.value.code == 1
