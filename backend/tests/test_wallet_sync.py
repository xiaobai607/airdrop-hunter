from uuid import uuid4
from fastapi.testclient import TestClient
from app.main import app

def auth(client):
    username = f"wallet{uuid4().hex[:12]}"
    registered = client.post("/api/auth/register", json={"username": username, "password": "strong-password-123"})
    assert registered.status_code == 201
    return {"Authorization": f"Bearer {registered.json()['access_token']}"}

def test_wallet_crud_and_project_claim_url_are_user_scoped():
    with TestClient(app) as client:
        alice, bob = auth(client), auth(client)
        address = "0x" + "a" * 40
        assert client.get("/api/wallets", headers=alice).json() == []
        saved = client.post("/api/wallets", headers=alice, json={
            "wallet_name": "主号", "chain": "EVM", "address": address,
            "tags": ["主钱包", " 空投 ", "主钱包"], "notes": "  主要交互地址  "
        })
        assert saved.status_code == 201
        wallet = saved.json()
        assert wallet["wallet_name"] == "主号"
        assert wallet["chain"] == "EVM"
        assert wallet["ecosystem"] == "EVM"
        assert wallet["network_icon"] == "E"
        assert wallet["address"] == address
        assert wallet["tags"] == ["主钱包", "空投"]
        assert wallet["notes"] == "主要交互地址"
        assert client.get("/api/wallets", headers=bob).json() == []
        updated = client.put(f"/api/wallets/{wallet['id']}", headers=alice, json={
            "wallet_name": "备用主号", "chain": "EVM", "address": address,
            "tags": ["长期", "DeFi"], "notes": "更新后的备注"
        })
        assert updated.status_code == 200
        assert updated.json()["wallet_name"] == "备用主号"
        assert updated.json()["tags"] == ["长期", "DeFi"]
        assert updated.json()["notes"] == "更新后的备注"
        assert client.put(f"/api/wallets/{wallet['id']}", headers=bob, json={
            "wallet_name": "越权", "chain": "EVM", "address": address
        }).status_code == 404
        project_response = client.post("/api/projects", headers=alice, json={"name": "Linea", "status": "tracking", "claim_url": "https://claim.example/eligibility?campaign=alpha"})
        assert project_response.status_code == 201
        project = project_response.json()
        claim = client.get(f"/api/projects/{project['id']}/claim-url", headers=alice, params={"wallet_id": wallet["id"]})
        assert claim.status_code == 200
        assert claim.json()["url"] == f"https://claim.example/eligibility?campaign=alpha&address={address}"
        assert client.get(f"/api/projects/{project['id']}/claim-url", headers=bob, params={"wallet_id": wallet["id"]}).status_code == 404
        assert client.delete(f"/api/wallets/{wallet['id']}", headers=bob).status_code == 404
        assert client.delete(f"/api/wallets/{wallet['id']}", headers=alice).status_code == 204
        assert client.get("/api/wallets", headers=alice).json() == []

def test_wallet_creation_rejects_chain_address_mismatch():
    with TestClient(app) as client:
        headers = auth(client)
        response = client.post("/api/wallets", headers=headers, json={"wallet_name": "wrong", "chain": "TRON", "address": "0x" + "b" * 40})
        assert response.status_code == 422

def test_same_evm_address_is_saved_as_one_ecosystem_wallet():
    with TestClient(app) as client:
        headers = auth(client)
        address = "0x" + "c" * 40
        first = client.post("/api/wallets", headers=headers, json={"wallet_name": "main", "chain": "EVM", "address": address})
        second = client.post("/api/wallets", headers=headers, json={
            "wallet_name": "main-updated", "chain": "Ethereum", "address": "0x" + "C" * 40
        })
        assert first.status_code == 201 and second.status_code == 201
        assert first.json()["id"] == second.json()["id"]
        assert second.json()["address"] == address
        assert second.json()["wallet_name"] == "main-updated"
        assert len(client.get("/api/wallets", headers=headers).json()) == 1

def test_claim_url_requires_https_and_configured_project():
    with TestClient(app) as client:
        headers = auth(client)
        project = client.post("/api/projects", headers=headers, json={"name": "No claim", "claim_url": "http://unsafe.example"})
        assert project.status_code == 422

def test_task_step_order_is_persisted_and_sorted():
    with TestClient(app) as client:
        headers = auth(client)
        project_id = client.post("/api/projects", headers=headers, json={"name": "Task order"}).json()["id"]
        second = client.post(f"/api/projects/{project_id}/tasks", headers=headers, json={"task_name": "Second", "step_order": 2}).json()
        first = client.post(f"/api/projects/{project_id}/tasks", headers=headers, json={"task_name": "First", "step_order": 1}).json()
        tasks = client.get(f"/api/projects/{project_id}/tasks", headers=headers).json()
        assert [task["id"] for task in tasks] == [first["id"], second["id"]]
        assert [task["step_order"] for task in tasks] == [1, 2]


def test_wallet_metadata_rejects_too_many_or_oversized_tags():
    with TestClient(app) as client:
        headers = auth(client)
        address = "0x" + "d" * 40
        too_many = client.post("/api/wallets", headers=headers, json={
            "wallet_name": "Wallet", "chain": "EVM", "address": address,
            "tags": [f"tag-{index}" for index in range(13)]
        })
        assert too_many.status_code == 422
        oversized = client.post("/api/wallets", headers=headers, json={
            "wallet_name": "Wallet", "chain": "EVM", "address": address,
            "tags": ["x" * 25]
        })
        assert oversized.status_code == 422


def test_wallet_creation_validates_selected_chain_and_preserves_base58_ecosystem():
    with TestClient(app) as client:
        headers = auth(client)
        cases = [
            ("EVM", "0x1111111111111111111111111111111111111111", "EVM"),
            ("TRON", "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb", "TRON"),
            ("SOL", "11111111111111111111111111111111", "SOL"),
            ("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", "Bitcoin"),
        ]
        for chain, address, expected in cases:
            response = client.post("/api/wallets", headers=headers, json={
                "wallet_name": chain, "chain": chain, "address": address
            })
            assert response.status_code == 201, response.text
            assert response.json()["ecosystem"] == expected
            assert response.json()["chain"] == expected

def test_wallet_rejects_bad_checksum_and_cross_chain_selection():
    with TestClient(app) as client:
        headers = auth(client)
        cases = [
            ("TRON", "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwa"),
            ("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNb"),
            ("BTC", "11111111111111111111111111111111"),
            ("SOL", "0x1111111111111111111111111111111111111111"),
        ]
        for chain, address in cases:
            response = client.post("/api/wallets", headers=headers, json={
                "wallet_name": "invalid", "chain": chain, "address": address
            })
            assert response.status_code == 422

def test_wallet_update_rejects_wrong_selected_chain():
    with TestClient(app) as client:
        headers = auth(client)
        created = client.post("/api/wallets", headers=headers, json={
            "wallet_name": "Solana", "chain": "SOL",
            "address": "11111111111111111111111111111111"
        })
        assert created.status_code == 201
        updated = client.put(f"/api/wallets/{created.json()['id']}", headers=headers, json={
            "wallet_name": "Solana", "chain": "BTC",
            "address": "11111111111111111111111111111111"
        })
        assert updated.status_code == 422
