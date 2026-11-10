from uuid import uuid4
from decimal import Decimal
import hashlib

import pytest
from fastapi.testclient import TestClient

from app.database import get_db
from fastapi import HTTPException
from app.main import app
from app.api.community_stats import CommunityAssetAmount, normalize_public_asset_breakdown
from app.models import CommunityProjectStats, User
from app.routers.auth import get_optional_current_user
from app.services.transaction_verification import VerificationResult


@pytest.fixture(autouse=True)
def stub_chain_verification(monkeypatch):
    async def fake_verify(network, wallet_address, tx_hash, expected_gas_usd):
        fingerprint = hashlib.sha256(f"{network.lower()}:{tx_hash.lower()}".encode()).hexdigest()
        return VerificationResult(
            tier="amount_verified",
            gas_cost_usd=Decimal(str(expected_gas_usd)),
            tx_fingerprint=fingerprint,
            metadata={
                "network": network.lower(), "block_timestamp": 1794258505,
                "price_updated_at": 1794258400, "native_fee": "0.001", "native_symbol": "ETH",
                "price_source": "chainlink:ethereum", "fee_components_complete": True
            },
        )
    monkeypatch.setattr("app.api.community_stats.verify_community_transaction", fake_verify)


def _headers(client, prefix: str):
    username = f"{prefix}{uuid4().hex[:12]}"
    response = client.post("/api/auth/register", json={
        "username": username, "password": "strong-password-123"
    })
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}



def test_reward_asset_breakdown_must_be_complete_and_match_actual_usd_total():
    verification = VerificationResult(
        tier="transaction_linked",
        gas_cost_usd=Decimal("1.00"),
        tx_fingerprint="fingerprint",
        metadata={},
    )
    incomplete = [
        CommunityAssetAmount(
            kind="reward", amount_native=Decimal("100"), symbol="TOKEN",
            decimals=18, usd_valuation=None,
        )
    ]
    with pytest.raises(HTTPException) as missing_quote:
        normalize_public_asset_breakdown(incomplete, Decimal("0"), verification)
    assert missing_quote.value.status_code == 422

    mismatched = [
        CommunityAssetAmount(
            kind="reward", amount_native=Decimal("100"), symbol="TOKEN",
            decimals=18, usd_valuation=Decimal("1000"),
        )
    ]
    with pytest.raises(HTTPException) as wrong_total:
        normalize_public_asset_breakdown(mismatched, Decimal("1"), verification)
    assert wrong_total.value.status_code == 422


def test_amount_verified_gas_breakdown_is_replaced_by_one_server_derived_row():
    verification = VerificationResult(
        tier="amount_verified",
        gas_cost_usd=Decimal("0.063"),
        tx_fingerprint="fingerprint",
        metadata={
            "price_updated_at": 1794258400,
            "native_fee": "0.000021",
            "native_symbol": "ETH",
            "price_source": "chainlink:ethereum",
        },
    )
    assets = [
        CommunityAssetAmount(
            kind="gas", amount_native=Decimal("99"), symbol="FAKE",
            decimals=18, usd_valuation=Decimal("999"),
        ),
        CommunityAssetAmount(
            kind="gas", amount_native=Decimal("88"), symbol="DUP",
            decimals=18, usd_valuation=Decimal("888"),
        ),
        CommunityAssetAmount(
            kind="reward", amount_native=Decimal("10"), symbol="LINEA",
            decimals=18, usd_valuation=Decimal("4.50"),
        ),
    ]
    normalized = normalize_public_asset_breakdown(assets, Decimal("4.50"), verification)
    gas_rows = [row for row in normalized if row["kind"] == "gas"]
    assert len(gas_rows) == 1
    assert gas_rows[0]["symbol"] == "ETH"
    assert gas_rows[0]["amount_native"] == "0.000021"
    assert gas_rows[0]["usd_valuation"] == "0.063"
    assert gas_rows[0]["valuation_source"] == "chainlink:ethereum"
    assert [row["symbol"] for row in normalized if row["kind"] == "reward"] == ["LINEA"]


