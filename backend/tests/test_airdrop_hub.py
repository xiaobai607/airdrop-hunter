from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app
from app.models import AirdropProject
from app.services.airdrop_hub_sync import (
    _configured_github_raw_urls,
    _normalize_binance_announcements,
    _normalize_binance_article_detail,
    _normalize_github_airdrop_list,
)

def test_binance_article_detail_preserves_official_html_and_metadata():
    detail = _normalize_binance_article_detail(
        {"data": {
            "title": "Official Airdrop Terms",
            "body": "<h2>Reward</h2><p>Stake BNB to earn tokens.</p>",
            "releaseDate": 1762800000000,
            "catalogName": "Crypto Airdrop",
        }},
        fallback_title="Fallback title",
        fallback_url="https://www.binance.com/en/support/announcement/example",
    )
    assert detail["title"] == "Official Airdrop Terms"
    assert detail["content_html"] == "<h2>Reward</h2><p>Stake BNB to earn tokens.</p>"
    assert detail["release_date"] == 1762800000000
    assert detail["catalog_name"] == "Crypto Airdrop"
    assert detail["original_url"].endswith("/example")

def test_airdrop_hub_chain_filter_and_sync(monkeypatch):
    from app.services import airdrop_hub_sync
    monkeypatch.setattr(airdrop_hub_sync, "TRUSTED_AIRDROP_PROVIDER_LABELS", {"test": "Test Directory"})
    async def fake_sync(db):
        db.add(AirdropProject(
            source_key=f"test-{uuid4().hex}:solana", name="Hub Solana Test",
            chain="Solana", description="Inserted by API test", status="热门",
            link="https://solana.example/", source_name="test",
            trust_level="TRUST_LEVEL_1", publication_status="PUBLISHED_SAFE"
        ))
        db.add(AirdropProject(
            source_key=f"test-{uuid4().hex}:ethereum", name="Hub Ethereum Test",
            chain="Ethereum", description="Inserted by API test", status="待发币",
            link="https://ethereum.example/", source_name="test",
            trust_level="TRUST_LEVEL_1", publication_status="PUBLISHED_SAFE"
        ))
        db.add(AirdropProject(
            source_key="binance:test123", name="Binance Observation Test",
            chain="Unknown", description="Unverified announcement lead", status="待核实",
            link="https://www.binance.com/en/support/announcement/test123",
            source_name="binance-announcements", trust_level="TRUST_LEVEL_0",
            publication_status="OBSERVATION"
        ))
        await db.commit()
        return 2

    monkeypatch.setattr("app.api.airdrop_hub.sync_airdrops", fake_sync)

    from app.api import airdrop_hub as hub_api
    async def fake_detail(code, *, fallback_title, fallback_url):
        assert code == "test123"
        return {
            "title": fallback_title,
            "content_html": "<p>Binance official full announcement content.</p>",
            "release_date": 1762800000000,
            "catalog_name": "Crypto Airdrop",
            "original_url": fallback_url,
        }
    monkeypatch.setattr(hub_api, "fetch_binance_article_detail", fake_detail)
    with TestClient(app) as client:
        refresh = client.post("/api/airdrops/sync")
        assert refresh.status_code == 200
        all_rows = client.get("/api/airdrops/hub")
        solana_rows = client.get("/api/airdrops/hub", params={"chain": "Solana"})
        assert all_rows.status_code == 200
        assert solana_rows.status_code == 200
        assert solana_rows.json() == []
        assert not any(row["name"] in {"Hub Solana Test", "Hub Ethereum Test"} for row in all_rows.json())
        observation = next(row for row in all_rows.json() if row["name"] == "Binance Observation Test")
        assert observation["link"].endswith("/test123")
        assert "publication_status" not in observation
        assert "data_source" not in observation
        detail = client.get("/api/airdrops/hub/" + observation["id"])
        assert detail.status_code == 200
        assert detail.json()["content_html"] == "<p>Binance official full announcement content.</p>"
        assert detail.json()["title"] == "Binance Observation Test"
        assert client.get("/api/airdrops/hub/status").status_code == 200

