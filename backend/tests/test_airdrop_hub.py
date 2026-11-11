from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app
from app.models import AirdropProject
from app.services.airdrop_hub_sync import (
    _configured_github_raw_sources,
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

def _approved_github_source(**overrides):
    import copy

    source = {
        "source_id": "sample-list",
        "url": "https://raw.githubusercontent.com/owner/repo/0123456789abcdef0123456789abcdef01234567/data.json",
        "repository": "owner/repo",
        "license_id": "MIT",
        "license_url": "https://spdx.org/licenses/MIT.html",
        "attribution": "Source: owner/repo; retain this notice.",
        "review_status": "approved",
        "reviewed_at": "2026-11-11",
    }
    source.update(copy.deepcopy(overrides))
    return source


def test_github_raw_configuration_requires_manual_license_review_and_commit_pin(monkeypatch):
    import json

    monkeypatch.setenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", json.dumps([_approved_github_source()]))
    accepted = _configured_github_raw_sources()
    assert len(accepted) == 1
    assert accepted[0]["source_id"] == "sample-list"
    assert accepted[0]["license_id"] == "MIT"
    assert accepted[0]["attribution"].startswith("Source:")
    assert accepted[0]["review_status"] == "approved"


def test_github_raw_configuration_rejects_missing_or_unapproved_metadata(monkeypatch):
    import json

    monkeypatch.delenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", raising=False)
    monkeypatch.setenv(
        "AIRDROP_GITHUB_RAW_SOURCES",
        json.dumps(["https://raw.githubusercontent.com/owner/repo/main/data.json"]),
    )
    assert _configured_github_raw_sources() == []

    for override in [
        {"license_id": ""},
        {"license_url": "http://spdx.org/licenses/MIT.html"},
        {"attribution": ""},
        {"review_status": "pending"},
        {"reviewed_at": "2026-02-30"},
        {"repository": "someone/else"},
    ]:
        monkeypatch.setenv(
            "AIRDROP_GITHUB_RAW_SOURCE_APPROVALS",
            json.dumps([_approved_github_source(**override)]),
        )
        assert _configured_github_raw_sources() == []


def test_github_raw_configuration_rejects_unsafe_urls_and_unpinned_refs(monkeypatch):
    import json

    invalid_urls = [
        "https://raw.githubusercontent.com.evil.example/owner/repo/0123456789abcdef0123456789abcdef01234567/list.json",
        "http://raw.githubusercontent.com/owner/repo/0123456789abcdef0123456789abcdef01234567/list.json",
        "https://user@raw.githubusercontent.com/owner/repo/0123456789abcdef0123456789abcdef01234567/list.json",
        "https://raw.githubusercontent.com/owner/repo/main/list.json",
        "https://raw.githubusercontent.com/owner/repo/0123456789abcdef0123456789abcdef01234567/list.json?token=x",
        "https://raw.githubusercontent.com/owner/repo/0123456789abcdef0123456789abcdef01234567/../private.json",
        "https://raw.githubusercontent.com:badport/owner/repo/0123456789abcdef0123456789abcdef01234567/list.json",
        "https://raw.githubusercontent.com/owner/repo/0123456789abcdef0123456789abcdef01234567%2Fprivate/list.json",
    ]
    for url in invalid_urls:
        monkeypatch.setenv(
            "AIRDROP_GITHUB_RAW_SOURCE_APPROVALS",
            json.dumps([_approved_github_source(url=url)]),
        )
        assert _configured_github_raw_sources() == []


def test_github_raw_configuration_rejects_duplicate_ids_urls_and_more_than_twenty_sources(monkeypatch):
    import json

    first = _approved_github_source()
    duplicate_id = _approved_github_source(
        url="https://raw.githubusercontent.com/owner/repo/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/other.json"
    )
    monkeypatch.setenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", json.dumps([first, duplicate_id]))
    assert _configured_github_raw_sources() == []

    duplicate_url = _approved_github_source(source_id="another-list")
    monkeypatch.setenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", json.dumps([first, duplicate_url]))
    assert _configured_github_raw_sources() == []

    too_many = [
        _approved_github_source(
            source_id=f"source-{index}",
            url=f"https://raw.githubusercontent.com/owner/repo/{index:040x}/list.json",
        )
        for index in range(21)
    ]
    monkeypatch.setenv("AIRDROP_GITHUB_RAW_SOURCE_APPROVALS", json.dumps(too_many))
    assert _configured_github_raw_sources() == []

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




def test_coingecko_demo_key_is_sent_in_header_not_query_string(monkeypatch):
    import asyncio
    import httpx
    from app.services.airdrop_hub_sync import _fetch_coingecko_observations

    monkeypatch.setenv("COINGECKO_DEMO_API_KEY", "demo-secret-for-test")
    seen_paths = []

    def handler(request):
        assert request.headers.get("x-cg-demo-api-key") == "demo-secret-for-test"
        assert "x_cg_demo_api_key" not in request.url.params
        seen_paths.append(request.url.path)
        if request.url.path.endswith("/coins/categories"):
            return httpx.Response(200, json=[{"id": "airdrop", "name": "Airdrop"}])
        if request.url.path.endswith("/coins/markets"):
            return httpx.Response(200, json=[{"id": "sample-token", "name": "Sample Token"}])
        return httpx.Response(404)

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            return await _fetch_coingecko_observations(client)

    rows = asyncio.run(run())
    assert seen_paths == ["/api/v3/coins/categories", "/api/v3/coins/markets"]
    assert len(rows) == 1
    assert rows[0]["source_key"] == "coingecko:airdrop:sample-token"


def test_binance_endpoint_discovery_extracts_only_binance_cms_path():
    from app.services.airdrop_hub_sync import _extract_binance_cms_path_from_reference

    source = 'const listUrl = new URL(`${baseUrl}/bapi/apex/v1/public/apex/cms/article/list/query`);'
    assert _extract_binance_cms_path_from_reference(source) == "/bapi/apex/v1/public/apex/cms/article/list/query"
    assert _extract_binance_cms_path_from_reference('fetch("https://evil.example/steal")') is None
    assert _extract_binance_cms_path_from_reference('new URL(`${baseUrl}/api/private/list`)') is None


def test_binance_sync_discovers_endpoint_from_reference_then_fetches_binance_data(monkeypatch):
    import asyncio
    import httpx
    from app.services import airdrop_hub_sync

    monkeypatch.setattr(airdrop_hub_sync, "_binance_discovery_cache", None)
    requested = []

    def handler(request):
        requested.append((request.url.host, request.url.path, request.url.params.get("catalogId")))
        if request.url.host == "raw.githubusercontent.com":
            return httpx.Response(200, text='const listUrl = new URL(`${baseUrl}/bapi/apex/v1/public/apex/cms/article/list/query`);')
        catalog_id = int(request.url.params["catalogId"])
        assert request.url.host == "www.binance.com"
        assert request.url.path == "/bapi/apex/v1/public/apex/cms/article/list/query"
        title, code = (
            ("Official Crypto Airdrop Announcement", "official-airdrop-001")
            if catalog_id == 128
            else ("Binance Launchpool: Stake Tokens to Earn Rewards", "latest-activity-002")
        )
        return httpx.Response(200, json={"data": {"catalogs": [{"catalogId": catalog_id, "articles": [{"title": title, "code": code}]}]}})

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            return await airdrop_hub_sync._fetch_binance_observations(client)

    rows = asyncio.run(run())
    assert requested == [
        ("raw.githubusercontent.com", "/DIYgod/RSSHub/master/lib/routes/binance/announcement.ts", None),
        ("www.binance.com", "/bapi/apex/v1/public/apex/cms/article/list/query", "128"),
        ("www.binance.com", "/bapi/apex/v1/public/apex/cms/article/list/query", "93"),
    ]
    assert {row["source_key"] for row in rows} == {
        "binance:official-airdrop-001",
        "binance:latest-activity-002",
    }


def test_binance_sync_uses_known_fallback_when_endpoint_discovery_is_unavailable(monkeypatch):
    import asyncio
    import httpx
    from app.services import airdrop_hub_sync

    monkeypatch.setattr(airdrop_hub_sync, "_binance_discovery_cache", None)
    requested = []

    def handler(request):
        if request.url.host == "raw.githubusercontent.com":
            return httpx.Response(503)
        requested.append((request.url.path, request.url.params["catalogId"]))
        if request.url.path == "/bapi/apex/v1/public/apex/cms/article/list/query":
            return httpx.Response(404)
        catalog_id = int(request.url.params["catalogId"])
        title = "Official Crypto Airdrop Announcement" if catalog_id == 128 else "Binance Launchpool: Stake Tokens"
        code = f"legacy-{catalog_id}"
        return httpx.Response(200, json={"data": {"catalogs": [{"catalogId": catalog_id, "articles": [{"title": title, "code": code}]}]}})

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            return await airdrop_hub_sync._fetch_binance_observations(client)

    rows = asyncio.run(run())
    assert requested == [
        ("/bapi/apex/v1/public/apex/cms/article/list/query", "128"),
        ("/bapi/composite/v1/public/cms/article/list/query", "128"),
        ("/bapi/apex/v1/public/apex/cms/article/list/query", "93"),
        ("/bapi/composite/v1/public/cms/article/list/query", "93"),
    ]
    assert {row["source_key"] for row in rows} == {"binance:legacy-128", "binance:legacy-93"}
