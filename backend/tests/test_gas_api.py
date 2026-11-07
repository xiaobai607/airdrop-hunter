import app.routers.gas as gas_module
from fastapi.testclient import TestClient
from app.main import app


def test_gas_api_requires_server_side_etherscan_key(monkeypatch):
    monkeypatch.delenv("ETHERSCAN_API_KEY", raising=False)
    with TestClient(app) as client:
        response = client.get("/api/gas/evm/latest", params={"address": "0x" + "a" * 40})
    assert response.status_code == 503


def test_gas_api_rejects_invalid_address():
    with TestClient(app) as client:
        response = client.get("/api/gas/evm/latest", params={"address": "not-an-address"})
    assert response.status_code == 422


def test_gas_api_returns_validated_transaction(monkeypatch):
    monkeypatch.setenv("ETHERSCAN_API_KEY", "test-server-key")

    class FakeResponse:
        def raise_for_status(self):
            return None

        def json(self):
            return {
                "status": "1",
                "message": "OK",
                "result": [{
                    "hash": "0x" + "b" * 64,
                    "gasUsed": "21000",
                    "gasPrice": "25000000000",
                }],
            }

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, traceback):
            return False

        async def get(self, url, params):
            assert url == "https://api.etherscan.io/v2/api"
            assert params["apikey"] == "test-server-key"
            return FakeResponse()

    monkeypatch.setattr(gas_module.httpx, "AsyncClient", FakeClient)
    with TestClient(app) as client:
        response = client.get("/api/gas/evm/latest", params={"address": "0x" + "a" * 40})

    assert response.status_code == 200
    assert response.json() == {
        "hash": "0x" + "b" * 64,
        "gasUsed": "21000",
        "gasPrice": "25000000000",
    }
