from datetime import datetime, timezone
from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app
from app.models import (
    AirdropCampaign,
    AirdropCampaignSourceSnapshot,
    AirdropCampaignStatusHistory,
    AirdropProject,
)
from app.services.airdrop_hub_sync import (
    _binance_article_plain_text,
    _configured_github_raw_sources,
    _normalize_binance_announcements,
    _normalize_binance_article_detail,
    _normalize_dexscreener_pairs,
    _normalize_github_airdrop_list,
    _upsert_binance_airdrop_campaign,
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
    filtered_id = uuid4()

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
            id=filtered_id,
            source_key="binance:filtered123", name="Filtered Binance News Test",
            chain="Unknown", description="Central filter rejected this unrelated activity headline", status="待筛选",
            link="https://www.binance.com/en/support/announcement/filtered123",
            source_name="binance-announcements", trust_level="TRUST_LEVEL_0",
            publication_status="OBSERVATION", filter_status="filtered",
        ))
        db.add(AirdropProject(
            source_key="binance:test123", name="Binance Observation Test",
            chain="Unknown", description="Unverified announcement lead", status="待核实",
            link="https://www.binance.com/en/support/announcement/test123",
            source_name="binance-announcements", trust_level="TRUST_LEVEL_0",
            publication_status="OBSERVATION",
            filter_metadata={"published_at": "2026-11-12T12:00:00+00:00"},
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
        assert not any(row["name"] in {"Hub Solana Test", "Hub Ethereum Test", "Filtered Binance News Test"} for row in all_rows.json())
        observation = next(row for row in all_rows.json() if row["name"] == "Binance Observation Test")
        assert observation["link"].endswith("/test123")
        assert observation["published_at"] == "2026-11-12T12:00:00+00:00"
        assert "publication_status" not in observation
        assert "data_source" not in observation
        detail = client.get("/api/airdrops/hub/" + observation["id"])
        assert detail.status_code == 200
        assert detail.json()["content_html"] == "<p>Binance official full announcement content.</p>"
        assert detail.json()["title"] == "Binance Observation Test"
        filtered_detail = client.get("/api/airdrops/hub/" + str(filtered_id))
        assert filtered_detail.status_code == 404
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

def test_github_adapter_preserves_entries_and_central_filter_decides_official_url():
    from app.services.airdrop_discovery_filter import build_discovery_identity, evaluate_discovery_observation

    source_url = "https://raw.githubusercontent.com/org/list/main/airdrop.json"
    rows = _normalize_github_airdrop_list([
        {"name": "Repository link is not an official website", "url": "https://github.com/org/project"},
        {"name": "Insecure website", "website": "http://project.example"},
        {"name": "Official project", "official_url": "https://project.example/"},
    ], source_url)

    assert len(rows) == 3
    assert rows[0]["link"] == source_url
    assert rows[1]["link"] == source_url
    assert rows[2]["link"] == "https://project.example/"
    missing = evaluate_discovery_observation(rows[0])
    insecure = evaluate_discovery_observation(rows[1])
    accepted = evaluate_discovery_observation(rows[2])
    assert missing["reasons"] == ["github_official_url_missing"]
    assert insecure["reasons"] == ["github_official_url_invalid"]
    assert accepted["reasons"] == []
    assert build_discovery_identity(rows[0])["project_identity_key"] is None


def test_github_markdown_entries_are_preserved_for_central_https_validation():
    from app.services.airdrop_discovery_filter import evaluate_discovery_observation

    source_url = "https://raw.githubusercontent.com/org/list/main/README.md"
    rows = _normalize_github_airdrop_list(
        "- [Secure Project](https://secure.example/airdrop)\n"
        "- [Insecure Project](http://insecure.example/airdrop)",
        source_url,
    )

    assert len(rows) == 2
    assert rows[0]["link"] == "https://secure.example/airdrop"
    assert rows[1]["link"] == source_url
    assert evaluate_discovery_observation(rows[1])["reasons"] == ["github_official_url_invalid"]


def test_binance_article_codes_are_rejected_instead_of_silently_rewritten():
    rows = _normalize_binance_announcements({
        "data": {"catalogs": [{
            "catalogName": "Crypto Airdrop",
            "articles": [
                {"title": "Valid Crypto Airdrop", "code": "valid_code-123"},
                {"title": "Malformed code", "code": "valid!code"},
            ]
        }]}
    }, catalog_id=128)

    assert len(rows) == 1
    assert rows[0]["source_key"] == "binance:valid_code-123"
    assert rows[0]["catalog_name"] == "Crypto Airdrop"
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
        if request.url.path.endswith("/coins/categories/list"):
            return httpx.Response(200, json=[{"id": "airdrop", "name": "Airdrop"}])
        if request.url.path.endswith("/coins/markets"):
            return httpx.Response(200, json=[{
                "id": "sample-token", "name": "Sample Token", "last_updated": "2026-11-11T11:00:00Z",
                "market_cap_rank": 1, "market_cap": 1000000, "total_volume": 100000,
            }])
        if request.url.path.endswith("/coins/sample-token"):
            return httpx.Response(200, json={"id": "sample-token", "links": {"homepage": ["https://sample.example/"]}})
        return httpx.Response(404)

    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            return await _fetch_coingecko_observations(client)

    rows = asyncio.run(run())
    assert seen_paths == [
        "/api/v3/coins/categories/list",
        "/api/v3/coins/markets",
        "/api/v3/coins/sample-token",
    ]
    assert len(rows) == 1
    assert rows[0]["source_key"] == "coingecko:airdrop:sample-token"
    assert rows[0]["link"] == "https://sample.example/"
    assert rows[0]["_filter_metadata"]["market_cap_rank"] == 1


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
        assert request.method == "GET"
        assert request.url.host == "www.binance.com"
        assert request.url.path == "/bapi/apex/v1/public/apex/cms/article/list/query"
        assert request.url.params["pageSize"] == "20"
        assert request.headers["user-agent"].endswith("Chrome/131.0.0.0 Safari/537.36")
        assert "AirdropHunter" not in request.headers["user-agent"]
        assert request.headers["accept-language"] == "en"
        assert request.headers["referer"] == "https://www.binance.com/en/messages/v2/group/announcement"
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


def test_all_provider_observations_enter_one_pending_candidate_queue_and_keep_review_decisions():
    import asyncio
    from datetime import datetime, timezone
    from app.models import AirdropDiscoveryCandidate
    from app.services.airdrop_hub_sync import _candidate_source_key, _upsert_discovery_candidate

    class FakeDB:
        def __init__(self, existing=None):
            self.existing = existing
            self.added = []

        async def scalar(self, statement):
            return self.existing

        def add(self, value):
            self.added.append(value)

    now = datetime.now(timezone.utc)
    record = {
        "source_key": "coingecko:airdrop:sample-project",
        "name": "Sample Project",
        "description": "CoinGecko 类目线索；不代表当前存在可领取活动。",
        "link": "https://www.coingecko.com/en/coins/sample-project",
    }
    db = FakeDB()
    created = asyncio.run(_upsert_discovery_candidate(
        db,
        source_name="coingecko-airdrop-category",
        source_url="https://api.coingecko.com/api/v3/coins/categories",
        record=record,
        now=now,
    ))

    assert created is True
    assert len(db.added) == 1
    candidate = db.added[0]
    assert isinstance(candidate, AirdropDiscoveryCandidate)
    assert candidate.source_key == _candidate_source_key(
        "coingecko-airdrop-category", "coingecko:airdrop:sample-project"
    )
    assert len(candidate.source_key) == 64
    assert candidate.review_status == "pending"
    assert candidate.item_url == record["link"]
    assert "不代表当前存在可领取活动" in candidate.summary

    existing = AirdropDiscoveryCandidate(
        source_key=candidate.source_key,
        source_name=candidate.source_name,
        source_url=candidate.source_url,
        title=candidate.title,
        item_url=candidate.item_url,
        summary=candidate.summary,
        review_status="rejected",
    )
    db2 = FakeDB(existing=existing)
    later = datetime.fromtimestamp(now.timestamp() + 60, timezone.utc)
    asyncio.run(_upsert_discovery_candidate(
        db2,
        source_name="coingecko-airdrop-category",
        source_url="https://api.coingecko.com/api/v3/coins/categories",
        record=record,
        now=later,
    ))
    assert existing.review_status == "rejected"
    assert existing.last_seen_at == later
    assert db2.added == []


def test_discovery_candidate_rejects_non_https_or_missing_project_links():
    import asyncio
    from datetime import datetime, timezone
    from app.services.airdrop_hub_sync import _upsert_discovery_candidate

    class FakeDB:
        async def scalar(self, statement):
            raise AssertionError("unsafe candidate must be rejected before database lookup")

        def add(self, value):
            raise AssertionError("unsafe candidate must not be stored")

    base = {
        "source_key": "dexscreener:chain:token",
        "name": "Token",
        "description": "Market observation only",
    }
    for url in ("http://unsafe.example/", "", None):
        assert asyncio.run(_upsert_discovery_candidate(
            FakeDB(),
            source_name="dexscreener-new-pools",
            source_url="https://api.dexscreener.com/latest/dex/search",
            record={**base, "link": url},
            now=datetime.now(timezone.utc),
        )) is False



def test_binance_activity_adapter_keeps_non_matching_titles_for_central_filter():
    from app.services.airdrop_discovery_filter import evaluate_discovery_observation
    rows = _normalize_binance_announcements({"data": {"catalogs": [{"articles": [
        {"title": "General platform update", "code": "general-update-001"},
        {"title": "Launchpool rewards", "code": "launchpool-002"},
    ]}]}}, catalog_id=93)
    assert len(rows) == 2
    decisions = [evaluate_discovery_observation(row) for row in rows]
    assert decisions[0]["status"] == "filtered"
    assert decisions[0]["reasons"] == ["binance_activity_title_not_airdrop_related"]
    assert decisions[1]["status"] == "eligible_for_review"


def test_dexscreener_adapter_keeps_low_activity_pairs_for_central_filter():
    from app.services.airdrop_discovery_filter import evaluate_discovery_observation
    rows = _normalize_dexscreener_pairs({"pairs": [{
        "chainId": "solana", "dexId": "raydium", "pairCreatedAt": 1762800000000,
        "baseToken": {"name": "Low Activity", "symbol": "LOW", "address": "TokenAddress"},
        "url": "https://dexscreener.com/solana/tokenaddress",
        "liquidity": {"usd": 100}, "volume": {"h24": 200},
        "txns": {"h24": {"buys": 1, "sells": 1}},
    }]}, "SOL")
    assert len(rows) == 1
    decision = evaluate_discovery_observation(rows[0])
    assert decision["status"] == "filtered"
    assert "liquidity_not_above_10000_usd" in decision["reasons"]


def test_dexscreener_filter_uses_existing_thresholds_and_keeps_raw_metrics():
    from app.services.airdrop_discovery_filter import evaluate_discovery_observation
    record = {"source_name": "dexscreener-new-pools", "_filter_metadata": {
        "age_seconds": 3600, "liquidity_usd": 10001, "volume_24h_usd": 50000,
        "trades_24h": 20, "token_address": "Address",
    }}
    decision = evaluate_discovery_observation(record)
    assert decision["status"] == "eligible_for_review"
    assert decision["metadata"]["token_address"] == "Address"


def test_coingecko_category_selection_is_centralized():
    from app.services.airdrop_discovery_filter import select_coingecko_airdrop_categories
    categories = [{"id": "airdrop", "name": "Airdrop"}, {"id": "gaming", "name": "Gaming"}]
    assert select_coingecko_airdrop_categories(categories) == ["airdrop"]


def test_identity_does_not_use_names_and_keeps_project_campaign_separate():
    from app.services.airdrop_discovery_filter import build_discovery_identity, classify_identity_matches
    a = build_discovery_identity({"source_name": "github-airdrop-lists", "name": "Same Name", "_filter_metadata": {"official_url": "https://alpha.example"}})
    b = build_discovery_identity({"source_name": "github-airdrop-lists", "name": "Same Name", "_filter_metadata": {"official_url": "https://beta.example"}})
    assert a["project_identity_key"] != b["project_identity_key"]
    first = build_discovery_identity({"source_name": "github-airdrop-lists", "_filter_metadata": {"official_url": "https://project.example"}, "campaign_url": "https://project.example/season-one"})
    second = build_discovery_identity({"source_name": "binance-announcements", "link": "https://www.binance.com/en/support/announcement/season-two", "_filter_metadata": {"official_url": "https://project.example", "campaign_url": "https://project.example/season-two"}})
    class Existing:
        source_key = "source:season-one"
        source_name = "github-airdrop-lists"
        project_identity_key = first["project_identity_key"]
        campaign_identity_key = first["campaign_identity_key"]
    result = classify_identity_matches(second, [Existing()])
    assert result["identity_status"] == "same_project_distinct_campaign"
    assert result["matched_source_key"] == "source:season-one"
    assert first["campaign_identity_key"] != second["campaign_identity_key"]


def test_contract_identity_is_chain_scoped_and_normalizes_evm_addresses():
    from app.services.airdrop_discovery_filter import build_discovery_identity
    a = build_discovery_identity({"_filter_metadata": {"chain_id": "ethereum", "token_address": "0x" + "A" * 40}})
    b = build_discovery_identity({"_filter_metadata": {"chain_id": "ethereum", "token_address": "0x" + "a" * 40}})
    c = build_discovery_identity({"_filter_metadata": {"chain_id": "base", "token_address": "0x" + "a" * 40}})
    assert a["project_identity_key"] == b["project_identity_key"]
    assert a["project_identity_key"] != c["project_identity_key"]


def test_identity_url_is_https_only_and_ignores_tracking_parameters():
    from app.services.airdrop_discovery_filter import build_discovery_identity
    a = build_discovery_identity({"source_name": "github-airdrop-lists", "link": "https://Project.example/path/?utm_source=x"})
    b = build_discovery_identity({"source_name": "github-airdrop-lists", "link": "https://project.example/path"})
    unsafe = build_discovery_identity({"source_name": "github-airdrop-lists", "link": "http://project.example/path"})
    assert a["project_identity_key"] == b["project_identity_key"]
    assert unsafe["project_identity_key"] is None


def test_same_campaign_evidence_is_grouped_and_conflicting_project_identity_is_flagged():
    from app.services.airdrop_discovery_filter import build_discovery_identity, classify_identity_matches

    current = build_discovery_identity({
        "source_name": "github-airdrop-lists",
        "_filter_metadata": {
            "official_url": "https://project-alpha.example",
            "campaign_url": "https://project-alpha.example/season",
        },
    })
    class SameSourceA:
        source_key = "source:a"
        source_name = "github-airdrop-lists"
        project_identity_key = current["project_identity_key"]
        campaign_identity_key = current["campaign_identity_key"]

    class SameSourceB:
        source_key = "source:b"
        source_name = "binance-announcements"
        project_identity_key = current["project_identity_key"]
        campaign_identity_key = current["campaign_identity_key"]

    grouped = classify_identity_matches(current, [SameSourceA(), SameSourceB()])
    assert grouped["identity_status"] == "same_project_same_campaign"
    assert grouped["identity_conflict"] is False
    assert grouped["matched_source_keys"] == ["source:a", "source:b"]

    other_project = build_discovery_identity({
        "source_name": "github-airdrop-lists",
        "_filter_metadata": {
            "official_url": "https://project-beta.example",
            "campaign_url": "https://project-beta.example/season",
        },
    })
    class ConflictingCampaign:
        source_key = "source:conflict"
        source_name = "another-provider"
        project_identity_key = other_project["project_identity_key"]
        campaign_identity_key = current["campaign_identity_key"]

    conflicted = classify_identity_matches(current, [ConflictingCampaign()])
    assert conflicted["identity_status"] == "identity_conflict_needs_review"
    assert conflicted["identity_conflict"] is True
    assert conflicted["matched_source_keys"] == ["source:conflict"]


def test_identity_groups_keep_campaigns_separate_under_one_project():
    from app.services.airdrop_discovery_filter import build_discovery_identity, classify_identity_matches

    first = build_discovery_identity({
        "source_name": "github-airdrop-lists",
        "_filter_metadata": {
            "official_url": "https://project.example",
            "campaign_url": "https://project.example/season-one",
        },
    })
    second = build_discovery_identity({
        "source_name": "github-airdrop-lists",
        "_filter_metadata": {
            "official_url": "https://project.example",
            "campaign_url": "https://project.example/season-two",
        },
    })
    first_group = classify_identity_matches(first, [])
    second_group = classify_identity_matches(second, [])

    assert first_group["identity_group_type"] == "campaign"
    assert second_group["identity_group_type"] == "campaign"
    assert first_group["identity_group_key"] == first["campaign_identity_key"]
    assert second_group["identity_group_key"] == second["campaign_identity_key"]
    assert first_group["identity_group_key"] != second_group["identity_group_key"]
    assert first["project_identity_key"] == second["project_identity_key"]

    project_only = classify_identity_matches({
        "project_identity_key": first["project_identity_key"],
        "campaign_identity_key": None,
        "project_identity_evidence": first["project_identity_evidence"],
        "campaign_identity_evidence": {},
    }, [])
    assert project_only["identity_group_type"] == "project"
    assert project_only["identity_group_key"] == first["project_identity_key"]

    unresolved = classify_identity_matches({
        "project_identity_key": None,
        "campaign_identity_key": None,
        "project_identity_evidence": {},
        "campaign_identity_evidence": {},
    }, [])
    assert unresolved["identity_group_type"] == "unresolved"
    assert unresolved["identity_group_key"] is None


def test_central_discovery_decision_composes_filter_and_identity_without_publishing():
    from app.services.airdrop_discovery_filter import compose_discovery_decision

    eligible = compose_discovery_decision(
        {"status": "eligible_for_review", "reasons": [], "metadata": {}, "evaluated_at": "2026-11-11T00:00:00+00:00"},
        {"identity_status": "same_project_distinct_campaign", "identity_conflict": False,
         "identity_group_type": "campaign", "identity_group_key": "campaign-key"},
    )
    assert eligible["disposition"] == "keep"
    assert eligible["identity_group_type"] == "campaign"
    assert eligible["identity_group_key"] == "campaign-key"
    assert eligible["reasons"] == []

    conflict = compose_discovery_decision(
        {"status": "eligible_for_review", "reasons": [], "metadata": {}, "evaluated_at": "2026-11-11T00:00:00+00:00"},
        {"identity_status": "identity_conflict_needs_review", "identity_conflict": True,
         "identity_group_type": "needs_review", "identity_group_key": None},
    )
    assert conflict["disposition"] == "needs_review"
    assert conflict["identity_group_key"] is None
    assert conflict["reasons"] == ["project_campaign_identity_conflict_needs_manual_review"]

    filtered = compose_discovery_decision(
        {"status": "filtered", "reasons": ["liquidity_below_existing_limit"], "metadata": {}, "evaluated_at": "2026-11-11T00:00:00+00:00"},
        {"identity_status": "identity_conflict_needs_review", "identity_conflict": True,
         "identity_group_type": "needs_review", "identity_group_key": None},
    )
    assert filtered["disposition"] == "filtered"
    assert "project_campaign_identity_conflict_needs_manual_review" in filtered["reasons"]


def test_discovery_observation_payload_is_bounded_and_removes_credentials():
    from app.services.airdrop_hub_sync import _bounded_observation_payload

    saved = _bounded_observation_payload({
        "title": "Observed campaign",
        "apiKey": "must-not-be-stored",
        "authorization": "Bearer private-token",
        "nested": {"refresh_token": "secret", "value": "kept"},
    })
    assert saved["title"] == "Observed campaign"
    assert "apiKey" not in saved
    assert "authorization" not in saved
    assert "refresh_token" not in saved["nested"]
    assert saved["nested"]["value"] == "kept"

    oversized = _bounded_observation_payload({"body": "x" * 100000})
    assert oversized["_truncated"] is True
    assert oversized["_original_serialized_bytes"] > 64 * 1024





def test_source_freshness_rejects_old_generated_and_updated_timestamps():
    from datetime import datetime, timezone, timedelta
    from app.services.airdrop_discovery_filter import is_source_fresh
    now = datetime(2026, 11, 11, 12, 0, tzinfo=timezone.utc)
    fresh = (now - timedelta(days=6)).isoformat()
    stale = (now - timedelta(days=8)).isoformat()
    assert is_source_fresh({"generatedAt": fresh}, now=now)
    assert is_source_fresh([{"last_updated": fresh}], now=now)
    assert not is_source_fresh({"generatedAt": stale}, now=now)
    assert not is_source_fresh([{"updated_at": stale}], now=now)
    assert not is_source_fresh({"updatedAt": "not-a-date"}, now=now)
    assert is_source_fresh({"name": "no upstream timestamp"}, now=now)


def test_dexscreener_central_selector_keeps_only_top_ten_above_10k_liquidity():
    from app.services.airdrop_discovery_filter import select_dexscreener_top_candidates
    rows = [
        {"name": f"Token {index}", "_filter_metadata": {"liquidity_usd": index * 1000, "volume_24h_usd": index}}
        for index in range(1, 26)
    ]
    selected = select_dexscreener_top_candidates(rows)
    assert len(selected) == 10
    assert selected[0]["name"] == "Token 25"
    assert selected[-1]["name"] == "Token 16"


def test_dexscreener_market_signal_is_not_an_airdrop_campaign():
    from app.services.airdrop_discovery_filter import evaluate_discovery_observation
    decision = evaluate_discovery_observation({
        "source_name": "dexscreener-new-pools",
        "_filter_metadata": {"age_seconds": 3600, "liquidity_usd": 15000},
    })
    assert decision["status"] == "eligible_for_review"


def test_dexscreener_top_ten_selection_excludes_pools_older_than_seven_days():
    from app.services.airdrop_discovery_filter import select_dexscreener_top_candidates

    rows = [
        {"name": "Old high liquidity", "_filter_metadata": {"liquidity_usd": 90000, "age_seconds": 900000}},
        {"name": "Fresh pool", "_filter_metadata": {"liquidity_usd": 15000, "age_seconds": 3600}},
    ]
    selected = select_dexscreener_top_candidates(rows)
    assert [row["name"] for row in selected] == ["Fresh pool"]


def test_binance_announcement_normalizer_preserves_official_release_date():
    from datetime import datetime, timezone
    from app.services.airdrop_discovery_filter import evaluate_discovery_observation

    rows = _normalize_binance_announcements({"data": {"catalogs": [{"articles": [
        {"title": "Official Airdrop Announcement", "code": "date-test-001", "releaseDate": 1794484800000},
        {"title": "Announcement without date", "code": "date-test-002"},
        {"title": "Malformed date", "code": "date-test-003", "releaseDate": "not-a-date"},
    ]}]}}, catalog_id=128)

    assert rows[0]["published_at"] == datetime(2026, 11, 12, 12, 0, tzinfo=timezone.utc)
    assert rows[1]["published_at"] is None
    assert rows[2]["published_at"] is None
    decision = evaluate_discovery_observation(rows[0])
    assert decision["metadata"]["published_at"] == "2026-11-12T12:00:00+00:00"


def test_official_binance_crypto_airdrop_detail_creates_evidence_backed_campaign():
    import asyncio

    class FakeDb:
        def __init__(self):
            self.added = []

        async def scalar(self, statement):
            return None

        def add(self, value):
            self.added.append(value)

        async def flush(self):
            for value in self.added:
                if isinstance(value, AirdropCampaign) and value.id is None:
                    value.id = uuid4()

    db = FakeDb()
    record = {
        "catalog_id": 128,
        "catalog_name": "Crypto Airdrop",
        "source_key": "binance:article-code-123",
        "name": "Official token airdrop",
        "link": "https://www.binance.com/en/support/announcement/article-code-123",
    }
    detail = {
        "title": "Official token airdrop",
        "content_html": "<h2>Reward</h2><p>Stake BNB to earn tokens.</p><p>Eligibility is subject to terms.</p>",
        "release_date": 1790000000000,
    }

    created = asyncio.run(_upsert_binance_airdrop_campaign(
        db, record=record, detail=detail, now=datetime.now(timezone.utc)
    ))

    campaign = next(item for item in db.added if isinstance(item, AirdropCampaign))
    assert created is True
    assert campaign.source_key == "binance:crypto-airdrop:article-code-123"
    assert campaign.source_name == "binance-crypto-airdrop"
    assert campaign.campaign_type == "airdrop"
    assert campaign.campaign_status == "unknown"
    assert campaign.trust_status == "verified"
    assert campaign.official_url == record["link"]
    assert campaign.chain is None
    assert campaign.requires_kyc is None
    assert "Stake BNB to earn tokens." in campaign.description
    assert any(isinstance(item, AirdropCampaignStatusHistory) for item in db.added)
    assert any(isinstance(item, AirdropCampaignSourceSnapshot) for item in db.added)


def test_latest_activities_and_missing_binance_article_body_are_not_promoted():
    import asyncio

    class FakeDb:
        async def scalar(self, statement):
            raise AssertionError("ineligible records must be rejected before database access")

        def add(self, value):
            raise AssertionError("ineligible records must not be persisted")

    db = FakeDb()
    record = {
        "catalog_id": 93,
        "catalog_name": "Latest Activities",
        "source_key": "binance:activity-code",
        "link": "https://www.binance.com/en/support/announcement/activity-code",
    }
    detail = {"title": "Some promotion", "content_html": "<p>Reward</p>"}
    assert asyncio.run(_upsert_binance_airdrop_campaign(
        db, record=record, detail=detail, now=datetime.now(timezone.utc)
    )) is False

    record["catalog_id"] = 128
    record["catalog_name"] = "Crypto Airdrop"
    detail["catalog_name"] = "Latest Activities"
    assert asyncio.run(_upsert_binance_airdrop_campaign(
        db, record=record, detail=detail, now=datetime.now(timezone.utc)
    )) is False
    detail["catalog_name"] = "Crypto Airdrop"
    detail["content_html"] = ""
    assert asyncio.run(_upsert_binance_airdrop_campaign(
        db, record=record, detail=detail, now=datetime.now(timezone.utc)
    )) is False


def test_binance_campaign_description_is_plain_text_not_html():
    text = _binance_article_plain_text("<h2>Reward</h2><p>Stake BNB &amp; earn.</p><script>doNotDisplay()</script>")
    assert "<h2>" not in text
    assert "doNotDisplay" not in text
    assert "Reward" in text
    assert "Stake BNB & earn." in text