def test_community_contribution_is_idempotent_and_public_stats_are_aggregated():
    with TestClient(app) as client:
        headers = _headers(client, "contrib")
        event_id = str(uuid4())
        payload = {
            "event_id": event_id,
            "network": "ethereum",
            "wallet_address": "0x1234567890abcdef1234567890abcdef12345678",
            "tx_hash": "0x" + "a" * 64,
            "public_contribution_opt_in": True,
            "gas_cost_usd": "2.50",
            "actual_reward_usd": "12.00",
            "asset_breakdown": [
                {"kind": "gas", "amount_native": "0.001", "symbol": "ETH", "decimals": 18,
                 "usd_valuation": "2.50", "valuation_timestamp": "2026-11-09T08:00:00+00:00", "valuation_source": "coinpaprika"},
                {"kind": "reward", "amount_native": "100", "symbol": "LINEA", "decimals": 18,
                 "usd_valuation": "12.00", "valuation_timestamp": "2026-11-09T08:00:00+00:00", "valuation_source": "user_input"},
            ],
        }
        first = client.post(
            "/api/community/projects/linea/contributions",
            headers=headers,
            json=payload,
        )
        assert first.status_code == 200
        assert first.json()["total_gas_cost_usd"] == 2.5
        assert first.json()["total_actual_reward_usd"] == 12.0
        assert first.json()["data_quality"] == "user_confirmed"
        assert first.json()["verification_tier"] == "amount_verified"
        assert first.json()["gas_by_verification_tier"]["amount_verified"] == 2.5
        assert "total_interactions" not in first.json()
        assert "contribution_count" not in first.json()

        second = client.post(
            "/api/community/projects/linea/contributions",
            headers=headers,
            json=payload,
        )
        assert second.status_code == 200
        assert second.json()["total_gas_cost_usd"] == 2.5
        assert second.json()["total_actual_reward_usd"] == 12.0

        conflicting_payload = {**payload, "actual_reward_usd": "1200.00"}
        conflict = client.post(
            "/api/community/projects/linea/contributions",
            headers=headers,
            json=conflicting_payload,
        )
        assert conflict.status_code == 422
        assert conflict.json()["detail"] == "奖励代币明细估值合计与本笔实际收益不一致"

        public = client.get("/api/community/projects/linea/stats")
        assert public.status_code == 200
        assert "total_interactions" not in public.json()
        assert "contribution_count" not in public.json()


def test_guest_can_contribute_to_public_stats_idempotently():
    with TestClient(app) as client:
        payload = {
            "event_id": str(uuid4()),
            "network": "ethereum",
            "wallet_address": "0x1234567890abcdef1234567890abcdef12345678",
            "tx_hash": "0x" + "b" * 64,
            "public_contribution_opt_in": True,
            "gas_cost_usd": "0.75",
            "actual_reward_usd": "3.25",
        }
        response = client.post("/api/community/projects/scroll/contributions", json=payload)
        assert response.status_code == 200
        assert response.json()["total_gas_cost_usd"] == 0.75
        assert response.json()["total_actual_reward_usd"] == 3.25

        retry = client.post("/api/community/projects/scroll/contributions", json=payload)
        assert retry.status_code == 200
        assert retry.json()["total_gas_cost_usd"] == 0.75
        assert retry.json()["total_actual_reward_usd"] == 3.25

        public = client.get("/api/community/projects/scroll/stats")
        assert public.status_code == 200
        assert "total_interactions" not in public.json()
        assert "contribution_count" not in public.json()


def test_authenticated_public_contribution_does_not_persist_account_identity():
    event_id = uuid4()
    captured = {}

    class FakeResult:
        def scalar_one_or_none(self):
            return event_id

        def all(self):
            return []

        def one(self):
            return type("RatingStats", (), {"average": None, "count": 0})()

    class FakeDatabase:
        async def execute(self, statement):
            if not captured:
                captured.update(statement.compile().params)
            return FakeResult()

        async def commit(self):
            return None

        async def get(self, model, key):
            assert model is CommunityProjectStats
            return None

    fake_db = FakeDatabase()
    fake_user = User(
        id=uuid4(),
        username="testuser",
        uid="123456",
        email=None,
        password_hash=None,
        display_name="Test User",
    )

    async def override_db():
        yield fake_db

    async def override_user():
        return fake_user

    app.dependency_overrides[get_db] = override_db
    app.dependency_overrides[get_optional_current_user] = override_user
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/community/projects/anonymous-test/contributions",
                json={
                    "event_id": str(event_id),
                    "network": "ethereum",
                    "wallet_address": "0x1234567890abcdef1234567890abcdef12345678",
                    "tx_hash": "0x" + "c" * 64,
                    "public_contribution_opt_in": True,
                    "gas_cost_usd": "1.00",
                    "actual_reward_usd": "4.00",
                },
            )
        assert response.status_code == 200
        assert captured["user_id"] is None
        assert "tx_hash" not in captured
        assert "wallet_address" not in captured
        assert "tx_fingerprint" in captured
    finally:
        app.dependency_overrides.pop(get_db, None)
        app.dependency_overrides.pop(get_optional_current_user, None)


