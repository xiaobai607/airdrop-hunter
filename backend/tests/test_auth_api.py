from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app

def test_project_routes_require_auth_and_isolate_users() -> None:
    with TestClient(app) as client:
        suffix = uuid4().hex
        alice_email = f"alice-{suffix}@example.com"
        bob_email = f"bob-{suffix}@example.com"
        client.post("/api/auth/send-code", json={"email": alice_email})
        client.post("/api/auth/send-code", json={"email": bob_email})
        alice = client.post("/api/auth/register", json={"email": alice_email, "password": "alice-password-123", "verification_code": "123456"})
        bob = client.post("/api/auth/register", json={"email": bob_email, "password": "bob-password-123", "verification_code": "123456"})
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
        client.post("/api/auth/send-code", json={"email": email})
        created = client.post("/api/auth/register", json={"email": email, "password": "right-password-123", "verification_code": "123456"})
        assert created.status_code == 201
        bad = client.post("/api/auth/login", json={"email": email, "password": "wrong-password-123"})
        assert bad.status_code == 401


def test_registration_requires_mock_verification_code() -> None:
    with TestClient(app) as client:
        email = f"verify-{uuid4().hex}@example.com"
        assert client.post("/api/auth/register", json={"email": email, "password": "strong-password-123", "verification_code": "123456"}).status_code == 400
        sent = client.post("/api/auth/send-code", json={"email": email})
        assert sent.status_code == 200
        assert sent.json()["mock_code"] == "123456"
        assert client.post("/api/auth/register", json={"email": email, "password": "strong-password-123", "verification_code": "000000"}).status_code == 400
        assert client.post("/api/auth/register", json={"email": email, "password": "strong-password-123", "verification_code": "123456"}).status_code == 201
        assert client.post("/api/auth/send-code", json={"email": email}).status_code == 429


def test_dev_test_identity_is_disabled_by_default_and_opt_in(monkeypatch) -> None:
    monkeypatch.delenv("ENABLE_DEV_TEST_AUTH", raising=False)
    with TestClient(app) as client:
        assert client.get("/api/projects").status_code == 401
        monkeypatch.setenv("ENABLE_DEV_TEST_AUTH", "true")
        response = client.get("/api/projects")
        assert response.status_code == 200
        assert isinstance(response.json(), list)
        assert client.get("/api/projects", headers={"Authorization": "Bearer dev-test-token"}).status_code == 200
