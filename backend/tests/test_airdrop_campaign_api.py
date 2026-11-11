from datetime import datetime, timezone
from decimal import Decimal
from types import SimpleNamespace
from uuid import uuid4

from fastapi.testclient import TestClient

from app.database import get_db
from app.main import app


class FakeScalars:
    def all(self):
        return [SimpleNamespace(
            id=uuid4(),
            name="Verified Test Campaign",
            source_name="approved-airdrop-directory",
            source_url="https://directory.example/campaign/verified-test",
            chain="Base",
            description="Verified campaign description",
            official_url="https://official.example/campaign",
            campaign_type="points",
            campaign_status="ongoing",
            starts_at=None,
            ends_at=None,
            snapshot_at=None,
            claim_starts_at=None,
            claim_ends_at=None,
            retroactive_possible=None,
            is_kyc_free=True,
            requires_kyc=False,
            requires_phone=None,
            difficulty="medium",
            cost_to_farm_usd=Decimal("0"),
            tier="featured",
            last_verified_at=None,
            updated_at=datetime.now(timezone.utc),
        )]


class FakeDb:
    async def scalars(self, statement):
        return FakeScalars()


def test_public_campaign_api_serializes_campaign_fields_and_is_read_only():
    async def override_db():
        yield FakeDb()

    app.dependency_overrides[get_db] = override_db
    try:
        with TestClient(app) as client:
            response = client.get("/api/airdrop-campaigns", params={"chain": "Base", "kyc": "free"})
        assert response.status_code == 200
        row = response.json()[0]
        assert row["name"] == "Verified Test Campaign"
        assert row["source_name"] == "approved-airdrop-directory"
        assert row["source_url"] == "https://directory.example/campaign/verified-test"
        assert row["tier"] == "featured"
        forbidden_internal_fields = {
            "temperature",
            "opportunity_score",
            "token_network",
            "token_contract_address",
            "trust_status",
            "dex_pair_count",
            "dex_liquidity_usd",
            "dex_volume_24h_usd",
            "gecko_trending",
            "score",
            "scoring_version",
            "listing_reason",
        }
        assert forbidden_internal_fields.isdisjoint(row)
        assert row["is_kyc_free"] is True
        assert row["campaign_status"] == "ongoing"
        assert row["official_url"] == "https://official.example/campaign"
    finally:
        app.dependency_overrides.pop(get_db, None)


def test_public_campaign_api_rejects_unbounded_pagination():
    async def override_db():
        yield FakeDb()

    app.dependency_overrides[get_db] = override_db
    try:
        with TestClient(app) as client:
            response = client.get("/api/airdrop-campaigns", params={"limit": 1000})
        assert response.status_code == 422
    finally:
        app.dependency_overrides.pop(get_db, None)
