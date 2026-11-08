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
        saved = client.post("/api/wallets", headers=alice, json={"wallet_name": "主号", "chain": "EVM", "address": address})
        assert saved.status_code == 201
        wallet = saved.json()
        assert wallet["wallet_name"] == "主号"
        assert wallet["chain"] == "EVM"
        assert wallet["ecosystem"] == "EVM"
        assert wallet["network_icon"] == "E"
        assert wallet["address"] == address
        assert client.get("/api/wallets", headers=bob).json() == []
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
        second = client.post("/api/wallets", headers=headers, json={"wallet_name": "main", "chain": "EVM", "address": address})
        assert first.status_code == 201 and second.status_code == 201
        assert first.json()["id"] == second.json()["id"]
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


def test_address_classifier_distinguishes_bitcoin_base58_from_solana():
    from app.routers.wallet import identify_chain

    assert identify_chain("1BoatSLRHtKNngkdXEeobR76b53LETtpyT") == "Bitcoin"
    assert identify_chain("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa") == "Bitcoin"
    assert identify_chain("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh") == "Bitcoin"
    assert identify_chain("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5") is None
    assert identify_chain("11111111111111111111111111111111") == "SOL"


def test_address_classifier_supports_all_four_bitcoin_mainnet_formats():
    from app.routers.wallet import identify_chain

    addresses = [
        "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",  # Legacy P2PKH
        "3EmUH8Uh9EXE7axgyAeBsCc2vdUdKkDqWK",  # P2SH (commonly Nested SegWit)
        "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",  # Native SegWit v0
        "bc1p5d7rjq7g6rdk2yhzks9smlaqtedr4dekq08ge8ztwac72sfr9rusxg3297",  # Taproot v1
    ]
    assert all(identify_chain(address) == "Bitcoin" for address in addresses)
    assert identify_chain("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5") is None