def test_global_rating_requires_verified_participation_and_upserts_per_account():
    with TestClient(app) as client:
        headers = _headers(client, "rating")
        rejected = client.post(
            "/api/community/projects/linea/ratings",
            headers=headers,
            json={"score": 5},
        )
        assert rejected.status_code == 403

        ledger_payload = {
            "client_record_id": "rating-participation",
            "project_id": "community:linea",
            "project_name": "Linea",
            "chain": "ethereum",
            "wallet_address": "0x1234567890abcdef",
            "tx_hash": "0xconfirmed",
            "interaction_method": "web",
            "expected_gas_cost_usd": "1.00",
            "expected_reward_usd": None,
            "actual_gas_cost_usd": "1.00",
            "actual_reward_usd": "2.00",
            "occurred_at": "2026-11-09T01:00:00+00:00",
            "extra": {"project_official_id": "linea", "is_demo": "false"},
        }
        saved = client.put(
            "/api/personal-ledger/rating-participation",
            headers=headers,
            json=ledger_payload,
        )
        assert saved.status_code == 200

        first = client.post(
            "/api/community/projects/linea/ratings",
            headers=headers,
            json={"score": 5},
        )
        assert first.status_code == 200
        assert first.json()["average_rating"] == 5.0
        assert first.json()["rating_count"] == 1

        updated = client.post(
            "/api/community/projects/linea/ratings",
            headers=headers,
            json={"score": 4},
        )
        assert updated.status_code == 200
        assert updated.json()["average_rating"] == 4.0
        assert updated.json()["rating_count"] == 1

        public = client.get("/api/community/projects/linea/stats")
        assert public.status_code == 200
        assert public.json()["average_rating"] == 4.0
        assert public.json()["rating_count"] == 1
        assert "user_id" not in public.json()



def test_same_transaction_hash_cannot_be_reused_for_another_event():
    with TestClient(app) as client:
        first_payload = {
            "event_id": str(uuid4()),
            "network": "ethereum",
            "wallet_address": "0x1234567890abcdef1234567890abcdef12345678",
            "tx_hash": "0x" + "d" * 64,
            "public_contribution_opt_in": True,
            "gas_cost_usd": "1.00",
            "actual_reward_usd": "2.00",
        }
        first = client.post("/api/community/projects/replay-test/contributions", json=first_payload)
        assert first.status_code == 200
        second_payload = {**first_payload, "event_id": str(uuid4())}
        second = client.post("/api/community/projects/replay-test/contributions", json=second_payload)
        assert second.status_code == 409


def test_community_contribution_requires_transaction_link_fields():
    with TestClient(app) as client:
        response = client.post(
            "/api/community/projects/missing-proof/contributions",
            json={"event_id": str(uuid4()), "gas_cost_usd": "1.00", "actual_reward_usd": "2.00"},
        )
        assert response.status_code == 422



def test_public_contribution_requires_explicit_opt_in():
    with TestClient(app) as client:
        response = client.post(
            "/api/community/projects/consent-test/contributions",
            json={
                "event_id": str(uuid4()),
                "network": "ethereum",
                "wallet_address": "0x1234567890abcdef1234567890abcdef12345678",
                "tx_hash": "0x" + "9" * 64,
                "public_contribution_opt_in": False,
                "gas_cost_usd": "1.00",
                "actual_reward_usd": "2.00",
            },
        )
        assert response.status_code == 403
