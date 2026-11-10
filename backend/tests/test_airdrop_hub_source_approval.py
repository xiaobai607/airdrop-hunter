import asyncio

from app.services import airdrop_hub_sync


def test_web3_discover_api_import_requires_explicit_approval(monkeypatch):
    monkeypatch.delenv("AIRDROP_HUB_SOURCE_APPROVED", raising=False)

    def forbidden_client(*args, **kwargs):
        raise AssertionError("unapproved provider must not be contacted")

    monkeypatch.setattr(airdrop_hub_sync.httpx, "AsyncClient", forbidden_client)

    imported = asyncio.run(airdrop_hub_sync.sync_airdrop_hub(None))  # type: ignore[arg-type]

    assert imported == 0
    status = airdrop_hub_sync.get_sync_status()
    assert status["imported_count"] == 0
    assert status["error"] == "source_not_approved"
