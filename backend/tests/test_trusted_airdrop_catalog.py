import asyncio
from datetime import datetime, timezone

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


def test_retired_web3_discover_source_remains_untrusted(monkeypatch):
    monkeypatch.setattr(
        catalog_sync,
        "TRUSTED_AIRDROP_PROVIDER_LABELS",
        {"approved-directory": "Approved Directory"},
    )

    assert catalog_sync._source_publication_state("web3-discover") == (
        "TRUST_LEVEL_0",
        "OBSERVATION",
    )
    assert catalog_sync._source_publication_state("approved-directory") == (
        "TRUST_LEVEL_1",
        "PUBLISHED_SAFE",
    )

def test_public_airdrop_feed_schema_hides_internal_source_metadata():
    fields = AirdropHubItem.model_fields
    assert set(fields) == {"id", "name", "chain", "description", "status", "link"}
    assert "data_source" not in fields
    assert "trust_level" not in fields
    assert "publication_status" not in fields


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
    assert [row["source_key"] for row in rows] == ["binance:x1", "binance:x2"]
    from app.services.airdrop_discovery_filter import evaluate_discovery_observation
    decisions = [evaluate_discovery_observation(row) for row in rows]
    assert [decision["status"] for decision in decisions] == ["filtered", "eligible_for_review"]


def test_trusted_catalog_destination_must_be_https_without_credentials():
    assert catalog_sync._valid_http_url("https://project.example/airdrop") == "https://project.example/airdrop"
    assert catalog_sync._valid_http_url("http://project.example/airdrop") is None
    assert catalog_sync._valid_http_url("https://user:pass@project.example/airdrop") is None
    assert catalog_sync._valid_http_url("not-a-url") is None



def test_coingecko_airdrop_category_and_coin_normalization():
    categories = catalog_sync._normalize_coingecko_categories([
        {"id": "airdrop", "name": "Airdrop"},
        {"id": "layer-1", "name": "Layer 1"},
    ])
    assert categories == [
        {"id": "airdrop", "name": "Airdrop"},
        {"id": "layer-1", "name": "Layer 1"},
    ]
    from app.services.airdrop_discovery_filter import select_coingecko_airdrop_categories
    assert select_coingecko_airdrop_categories(categories) == ["airdrop"]
    rows = catalog_sync._normalize_coingecko_coins(
        [{"id": "sample-token", "name": "Sample Token"}], "airdrop"
    )
    assert len(rows) == 1
    assert rows[0]["source_key"] == "coingecko:airdrop:sample-token"
    assert rows[0]["link"] == "https://www.coingecko.com/en/coins/sample-token"
    assert rows[0]["publication_status"] == "OBSERVATION"
    assert rows[0]["trust_level"] == "TRUST_LEVEL_0"


def test_github_raw_json_and_markdown_normalization():
    json_rows = catalog_sync._normalize_github_airdrop_list(
        [{"name": "Project JSON", "website": "https://json.example"}],
        "https://raw.githubusercontent.com/example/list/main/data.json",
    )
    markdown_rows = catalog_sync._normalize_github_airdrop_list(
        "- [Project Markdown](https://markdown.example/)",
        "https://raw.githubusercontent.com/example/list/main/README.md",
    )
    assert json_rows[0]["name"] == "Project JSON"
    assert markdown_rows[0]["name"] == "Project Markdown"
    assert all(row["source_name"] == "github-airdrop-lists" for row in json_rows + markdown_rows)
    assert all(row["publication_status"] == "OBSERVATION" for row in json_rows + markdown_rows)


def test_dexscreener_adapter_retains_pairs_for_centralized_filtering():
    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    payload = {"pairs": [
        {
            "pairCreatedAt": now_ms - 3600_000,
            "liquidity": {"usd": 25000},
            "volume": {"h24": 80000},
            "txns": {"h24": {"buys": 30, "sells": 25}},
            "baseToken": {"name": "New Token", "symbol": "NEW", "address": "0xabc"},
            "chainId": "base",
            "url": "https://dexscreener.com/base/0xabc",
        },
        {
            "pairCreatedAt": now_ms - 9 * 86400_000,
            "liquidity": {"usd": 25000},
            "volume": {"h24": 80000},
            "txns": {"h24": {"buys": 30, "sells": 25}},
            "baseToken": {"name": "Old Token", "symbol": "OLD", "address": "0xold"},
            "chainId": "base",
            "url": "https://dexscreener.com/base/0xold",
        },
    ]}
    rows = catalog_sync._normalize_dexscreener_pairs(payload, "BASE")
    assert len(rows) == 2
    from app.services.airdrop_discovery_filter import evaluate_discovery_observation
    decisions = [evaluate_discovery_observation(row) for row in rows]
    assert [decision["status"] for decision in decisions] == ["eligible_for_review", "filtered"]
    assert rows[0]["name"] == "New Token (NEW)"
    assert rows[0]["source_name"] == "dexscreener-new-pools"
    assert rows[0]["publication_status"] == "OBSERVATION"


def test_all_four_source_labels_are_available():
    assert catalog_sync.SOURCE_LABELS["coingecko-airdrop-category"] == "CoinGecko Airdrop Category"
    assert catalog_sync.SOURCE_LABELS["github-airdrop-lists"] == "GitHub Open-source Lists"
    assert catalog_sync.SOURCE_LABELS["dexscreener-new-pools"] == "DexScreener New Active Pools"
    assert catalog_sync.SOURCE_LABELS["binance-announcements"] == "Binance Announcements"
