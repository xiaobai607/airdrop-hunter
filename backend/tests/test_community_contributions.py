from uuid import uuid4

from fastapi.testclient import TestClient

from app.database import get_db
from app.main import app
from app.models import CommunityProjectStats, User
from app.routers.auth import get_optional_current_user


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

        second = client.post(
            "/api/community/projects/linea/contributions",
            headers=headers,
            json=payload,
        )
        assert second.status_code == 200
        assert second.json()["total_gas_cost_usd"] == 2.5
        assert second.json()["total_actual_reward_usd"] == 12.0
        assert second.json()["total_interactions"] == 1

        conflicting_payload = {**payload, "actual_reward_usd": "1200.00"}
        conflict = client.post(
            "/api/community/projects/linea/contributions",
            headers=headers,
            json=conflicting_payload,
        )
        assert conflict.status_code == 409

        public = client.get("/api/community/projects/linea/stats")
        assert public.status_code == 200
        assert public.json()["contribution_count"] == 1


def test_guest_can_contribute_to_public_stats_idempotently():
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
        assert response.json()["total_interactions"] == 1
        assert response.json()["contribution_count"] == 1

        retry = client.post("/api/community/projects/scroll/contributions", json=payload)
        assert retry.status_code == 200
        assert retry.json()["total_gas_cost_usd"] == 0.75
        assert retry.json()["total_actual_reward_usd"] == 3.25
        assert retry.json()["total_interactions"] == 1
        assert retry.json()["contribution_count"] == 1

        public = client.get("/api/community/projects/scroll/stats")
        assert public.status_code == 200
        assert public.json()["contribution_count"] == 1


def test_authenticated_public_contribution_does_not_persist_account_identity():
    event_id = uuid4()
    captured = {}

    class FakeResult:
        def scalar_one_or_none(self):
            return event_id

    class FakeDatabase:
        async def execute(self, statement):
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
                    "gas_cost_usd": "1.00",
                    "actual_reward_usd": "4.00",
                    "interaction_count": 1,
                },
            )
        assert response.status_code == 200
        assert captured["user_id"] is None
    finally:
        app.dependency_overrides.pop(get_db, None)
        app.dependency_overrides.pop(get_optional_current_user, None)
