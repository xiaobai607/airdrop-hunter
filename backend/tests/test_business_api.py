from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app


def make_auth(client, prefix):
    username = f"{prefix}_{uuid4().hex[:12]}"
    response = client.post("/api/auth/register", json={
        "username": username, "password": "strong-password-123"
    })
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_project_task_stats_are_user_scoped():
    with TestClient(app) as client:
        alice, bob = make_auth(client, "alice"), make_auth(client, "bob")
        r = client.post("/api/projects", headers=alice, json={
            "name": "Alpha", "status": "tracking", "official_url": "https://example.org",
            "twitter_url": "https://x.com/example", "expected_income": 120, "gas_cost": 5,
            "actual_income": 90
        })
        assert r.status_code == 201
        project = r.json()
        assert project["official_url"] == "https://example.org"
        pid = project["id"]
        task = client.post(f"/api/projects/{pid}/tasks", headers=alice, json={"task_name": "Connect wallet"})
        assert task.status_code == 201
        tid = task.json()["id"]
        assert client.get(f"/api/projects/{pid}/tasks", headers=bob).status_code == 404
        assert client.patch(f"/api/projects/{pid}/tasks/{tid}", headers=bob, json={"is_completed": True}).status_code == 404
        assert client.patch(f"/api/projects/{pid}/tasks/{tid}", headers=alice, json={"is_completed": True}).json()["is_completed"] is True
        stats = client.get("/api/stats", headers=alice).json()
        assert stats["total_actual_income"] == 90 and stats["total_gas_cost"] == 5
        assert client.get("/api/stats", headers=bob).json()["project_count"] == 0
        user_stats = client.get("/api/user/stats", headers=alice).json()
        assert user_stats["tracked_projects"] == 1
        assert user_stats["completed_projects"] == 0
        assert user_stats["total_actual_income"] == 90
        assert user_stats["interaction_days"] == 1
        assert client.get("/api/user/stats", headers=bob).json()["tracked_projects"] == 0


def test_wallet_check_rejects_invalid_address_without_fabricated_reward():
    with TestClient(app) as client:
        r = client.get("/api/wallet/check", params={"address": "not-a-wallet-address-12345"})
        assert r.status_code == 200
        assert r.json()["valid"] is False
        assert r.json()["message"] == "地址格式错误或不存在"
        assert r.json().get("estimated_reward_usd") is None
