from datetime import datetime, timezone
from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app

def auth(client):
    username = f"ledger{uuid4().hex[:12]}"
    response = client.post("/api/auth/register", json={"username": username, "password": "strong-password-123"})
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}

def ledger_payload(record_id="local-record-1", **overrides):
    payload = {
        "client_record_id": record_id,
        "project_id": "linea",
        "project_name": "Linea",
        "chain": "EVM",
        "wallet_address": "0x" + "a" * 40,
        "tx_hash": "0x" + "b" * 64,
        "interaction_method": "swap",
        "expected_gas_cost_usd": "1.25",
        "expected_reward_usd": "8.00",
        "actual_gas_cost_usd": "1.30",
        "actual_reward_usd": "7.50",
        "occurred_at": datetime.now(timezone.utc).isoformat(),
        "extra": {"source": "manual-confirmation"},
    }
    payload.update(overrides)
    return payload

def test_personal_ledger_requires_login_and_is_account_scoped():
    with TestClient(app) as client:
        assert client.get("/api/personal-ledger").status_code == 401
        alice, bob = auth(client), auth(client)
        saved = client.put("/api/personal-ledger/local-record-1", headers=alice, json=ledger_payload())
        assert saved.status_code == 200
        row = saved.json()
        assert row["wallet_address"] == "0x" + "a" * 40
        assert row["actual_gas_cost_usd"] == "1.30000000"
        assert row["expected_reward_usd"] == "8.00000000"
        assert len(client.get("/api/personal-ledger", headers=alice).json()) == 1
        assert client.get("/api/personal-ledger", headers=bob).json() == []
        assert client.delete("/api/personal-ledger/local-record-1", headers=bob).status_code == 404
        assert client.delete("/api/personal-ledger/local-record-1", headers=alice).status_code == 204
        assert client.get("/api/personal-ledger", headers=alice).json() == []

def test_personal_ledger_upsert_is_idempotent_per_account_record_id():
    with TestClient(app) as client:
        headers = auth(client)
        first = client.put("/api/personal-ledger/same-id", headers=headers, json=ledger_payload("same-id"))
        updated = client.put("/api/personal-ledger/same-id", headers=headers, json=ledger_payload(
            "same-id", actual_reward_usd="12.75"
        ))
        assert first.status_code == updated.status_code == 200
        assert first.json()["id"] == updated.json()["id"]
        assert updated.json()["actual_reward_usd"] == "12.75000000"
        assert len(client.get("/api/personal-ledger", headers=headers).json()) == 1

def test_personal_ledger_rejects_naive_timestamp_and_mismatched_record_id():
    with TestClient(app) as client:
        headers = auth(client)
        naive = ledger_payload(occurred_at="2026-11-08T12:00:00")
        assert client.put("/api/personal-ledger/local-record-1", headers=headers, json=naive).status_code == 422
        assert client.put("/api/personal-ledger/different-id", headers=headers, json=ledger_payload()).status_code == 422


def test_personal_ledger_offset_pagination_keeps_equal_timestamp_rows():
    with TestClient(app) as client:
        headers = auth(client)
        timestamp = datetime(2026, 11, 8, 12, 0, tzinfo=timezone.utc).isoformat()
        for record_id in ("same-time-a", "same-time-b", "same-time-c"):
            saved = client.put(
                f"/api/personal-ledger/{record_id}",
                headers=headers,
                json=ledger_payload(record_id, occurred_at=timestamp),
            )
            assert saved.status_code == 200

        first_page = client.get("/api/personal-ledger?limit=2&offset=0", headers=headers)
        second_page = client.get("/api/personal-ledger?limit=2&offset=2", headers=headers)
        assert first_page.status_code == second_page.status_code == 200
        returned_ids = {
            row["client_record_id"]
            for row in first_page.json() + second_page.json()
        }
        assert returned_ids == {"same-time-a", "same-time-b", "same-time-c"}



def test_personal_ledger_preserves_unknown_expected_reward_as_null():
    with TestClient(app) as client:
        headers = auth(client)
        payload = ledger_payload("unknown-reward")
        payload["expected_reward_usd"] = None
        saved = client.put(
            "/api/personal-ledger/unknown-reward",
            headers=headers,
            json=payload,
        )
        assert saved.status_code == 200
        assert saved.json()["expected_reward_usd"] is None
        restored = client.get("/api/personal-ledger", headers=headers)
        assert restored.status_code == 200
        assert restored.json()[0]["expected_reward_usd"] is None


def test_user_stats_aggregate_confirmed_ledger_gas_rewards_and_win_rate():
    with TestClient(app) as client:
        headers = auth(client)
        entries = [
            ("stats-linea", "linea", "5.00", "10.00", "2026-11-08T10:00:00+00:00"),
            ("stats-scroll", "scroll", "4.00", "1.00", "2026-11-08T11:00:00+00:00"),
            ("stats-base", "base", "2.00", "2.00", "2026-11-09T09:00:00+00:00"),
        ]
        for record_id, project_id, gas, reward, occurred_at in entries:
            payload = ledger_payload(record_id, project_id=project_id, actual_gas_cost_usd=gas,
                                     actual_reward_usd=reward, occurred_at=occurred_at)
            saved = client.put(f"/api/personal-ledger/{record_id}", headers=headers, json=payload)
            assert saved.status_code == 200
        result = client.get("/api/user/stats", headers=headers).json()
        assert result["ledger_project_count"] == 3
        assert result["ledger_total_gas_cost"] == 11.0
        assert result["ledger_total_actual_reward"] == 13.0
        assert result["ledger_net_profit"] == 2.0
        assert result["profitable_projects"] == 1
        assert result["evaluated_projects"] == 3
        assert abs(result["win_rate"] - 33.3333333333) < 0.001
        assert result["ledger_interaction_days"] == 2
        assert result["total_interactions"] == 3


