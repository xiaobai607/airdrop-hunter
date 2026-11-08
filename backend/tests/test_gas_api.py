from decimal import Decimal
from uuid import uuid4

from fastapi.testclient import TestClient
from app.main import app
import app.routers.wallet_activity as activity


def auth(client):
    username = f"gas{uuid4().hex[:12]}"
    response = client.post("/api/auth/register", json={"username": username, "password": "strong-password-123"})
    assert response.status_code == 201
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_evm_activity_requires_login():
    with TestClient(app) as client:
        response = client.get(f"/api/wallets/{uuid4()}/evm-transactions")
    assert response.status_code == 401


def test_evm_activity_is_scoped_to_wallet_owner():
    with TestClient(app) as client:
        alice, bob = auth(client), auth(client)
        wallet = client.post("/api/wallets", headers=alice, json={
            "wallet_name": "Main", "chain": "EVM", "address": "0x" + "a" * 40
        }).json()
        response = client.get(f"/api/wallets/{wallet['id']}/evm-transactions", headers=bob)
    assert response.status_code == 404


def test_evm_activity_rejects_unsupported_network():
    with TestClient(app) as client:
        headers = auth(client)
        wallet = client.post("/api/wallets", headers=headers, json={
            "wallet_name": "Main", "chain": "EVM", "address": "0x" + "b" * 40
        }).json()
        response = client.get(f"/api/wallets/{wallet['id']}/evm-transactions", headers=headers, params={"chain_id": 8453})
    assert response.status_code == 422


def test_evm_activity_returns_actual_gas_and_never_auto_assigns_project(monkeypatch):
    class FakeResponse:
        def __init__(self, data): self.data = data
        def raise_for_status(self): pass
        def json(self): return self.data

    class FakeClient:
        def __init__(self, *args, **kwargs): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *args): pass
        async def get(self, url, params=None):
            if url == activity.COINPAPRIKA_ETH:
                return FakeResponse({"quotes": {"USD": {"price": "2000"}}})
            return FakeResponse({"status": "1", "message": "OK", "result": [{
                "hash": "0x" + "c" * 64, "gasUsed": "21000", "gasPrice": "1000000000",
                "timeStamp": "1700000000", "from": "0x" + "d" * 40,
                "to": "0x" + "e" * 40, "methodId": "0x12345678", "isError": "0"
            }]})

    monkeypatch.setenv("ETHERSCAN_API_KEY", "test-key")
    monkeypatch.setattr(activity.httpx, "AsyncClient", FakeClient)
    with TestClient(app) as client:
        headers = auth(client)
        wallet = client.post("/api/wallets", headers=headers, json={
            "wallet_name": "Main", "chain": "EVM", "address": "0x" + "d" * 40
        }).json()
        response = client.get(f"/api/wallets/{wallet['id']}/evm-transactions", headers=headers)
    assert response.status_code == 200
    tx = response.json()[0]
    assert Decimal(tx["gas_cost_eth"]) == Decimal("0.000021")
    assert Decimal(tx["gas_cost_usd"]) == Decimal("0.042")
    assert tx["project_official_id"] is None
    assert tx["attribution"] == "unassigned"


def test_evm_activity_requires_explorer_key(monkeypatch):
    monkeypatch.delenv("ETHERSCAN_API_KEY", raising=False)
    with TestClient(app) as client:
        headers = auth(client)
        wallet = client.post("/api/wallets", headers=headers, json={
            "wallet_name": "Main", "chain": "EVM", "address": "0x" + "f" * 40
        }).json()
        response = client.get(f"/api/wallets/{wallet['id']}/evm-transactions", headers=headers)
    assert response.status_code == 503
