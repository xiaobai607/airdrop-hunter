from app.services import airdrop_hub_sync


def test_unapproved_legacy_directory_remains_observation_only(monkeypatch):
    monkeypatch.delenv("AIRDROP_HUB_SOURCE_APPROVED", raising=False)
    monkeypatch.setattr(airdrop_hub_sync, "TRUSTED_AIRDROP_PROVIDER_LABELS", {})
    assert airdrop_hub_sync._source_publication_state("web3-discover") == (
        "TRUST_LEVEL_0",
        "OBSERVATION",
    )


def test_binance_is_not_promoted_to_trusted_catalog_provider():
    assert airdrop_hub_sync._source_publication_state("binance-announcements") == (
        "TRUST_LEVEL_0",
        "OBSERVATION",
    )
