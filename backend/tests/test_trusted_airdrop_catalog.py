import asyncio

from app.api.airdrop_hub import AirdropHubItem
from app.services import airdrop_hub_sync as catalog_sync


def test_only_explicitly_allowlisted_airdrop_directory_gets_trust_level_one(monkeypatch):
    monkeypatch.setattr(
        catalog_sync,
        "TRUSTED_AIRDROP_PROVIDER_LABELS",
        {"approved-free-airdrop-directory": "Approved Directory"},
    )

    trusted = catalog_sync._source_publication_state("approved-free-airdrop-directory")
    untrusted = catalog_sync._source_publication_state("coingecko-market-data")

    assert trusted == ("TRUST_LEVEL_1", "PUBLISHED_SAFE")
    assert untrusted == ("TRUST_LEVEL_0", "OBSERVATION")


def test_normalized_records_keep_source_and_trust_metadata(monkeypatch):
    payload = {
        "airdrops": [{
            "id": "campaign-1",
            "name": "Example Campaign",
            "website": "https://example.org/airdrop",
            "description": "Example description",
            "chain": "Base",
        }]
    }

    ordinary = catalog_sync._normalize(payload, source_name="web3-discover")
    assert len(ordinary) == 1
    assert ordinary[0]["source_name"] == "web3-discover"
    assert ordinary[0]["trust_level"] == "TRUST_LEVEL_0"
    assert ordinary[0]["publication_status"] == "OBSERVATION"

    monkeypatch.setattr(catalog_sync, "TRUSTED_AIRDROP_PROVIDER_LABELS", {"approved-directory": "Approved Directory"})
    trusted = catalog_sync._normalize(payload, source_name="approved-directory")
    assert trusted[0]["trust_level"] == "TRUST_LEVEL_1"
    assert trusted[0]["publication_status"] == "PUBLISHED_SAFE"


def test_public_project_schema_exposes_source_and_publication_provenance():
    item = AirdropHubItem(
        id="project-id",
        name="Example Campaign",
        chain="Base",
        description="Example",
        status="进行中",
        link="https://example.org/airdrop",
        data_source="Source: Approved Directory",
        trust_level="TRUST_LEVEL_1",
        publication_status="PUBLISHED_SAFE",
    )

    assert item.data_source == "Source: Approved Directory"
    assert item.trust_level == "TRUST_LEVEL_1"
    assert item.publication_status == "PUBLISHED_SAFE"


def test_binance_announcement_headlines_are_imported_as_observation_leads():
    payload = {
        "data": {"catalogs": [{
            "catalogId": 128,
            "articles": [
                {"title": "Project X Airdrop is Live", "code": "abc123"},
                {"title": "Malformed item", "code": ""},
            ],
        }]}
    }
    rows = catalog_sync._normalize_binance_announcements(payload, catalog_id=128)
    assert len(rows) == 1
    assert rows[0]["name"] == "Project X Airdrop is Live"
    assert rows[0]["link"] == "https://www.binance.com/en/support/announcement/abc123"
    assert rows[0]["source_name"] == "binance-announcements"
    assert rows[0]["trust_level"] == "TRUST_LEVEL_0"
    assert rows[0]["publication_status"] == "OBSERVATION"
    assert rows[0]["status"] == "待核实"


def test_binance_latest_activities_requires_airdrop_keyword():
    payload = {"data": {"catalogs": [{"articles": [
        {"title": "General Binance update", "code": "x1"},
        {"title": "Launchpool Season announcement", "code": "x2"},
    ]}]}}
    rows = catalog_sync._normalize_binance_announcements(payload, catalog_id=93)
    assert [row["source_key"] for row in rows] == ["binance:x2"]


def test_trusted_catalog_destination_must_be_https_without_credentials():
    assert catalog_sync._valid_http_url("https://project.example/airdrop") == "https://project.example/airdrop"
    assert catalog_sync._valid_http_url("http://project.example/airdrop") is None
    assert catalog_sync._valid_http_url("https://user:pass@project.example/airdrop") is None
    assert catalog_sync._valid_http_url("not-a-url") is None
