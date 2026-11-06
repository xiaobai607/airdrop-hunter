from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app

def test_project_routes_require_auth_and_isolate_users() -> None:
    with TestClient(app) as client:
        suffix = uuid4().hex
        alice = client.post("/api/auth/register", json={"email": f"alice-{suffix}@example.com", "password": "alice-password-123"})
        bob = client.post("/api/auth/register", json={"email": f"bob-{suffix}@example.com", "password": "bob-password-123"})
        assert alice.status_code == 201 and bob.status_code == 201
        ah = {"Authorization": f"Bearer {alice.json()['access_token']}"}
        bh = {"Authorization": f"Bearer {bob.json()['access_token']}"}
        assert client.get("/api/projects").status_code == 401
        created = client.post("/api/projects", headers=ah, json={"name": "Alice private project", "description": "private", "status": "tracking"})
        assert created.status_code == 201
        pid = created.json()["id"]
        assert client.get("/api/projects", headers=bh).json() == []
        assert client.put(f"/api/projects/{pid}", headers=bh, json={"name": "stolen", "description": "", "status": "upcoming"}).status_code == 404
        assert client.delete(f"/api/projects/{pid}", headers=bh).status_code == 404
        owned = client.get("/api/projects", headers=ah).json()
        assert len(owned) == 1 and owned[0]["name"] == "Alice private project"

def test_login_rejects_wrong_password() -> None:
    with TestClient(app) as client:
        email = f"login-{uuid4().hex}@example.com"
        created = client.post("/api/auth/register", json={"email": email, "password": "right-password-123"})
        assert created.status_code == 201
        bad = client.post("/api/auth/login", json={"email": email, "password": "wrong-password-123"})
        assert bad.status_code == 401
