from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app
from app.models import AirdropProject
from app.services.airdrop_hub_sync import _normalize, _normalize_binance_article_detail, _status_label

def test_normalize_public_airdrop_payload():
    rows = _normalize({"data": [{
        "slug": "sample-airdrop", "project": "Sample Protocol",
        "blockchains": ["Ethereum", "Arbitrum"], "blurb": "A public test listing",
        "status": "upcoming", "officialUrl": "https://sample.example/"
    }]})
    assert len(rows) == 2
    assert {row["chain"] for row in rows} == {"Ethereum", "Arbitrum"}
    assert all(row["name"] == "Sample Protocol" for row in rows)
    assert all(row["status"] == "待发币" for row in rows)
    assert len({row["source_key"] for row in rows}) == 2

def test_normalize_rejects_missing_or_unsafe_links():
    rows = _normalize([
        {"name": "No link project", "chain": "Ethereum"},
        {"name": "Unsafe link project", "chain": "Solana", "link": "javascript:alert(1)"},
        {"name": "Valid project", "chain": "Base", "link": "https://valid.example"},
    ])
    assert len(rows) == 1
    assert rows[0]["name"] == "Valid project"


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

def test_status_mapping_is_honest():
    assert _status_label("hot") == "热门"
    assert _status_label("upcoming") == "待发币"
    assert _status_label("active") == "进行中"