def test_user_stats_excludes_incomplete_reward_valuation_from_cloud_totals():
    with TestClient(app) as client:
        headers = auth(client)
        entries = [
            ("complete-linea", "linea", "5.00", "10.00",
             {"reward_kind": "USD", "reward_usd_valuation": "10.00"}),
            ("unknown-scroll", "scroll", "4.00", "0.00",
             {"reward_kind": "UNKNOWN", "reward_usd_valuation": "", "usd_profit_complete": "false"}),
            ("points-base", "base", "3.00", "0.00",
             {"reward_kind": "POINTS", "reward_amount": "100", "reward_unit": "POINTS"}),
            ("unpriced-token", "optimism", "1.00", "0.00",
             {"reward_kind": "TOKEN", "reward_amount": "50", "reward_unit": "ABC",
              "reward_usd_valuation": ""}),
            ("complete-zksync", "zksync", "2.00", "2.00",
             {"reward_kind": "MANUAL_USD_VALUATION", "reward_usd_valuation": "2.00"}),
        ]
        for record_id, project_id, gas, reward, extra in entries:
            payload = ledger_payload(
                record_id, project_id=project_id, actual_gas_cost_usd=gas,
                actual_reward_usd=reward, extra=extra
            )
            saved = client.put(f"/api/personal-ledger/{record_id}", headers=headers, json=payload)
            assert saved.status_code == 200

        result = client.get("/api/user/stats", headers=headers).json()
        assert result["ledger_project_count"] == 5
        assert result["ledger_total_gas_cost"] == 15.0
        assert result["ledger_total_actual_reward"] == 12.0
        assert result["ledger_net_profit"] == 5.0
        assert result["profitable_projects"] == 1
        assert result["evaluated_projects"] == 2
        assert result["win_rate"] == 50.0
        assert result["usd_profit_complete"] is False
        assert result["total_interactions"] == 5


def test_user_stats_accepts_token_reward_only_with_persisted_usd_valuation():
    with TestClient(app) as client:
        headers = auth(client)
        payload = ledger_payload(
            "priced-token", project_id="linea", actual_gas_cost_usd="1.00",
            actual_reward_usd="2.50",
            extra={"reward_kind": "TOKEN", "reward_amount": "10", "reward_unit": "ABC",
                   "reward_usd_valuation": "2.50", "usd_profit_complete": "true"}
        )
        saved = client.put("/api/personal-ledger/priced-token", headers=headers, json=payload)
        assert saved.status_code == 200
        result = client.get("/api/user/stats", headers=headers).json()
        assert result["ledger_total_actual_reward"] == 2.5
        assert result["ledger_net_profit"] == 1.5
        assert result["evaluated_projects"] == 1
        assert result["usd_profit_complete"] is True


def test_personal_ledger_preserves_unknown_expected_gas_as_null_and_explicit_zero():
    with TestClient(app) as client:
        headers = auth(client)
        unknown = ledger_payload("unknown-gas", expected_gas_cost_usd=None)
        saved_unknown = client.put("/api/personal-ledger/unknown-gas", headers=headers, json=unknown)
        assert saved_unknown.status_code == 200
        assert saved_unknown.json()["expected_gas_cost_usd"] is None
        restored = client.get("/api/personal-ledger", headers=headers)
        assert restored.status_code == 200
        assert next(row for row in restored.json() if row["client_record_id"] == "unknown-gas")["expected_gas_cost_usd"] is None

        explicit_zero = ledger_payload("known-zero-gas", expected_gas_cost_usd="0")
        saved_zero = client.put("/api/personal-ledger/known-zero-gas", headers=headers, json=explicit_zero)
        assert saved_zero.status_code == 200
        assert float(saved_zero.json()["expected_gas_cost_usd"]) == 0.0



def test_personal_ledger_rejects_secret_material_and_oversized_extra():
    with TestClient(app) as client:
        headers = auth(client)
        secret_payload = ledger_payload(
            "secret-extra",
            extra={"source": "manual", "nested": {"seed_phrase": "word1 word2 word3"}},
        )
        secret = client.put(
            "/api/personal-ledger/secret-extra",
            headers=headers,
            json=secret_payload,
        )
        assert secret.status_code == 422

        private_key_payload = ledger_payload(
            "private-key-extra",
            extra={"privateKey": "0x" + "a" * 64},
        )
        private_key = client.put(
            "/api/personal-ledger/private-key-extra",
            headers=headers,
            json=private_key_payload,
        )
        assert private_key.status_code == 422

        oversized_payload = ledger_payload(
            "oversized-extra",
            extra={"note": "x" * 17_000},
        )
        oversized = client.put(
            "/api/personal-ledger/oversized-extra",
            headers=headers,
            json=oversized_payload,
        )
        assert oversized.status_code == 422
