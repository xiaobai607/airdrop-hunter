import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scripts import smoke_discovery_sources as smoke


class FakeAsyncClient:
    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False


def test_live_smoke_reports_success_without_database_access(monkeypatch, capsys):
    async def binance(_client):
        return [{"name": "Official campaign"}]

    async def dexscreener(_client):
        raise RuntimeError("provider unavailable")

    monkeypatch.setattr(smoke.httpx, "AsyncClient", lambda **_kwargs: FakeAsyncClient())
    monkeypatch.setattr(smoke, "_fetch_binance_observations", binance)
    monkeypatch.setattr(smoke, "_fetch_dexscreener_observations", dexscreener)
    monkeypatch.delenv("COINGECKO_DEMO_API_KEY", raising=False)
    monkeypatch.delenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", raising=False)

    result = asyncio.run(smoke.main())

    output = capsys.readouterr().out
    assert result == 0
    assert "binance-announcements: OK count=1" in output
    assert "dexscreener-market-leads: FAILED error_type=RuntimeError" in output
    assert "coingecko-airdrop-category: SKIPPED" in output
    assert "github-raw-approved-lists: SKIPPED" in output


def test_live_smoke_fails_when_no_configured_source_returns_records(monkeypatch, capsys):
    async def empty(_client):
        return []

    monkeypatch.setattr(smoke.httpx, "AsyncClient", lambda **_kwargs: FakeAsyncClient())
    monkeypatch.setattr(smoke, "_fetch_binance_observations", empty)
    monkeypatch.setattr(smoke, "_fetch_binance_public_market_data", empty)
    monkeypatch.setattr(smoke, "_fetch_dexscreener_observations", empty)
    monkeypatch.delenv("COINGECKO_DEMO_API_KEY", raising=False)
    monkeypatch.delenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", raising=False)

    result = asyncio.run(smoke.main())

    output = capsys.readouterr().out
    assert result == 1
    assert "SUMMARY: successful_sources=0 attempted_sources=3" in output
