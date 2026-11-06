from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app

def auth(client):
    email = f"wallet-{uuid4().hex}@example.com"
    client.post("/api/auth/send-code", json={"email": email})
    response = client.post("/api/auth/register", json={"email": email, "password": "strong-password-123", "verification_code": "123456"})
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}

def test_wallet_sync_is_authenticated_and_user_scoped():
    with TestClient(app) as client:
        alice, bob = auth(client), auth(client)
        address = "11111111111111111111111111111111"
        assert client.get("/api/wallet/sync", params={"public_key": address}).status_code == 401
        synced = client.get("/api/wallet/sync", params={"public_key": address}, headers=alice)
        assert synced.status_code == 200
        body = synced.json()
        assert body["status"] == "simulated"
        assert body["daily_gas_usd"] >= 0
        assert body["recent_airdrop_income_usd"] >= 0
        assert len(client.get("/api/wallet", headers=alice).json()) == 1
        assert client.get("/api/wallet", headers=bob).json() == []

def test_task_step_order_is_persisted_and_sorted():
    with TestClient(app) as client:
        headers = auth(client)
        project = client.post("/api/projects", headers=headers, json={"name": "Steps"}).json()
        pid = project["id"]
        second = client.post(f"/api/projects/{pid}/tasks", headers=headers, json={"task_name": "Second", "step_order": 2}).json()
        first = client.post(f"/api/projects/{pid}/tasks", headers=headers, json={"task_name": "First", "step_order": 1}).json()
        tasks = client.get(f"/api/projects/{pid}/tasks", headers=headers).json()
        assert [t["id"] for t in tasks] == [first["id"], second["id"]]
        assert [t["step_order"] for t in tasks] == [1, 2]
