from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app


def auth(client):
    username = f"assign{uuid4().hex[:12]}"
    response = client.post("/api/auth/register", json={"username": username, "password": "strong-password-123"})
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def add_wallet(client, headers, suffix):
    response = client.post("/api/wallets", headers=headers, json={
        "wallet_name": f"Wallet {suffix}",
        "chain": "EVM",
        "address": "0x" + suffix * 40,
    })
    assert response.status_code == 201
    return response.json()["id"]


def test_project_wallet_assignments_are_persistent_and_account_scoped():
    with TestClient(app) as client:
        alice, bob = auth(client), auth(client)
        project = client.post("/api/projects", headers=alice, json={"name": "Linea"}).json()
        wallet_a = add_wallet(client, alice, "a")
        wallet_b = add_wallet(client, alice, "b")
        wallet_bob = add_wallet(client, bob, "c")
        endpoint = f"/api/project-wallet-assignments/{project['id']}"

        assert client.get(endpoint, headers=alice).json()["wallet_ids"] == []
        saved = client.put(endpoint, headers=alice, json={"wallet_ids": [wallet_a, wallet_b]})
        assert saved.status_code == 200
        assert set(saved.json()["wallet_ids"]) == {wallet_a, wallet_b}
        assert set(client.get(endpoint, headers=alice).json()["wallet_ids"]) == {wallet_a, wallet_b}
        assert client.get(endpoint, headers=bob).status_code == 404

        assert client.put(endpoint, headers=alice, json={"wallet_ids": [wallet_bob]}).status_code == 404
        assert client.put(endpoint, headers=alice, json={"wallet_ids": [wallet_a, wallet_a]}).status_code == 422

        replaced = client.put(endpoint, headers=alice, json={"wallet_ids": [wallet_b]})
        assert replaced.status_code == 200
        assert replaced.json()["wallet_ids"] == [wallet_b]

        empty = client.put(endpoint, headers=alice, json={"wallet_ids": []})
        assert empty.status_code == 200
        assert empty.json()["wallet_ids"] == []


def test_community_project_wallet_assignments_support_account_private_tracking():
    with TestClient(app) as client:
        headers = auth(client)
        wallet_id = add_wallet(client, headers, "d")
        endpoint = "/api/project-wallet-assignments/community:linea"
        saved = client.put(endpoint, headers=headers, json={"wallet_ids": [wallet_id]})
        assert saved.status_code == 200
        assert client.get(endpoint, headers=headers).json()["wallet_ids"] == [wallet_id]
        assert client.get("/api/project-wallet-assignments/community:unknown!", headers=headers).status_code == 422


def test_project_wallet_assignments_require_authentication_and_owned_project():
    with TestClient(app) as client:
        assert client.get("/api/project-wallet-assignments/community:linea").status_code == 401
        alice, bob = auth(client), auth(client)
        project = client.post("/api/projects", headers=alice, json={"name": "Private"}).json()
        assert client.get(f"/api/project-wallet-assignments/{project['id']}", headers=bob).status_code == 404
