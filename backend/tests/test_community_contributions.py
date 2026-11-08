from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import app


def _headers(client, prefix: str):
    username = f"{prefix}{uuid4().hex[:12]}"
    response = client.post("/api/auth/register", json={
        "username": username, "password": "strong-password-123"
    })
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_community_contribution_is_idempotent_and_public_stats_are_aggregated():
    with TestClient(app) as client:
        headers = _headers(client, "contrib")
        event_id = str(uuid4())
        payload = {
            "event_id": event_id,
            "gas_cost_usd": "2.50",
            "actual_reward_usd": "12.00",
            "interaction_count": 1,
        }
        first = client.post(
            "/api/community/projects/linea/contributions",
            headers=headers,
            json=payload,
        )
        assert first.status_code == 200
        assert first.json()["total_gas_cost_usd"] == 2.5
        assert first.json()["total_actual_reward_usd"] == 12.0
        assert first.json()["total_interactions"] == 1
        assert first.json()["data_quality"] == "user_confirmed"

        # Retrying the same outbox event must not increase totals a second time.
        second = client.post(
            "/api/community/projects/linea/contributions",
            headers=headers,
            json=payload,
        )
        assert second.status_code == 200
        assert second.json()["total_gas_cost_usd"] == 2.5
        assert second.json()["total_actual_reward_usd"] == 12.0
        assert second.json()["total_interactions"] == 1

        public = client.get("/api/community/projects/linea/stats")
        assert public.status_code == 200
        assert public.json()["contribution_count"] == 1


def test_guest_can_contribute_without_exposing_wallet_identity():
    with TestClient(app) as client:
        payload = {
            "event_id": str(uuid4()),
            "gas_cost_usd": "0.75",
            "actual_reward_usd": "3.25",
            "interaction_count": 1,
        }
        response = client.post("/api/community/projects/scroll/contributions", json=payload)
        assert response.status_code == 200
        assert response.json()["total_gas_cost_usd"] == 0.75
        assert response.json()["total_actual_reward_usd"] == 3.25
