from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app

def make_auth(client, prefix):
    r=client.post("/api/auth/register",json={"email":f"{prefix}-{uuid4().hex}@example.com","password":"strong-password-123"})
    assert r.status_code==201
    return {"Authorization":f"Bearer {r.json()['access_token']}"}

def test_project_task_stats_are_user_scoped():
    with TestClient(app) as client:
        alice,bob=make_auth(client,"alice"),make_auth(client,"bob")
        r=client.post("/api/projects",headers=alice,json={"name":"Alpha","status":"tracking","official_url":"https://example.org","twitter_url":"https://x.com/example","expected_income":120,"gas_cost":5,"actual_income":90})
        assert r.status_code==201
        project=r.json()
        assert project["official_url"]=="https://example.org"
        pid=project["id"]
        task=client.post(f"/api/projects/{pid}/tasks",headers=alice,json={"task_name":"Connect wallet"})
        assert task.status_code==201
        tid=task.json()["id"]
        assert client.get(f"/api/projects/{pid}/tasks",headers=bob).status_code==404
        assert client.patch(f"/api/projects/{pid}/tasks/{tid}",headers=bob,json={"is_completed":True}).status_code==404
        assert client.patch(f"/api/projects/{pid}/tasks/{tid}",headers=alice,json={"is_completed":True}).json()["is_completed"] is True
        stats=client.get("/api/stats",headers=alice).json()
        assert stats["total_actual_income"]==90 and stats["total_gas_cost"]==5
        assert client.get("/api/stats",headers=bob).json()["project_count"]==0

def test_wallet_endpoint_returns_simulation_notice():
    with TestClient(app) as client:
        r=client.get("/api/wallet/check",params={"address":"11111111111111111111111111111111"})
        assert r.status_code==200
        assert r.json()["status"]=="simulated"
        assert "不代表真实" in r.json()["notice"]
