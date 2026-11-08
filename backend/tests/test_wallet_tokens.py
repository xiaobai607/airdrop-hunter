import pytest
from fastapi.testclient import TestClient

import app.routers.wallet_tokens as wallet_tokens
from app.main import app


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self.payload


class FakeClient:
    def __init__(self, *args, **kwargs):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        pass

    async def get(self, url, params=None, headers=None):
        assert url == wallet_tokens.ETHERSCAN_V2
        assert params["action"] == "addresstokenbalance"
        return FakeResponse({
            "status": "1",
            "message": "OK",
            "result": [{
                "TokenAddress": "0x" + "a" * 40,
                "TokenName": "USD Coin",
                "TokenSymbol": "USDC",
                "TokenQuantity": "1250000",
                "TokenDivisor": "6",
            }],
        })


class RestrictedClient(FakeClient):
    async def get(self, url, params=None, headers=None):
        return FakeResponse({
            "status": "0",
            "message": "NOTOK",
            "result": "This endpoint is only available for PRO users",
        })


def test_evm_token_portfolio_reads_all_supported_networks(monkeypatch):
    monkeypatch.setenv("ETHERSCAN_API_KEY", "test")
    monkeypatch.setattr(wallet_tokens.httpx, "AsyncClient", FakeClient)
    monkeypatch.setattr(wallet_tokens, "_LAST_REQUEST_AT", 0.0)
    address = "0x" + "b" * 40
    with TestClient(app) as client:
        response = client.get(f"/api/wallet-tokens/evm/{address}")
    assert response.status_code == 200
    payload = response.json()
    assert len(payload["networks"]) == 7
    assert {item["status"] for item in payload["networks"]} == {"available"}
    assert len(payload["holdings"]) == 7
    assert payload["holdings"][0]["quantity"] == "1.25"
    assert payload["holdings"][0]["symbol"] == "USDC"


def test_evm_token_portfolio_marks_plan_restricted_network_unavailable(monkeypatch):
    monkeypatch.setenv("ETHERSCAN_API_KEY", "test")
    monkeypatch.setattr(wallet_tokens.httpx, "AsyncClient", RestrictedClient)
    monkeypatch.setattr(wallet_tokens, "_LAST_REQUEST_AT", 0.0)
    address = "0x" + "b" * 40
    with TestClient(app) as client:
        response = client.get(f"/api/wallet-tokens/evm/{address}")
    assert response.status_code == 200
    payload = response.json()
    assert payload["holdings"] == []
    assert len(payload["networks"]) == 7
    assert {item["status"] for item in payload["networks"]} == {"unavailable"}
    assert all(item["detail"] for item in payload["networks"])


@pytest.mark.parametrize("address", ["not-an-address", "T" + "A" * 33])
def test_evm_token_portfolio_rejects_invalid_address(address):
    with TestClient(app) as client:
        response = client.get(f"/api/wallet-tokens/evm/{address}")
    assert response.status_code == 422



class OneNetworkOfflineClient(FakeClient):
    async def get(self, url, params=None, headers=None):
        if params["chainid"] == "56":
            raise wallet_tokens.httpx.ConnectError("BSC explorer unavailable")
        return await super().get(url, params=params, headers=headers)


def test_one_evm_explorer_failure_does_not_hide_other_network_holdings(monkeypatch):
    monkeypatch.setenv("ETHERSCAN_API_KEY", "test")
    monkeypatch.setattr(wallet_tokens.httpx, "AsyncClient", OneNetworkOfflineClient)
    monkeypatch.setattr(wallet_tokens, "_LAST_REQUEST_AT", 0.0)
    address = "0x" + "b" * 40
    with TestClient(app) as client:
        response = client.get(f"/api/wallet-tokens/evm/{address}")
    assert response.status_code == 200
    payload = response.json()
    by_chain = {item["chain"]: item for item in payload["networks"]}
    assert by_chain["bsc"]["status"] == "unavailable"
    assert sum(item["status"] == "available" for item in payload["networks"]) == 6
    assert {item["chain"] for item in payload["holdings"]} == set(wallet_tokens.EVM_NETWORKS) - {"bsc"}


def test_evm_token_parser_rejects_malformed_or_zero_balances():
    assert wallet_tokens._parse_holding({
        "TokenAddress": "0x" + "a" * 40,
        "TokenName": "Fake",
        "TokenSymbol": "FAKE",
        "TokenQuantity": "0",
        "TokenDivisor": "18",
    }, "ethereum") is None
    assert wallet_tokens._parse_holding({
        "TokenAddress": "invalid",
        "TokenName": "Fake",
        "TokenSymbol": "FAKE",
        "TokenQuantity": "100",
        "TokenDivisor": "18",
    }, "ethereum") is None
