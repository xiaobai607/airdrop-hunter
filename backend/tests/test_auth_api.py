from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app, limiter


def test_username_registration_login_and_private_data_isolation() -> None:
    with TestClient(app) as client:
        suffix = uuid4().hex[:12]
        alice_name = f"alice{suffix}"
        bob_name = f"bob{suffix}"
        alice = client.post("/api/auth/register", json={"username": alice_name, "password": "alice-password-123"})
        bob = client.post("/api/auth/register", json={"username": bob_name, "password": "bob-password-123"})
        assert alice.status_code == 201 and bob.status_code == 201
        assert len(alice.json()["uid"]) == 6 and alice.json()["uid"].isdigit()
        assert alice.json()["username"] == alice_name
        assert alice.json()["access_token"]

        duplicate = client.post("/api/auth/register", json={"username": alice_name.upper(), "password": "another-password-123"})
        assert duplicate.status_code == 400
        assert duplicate.json()["detail"] == "该账号已存在"

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
        username = f"login{uuid4().hex[:12]}"
        assert client.post("/api/auth/register", json={"username": username, "password": "right-password-123"}).status_code == 201
        bad = client.post("/api/auth/login", json={"username": username, "password": "wrong-password-123"})
        assert bad.status_code == 401


def test_registration_validates_username_and_password() -> None:
    with TestClient(app) as client:
        short_username = client.post("/api/auth/register", json={"username": "abc", "password": "strong-password-123"})
        invalid_username = client.post("/api/auth/register", json={"username": "bad_name", "password": "strong-password-123"})
        short_password = client.post("/api/auth/register", json={"username": "validname", "password": "short"})
        long_password = client.post("/api/auth/register", json={"username": "validname2", "password": "p" * 31})
        assert short_username.status_code == 400
        assert short_username.json()["detail"] == "账号只能由4到20位英文字母和数字组成"
        assert invalid_username.status_code == 400
        assert short_password.status_code == 400
        assert short_password.json()["detail"] == "密码长度必须为8到30位"
        assert long_password.status_code == 400


def test_register_rate_limit_three_per_minute(monkeypatch) -> None:
    limiter.enabled = True
    try:
        with TestClient(app, client=("203.0.113.41", 12345)) as client:
            statuses = []
            for index in range(4):
                response = client.post("/api/auth/register", json={
                    "username": f"ratetest{index:02d}",
                    "password": "rate-limit-test-password"
                })
                statuses.append(response.status_code)
            assert statuses == [201, 201, 201, 429]
            assert "detail" in response.json() or "error" in response.json()
    finally:
        limiter.enabled = False
        limiter.reset()
