from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import app


def _register(client: TestClient, username: str) -> dict[str, str]:
    response = client.post(
        "/api/auth/register",
        json={"username": username, "password": "secure-test-password-123"},
    )
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def _airdrop_payload(name: str) -> dict:
    return {
        "name": name,
        "description": "Private user-created listing",
        "official_url": "https://example.org/project",
        "source_url": "https://example.org/source",
        "source_name": "manual test",
        "source_type": "manual",
    }


def test_airdrop_writes_require_auth_and_are_user_scoped():
    with TestClient(app) as client:
        assert client.get("/api/airdrops").status_code == 200
        assert client.post("/api/airdrops", json=_airdrop_payload("Guest attempt")).status_code == 401

        suffix = uuid4().hex[:10]
        alice = _register(client, f"alice{suffix}")
        bob = _register(client, f"bobby{suffix}")

        created = client.post(
            "/api/airdrops", headers=alice,
            json=_airdrop_payload("Alice private listing"),
        )
        assert created.status_code == 201
        airdrop_id = created.json()["id"]

        public_rows = client.get("/api/airdrops").json()
        assert not any(row["id"] == airdrop_id for row in public_rows)

        alice_rows = client.get("/api/airdrops", headers=alice).json()
        bob_rows = client.get("/api/airdrops", headers=bob).json()
        assert any(row["id"] == airdrop_id for row in alice_rows)
        assert not any(row["id"] == airdrop_id for row in bob_rows)

        assert client.put(f"/api/airdrops/{airdrop_id}", json=_airdrop_payload("Guest overwrite")).status_code == 401
        assert client.delete(f"/api/airdrops/{airdrop_id}").status_code == 401

        update_payload = _airdrop_payload("Bob tried to overwrite")
        assert client.put(f"/api/airdrops/{airdrop_id}", headers=bob, json=update_payload).status_code == 404
        assert client.delete(f"/api/airdrops/{airdrop_id}", headers=bob).status_code == 404

        updated = client.put(
            f"/api/airdrops/{airdrop_id}", headers=alice,
            json=_airdrop_payload("Alice updated listing"),
        )
        assert updated.status_code == 200
        assert updated.json()["name"] == "Alice updated listing"
        assert client.delete(f"/api/airdrops/{airdrop_id}", headers=alice).status_code == 204


def test_airdrop_rejects_unsafe_links():
    with TestClient(app) as client:
        auth = _register(client, f"safe{uuid4().hex[:10]}")
        payload = _airdrop_payload("Unsafe link")
        payload["official_url"] = "javascript:alert(1)"
        response = client.post("/api/airdrops", headers=auth, json=payload)
        assert response.status_code == 422


def test_api_responses_include_security_headers():
    with TestClient(app) as client:
        response = client.get("/health")
        assert response.status_code == 200
        assert response.headers["X-Content-Type-Options"] == "nosniff"
        assert response.headers["X-Frame-Options"] == "DENY"
        assert response.headers["Referrer-Policy"] == "no-referrer"
        assert response.headers["Cache-Control"] == "no-store"


def test_login_rate_limit_five_per_minute():
    from app.main import limiter

    limiter.enabled = True
    try:
        with TestClient(app, client=("203.0.113.42", 12345)) as client:
            username = f"ratelogin{uuid4().hex[:8]}"
            register = client.post(
                "/api/auth/register",
                json={"username": username, "password": "secure-test-password-123"},
            )
            assert register.status_code == 201
            responses = [
                client.post(
                    "/api/auth/login",
                    json={"username": username, "password": "wrong-password-123"},
                )
                for _ in range(6)
            ]
            assert [response.status_code for response in responses] == [401, 401, 401, 401, 401, 429]
    finally:
        limiter.enabled = False
        limiter.reset()