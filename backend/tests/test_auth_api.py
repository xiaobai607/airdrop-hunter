from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app


def test_username_registration_login_and_private_data_isolation() -> None:
    with TestClient(app) as client:
        suffix = uuid4().hex[:12]
        alice_name = f"alice_{suffix}"
        bob_name = f"bob_{suffix}"
        alice = client.post("/api/auth/register", json={"username": alice_name, "password": "alice-password-123"})
        bob = client.post("/api/auth/register", json={"username": bob_name, "password": "bob-password-123"})
        assert alice.status_code == 201 and bob.status_code == 201
        assert len(alice.json()["uid"]) == 6 and alice.json()["uid"].isdigit()
        assert alice.json()["username"] == alice_name
        assert alice.json()["access_token"]

        duplicate = client.post("/api/auth/register", json={"username": alice_name.upper(), "password": "another-password-123"})
        assert duplicate.status_code == 400
        assert duplicate.json()["detail"] == "该账号已被占用"

        ah = {"Authorization": f"Bearer {alice.json()['access_token']}"}
        bh = {"Authorization": f"Bearer {bob.json()['access_token']}"}
        assert client.get("/api/projects").status_code == 401
        created = client.post("/api/projects", headers=ah, json={"name": "Alice private project", "description": "private", "status": "tracking"})
        assert created.status_code == 201
        pid = created.json()["id"]
        assert client.get("/api/projects", headers=bh).json() == []
        assert client.put(f"/api/projects/{pid}", headers=bh, json={"name": "stolen", "description": "", "status": "upcoming"}).status_code == 404
        assert client.delete(f"/api/projects/{pid}", headers=bh).status_code == 404
        assert client.get("/api/projects", headers=ah).json()[0]["name"] == "Alice private project"

        profile = client.get("/api/auth/me", headers=ah)
        assert profile.status_code == 200
        assert profile.json()["uid"] == alice.json()["uid"]


def test_login_rejects_wrong_password() -> None:
    with TestClient(app) as client:
        username = f"login_{uuid4().hex[:12]}"
        assert client.post("/api/auth/register", json={"username": username, "password": "right-password-123"}).status_code == 201
        bad = client.post("/api/auth/login", json={"username": username, "password": "wrong-password-123"})
        assert bad.status_code == 401


def test_registration_validates_username_and_password() -> None:
    with TestClient(app) as client:
        assert client.post("/api/auth/register", json={"username": "ab", "password": "strong-password-123"}).status_code == 422
        assert client.post("/api/auth/register", json={"username": "valid_user", "password": "short"}).status_code == 422
