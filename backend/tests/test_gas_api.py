from fastapi.testclient import TestClient
from app.main import app


def test_real_gas_api_is_not_exposed():
    with TestClient(app) as client:
        response = client.get("/api/gas/evm/latest", params={"address": "0x" + "a" * 40})
    assert response.status_code == 404