def test_github_raw_configuration_requires_exact_https_raw_file_urls(monkeypatch):
    import json

    monkeypatch.setenv("AIRDROP_GITHUB_RAW_SOURCES", json.dumps([
        "https://raw.githubusercontent.com/owner/repo/main/airdrop.json",
        "https://raw.githubusercontent.com.evil.example/owner/repo/main/list.json",
        "http://raw.githubusercontent.com/owner/repo/main/list.json",
        "https://user@raw.githubusercontent.com/owner/repo/main/list.json",
        "https://raw.githubusercontent.com/owner/repo",
        "https://raw.githubusercontent.com/owner/repo/main/list.json?token=secret",
        "https://raw.githubusercontent.com/owner/repo/main/../private.json",
    ]))

    assert _configured_github_raw_urls() == [
        "https://raw.githubusercontent.com/owner/repo/main/airdrop.json"
    ]


def test_github_json_lists_require_explicit_official_https_website_fields():
    rows = _normalize_github_airdrop_list([
        {"name": "Repository link is not an official website", "url": "https://github.com/org/project"},
        {"name": "Insecure website", "website": "http://project.example"},
        {"name": "Official project", "official_url": "https://project.example/"},
    ], "https://raw.githubusercontent.com/org/list/main/airdrop.json")

    assert len(rows) == 1
    assert rows[0]["name"] == "Official project"
    assert rows[0]["link"] == "https://project.example/"


def test_github_markdown_list_accepts_only_https_project_links():
    rows = _normalize_github_airdrop_list(
        "- [Secure Project](https://secure.example/airdrop)\n"
        "- [Insecure Project](http://insecure.example/airdrop)",
        "https://raw.githubusercontent.com/org/list/main/README.md",
    )

    assert len(rows) == 1
    assert rows[0]["name"] == "Secure Project"


def test_binance_article_codes_are_rejected_instead_of_silently_rewritten():
    rows = _normalize_binance_announcements({
        "data": {"catalogs": [{
            "articles": [
                {"title": "Valid Crypto Airdrop", "code": "valid_code-123"},
                {"title": "Malformed code", "code": "valid!code"},
            ]
        }]}
    }, catalog_id=128)

    assert len(rows) == 1
    assert rows[0]["source_key"] == "binance:valid_code-123"
    assert rows[0]["link"].endswith("/valid_code-123")


def test_github_raw_configuration_rejects_malformed_ports_and_encoded_path_traversal(monkeypatch):
    import json

    monkeypatch.setenv("AIRDROP_GITHUB_RAW_SOURCES", json.dumps([
        "https://raw.githubusercontent.com:badport/owner/repo/main/list.json",
        "https://raw.githubusercontent.com:99999/owner/repo/main/list.json",
        "https://@raw.githubusercontent.com/owner/repo/main/list.json",
        "https://raw.githubusercontent.com/owner/repo/main/%2e%2e/private.json",
        "https://raw.githubusercontent.com/owner/repo/main%2Fprivate/list.json",
        "https://raw.githubusercontent.com/owner/repo/main/list.json",
    ]))

    assert _configured_github_raw_urls() == [
        "https://raw.githubusercontent.com/owner/repo/main/list.json"
    ]


def test_github_raw_configuration_deduplicates_and_caps_sources(monkeypatch):
    import json

    sources = [f"https://raw.githubusercontent.com/owner/repo/main/list-{index}.json" for index in range(25)]
    monkeypatch.setenv("AIRDROP_GITHUB_RAW_SOURCES", json.dumps([sources[0], *sources]))

    accepted = _configured_github_raw_urls()
    assert len(accepted) == 20
    assert len(set(accepted)) == 20
