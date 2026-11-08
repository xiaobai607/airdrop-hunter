import os
import pytest
from fastapi.testclient import TestClient
from app.main import app
import app.routers.wallet_activity as activity


class FakeResponse:
    def __init__(self, data): self.data = data
    def raise_for_status(self): pass
    def json(self): return self.data


class FakeClient:
    def __init__(self, *args, **kwargs): pass
    async def __aenter__(self): return self
    async def __aexit__(self, *args): pass
    async def get(self, url, params=None, headers=None):
        if url == activity.ETHERSCAN_V2:
            return FakeResponse({"status": "1", "result": [{
                "hash": "0x" + "a" * 64, "gasUsed": "21000", "gasPrice": "1000000000",
                "timeStamp": "1700000000", "from": "0x" + "b" * 40, "to": "0x" + "c" * 40,
                "methodId": "0x12345678", "isError": "0"
            }]})
        if url == activity.TRONGRID + "/v1/accounts/" + "T" + "A" * 33 + "/transactions":
            return FakeResponse({"data": [{
                "txID": "d" * 64, "block_timestamp": 1700000000000,
                "raw_data": {"contract": [{"parameter": {"value": {"owner_address": "41" + "e" * 40, "to_address": "41" + "f" * 40}}}]}
            }]})
        if url == activity.COINPAPRIKA_ETH:
            return FakeResponse({"quotes": {"USD": {"price": "2000"}}})
        if url == activity.COINPAPRIKA_TRX:
            return FakeResponse({"quotes": {"USD": {"price": "0.1"}}})
        raise AssertionError(url)
    async def post(self, url, json=None):
        assert url == activity.TRONGRID + "/wallet/gettransactioninfobyid"
        return FakeResponse({"id": json["value"], "fee": 2800000, "receipt": {"energy_usage_total": 10000, "energy_fee": 2000000, "result": "SUCCESS"}})


def test_public_ethereum_activity_does_not_require_login(monkeypatch):
    monkeypatch.setenv("ETHERSCAN_API_KEY", "test")
    monkeypatch.setattr(activity.httpx, "AsyncClient", FakeClient)
    with TestClient(app) as client:
        response = client.get("/api/wallet-activity/ethereum/" + "0x" + "b" * 40 + "/transactions")
    assert response.status_code == 200
    tx = response.json()[0]
    assert tx["gas_cost_native"] == "0.000021"
    assert tx["gas_cost_usd"] == "0.042"
    assert tx["attribution"] == "unassigned"


def test_public_tron_activity_does_not_require_login(monkeypatch):
    monkeypatch.setattr(activity.httpx, "AsyncClient", FakeClient)
    address = "T" + "A" * 33
    with TestClient(app) as client:
        response = client.get(f"/api/wallet-activity/tron/{address}/transactions")
    assert response.status_code == 200
    tx = response.json()[0]
    assert tx["native_symbol"] == "TRX"
    assert tx["gas_cost_native"] == "2.8"
    assert tx["gas_cost_usd"] == "0.28"
    assert tx["attribution"] == "unassigned"


@pytest.mark.parametrize("chain,address", [
    ("ethereum", "T" + "A" * 33),
    ("tron", "0x" + "a" * 40),
])
def test_public_activity_rejects_wrong_address_type(chain, address):
    with TestClient(app) as client:
        response = client.get(f"/api/wallet-activity/{chain}/{address}/transactions")
    assert response.status_code == 422
