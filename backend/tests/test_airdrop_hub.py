from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import app
from app.models import AirdropProject
from app.services.airdrop_hub_sync import _normalize, _status_label


def test_normalize_public_airdrop_payload():
    rows = _normalize({
        "data": [{
            "slug": "sample-airdrop",
            "project": "Sample Protocol",
            "blockchains": ["Ethereum", "Arbitrum"],
            "blurb": "A public test listing",
            "status": "upcoming",
            "officialUrl": "https://sample.example/",
        }]
    })
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


def test_airdrop_hub_chain_filter_and_refresh(monkeypatch):
    async def fake_sync(db):
        db.add(AirdropProject(
            source_key=f"test-{uuid4().hex}:solana",
            name="Hub Solana Test",
            chain="Solana",
            description="Inserted by API test",
            status="热门",
            link="https://solana.example/",
            source_name="test",
        ))
        db.add(AirdropProject(
            source_key=f"test-{uuid4().hex}:ethereum",
            name="Hub Ethereum Test",
            chain="Ethereum",
            description="Inserted by API test",
            status="待发币",
            link="https://ethereum.example/",
            source_name="test",
        ))
        await db.commit()
        return 2

    monkeypatch.setattr("app.api.airdrop_hub.sync_airdrop_hub", fake_sync)
    with TestClient(app) as client:
        refresh = client.post("/api/airdrops/hub/refresh")
        assert refresh.status_code == 200
        all_rows = client.get("/api/airdrops/hub")
        solana_rows = client.get("/api/airdrops/hub", params={"chain": "Solana"})
        assert all_rows.status_code == 200
        assert solana_rows.status_code == 200
        assert any(row["name"] == "Hub Solana Test" for row in solana_rows.json())
        assert all(row["chain"] == "Solana" for row in solana_rows.json())
        assert not any(row["name"] == "Hub Ethereum Test" for row in solana_rows.json())


def test_status_mapping_is_honest():
    assert _status_label("hot") == "热门"
    assert _status_label("upcoming") == "待发币"
    assert _status_label("active") == "进行中"
