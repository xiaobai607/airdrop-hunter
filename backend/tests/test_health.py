from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_endpoint_returns_ok() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "airdrop-hunter-api",
    }


def test_readiness_endpoint_requires_current_database_revision(monkeypatch) -> None:
    from types import SimpleNamespace
    import app.main as main_module

    class Result:
        def __init__(self, revision):
            self.revision = revision

        def scalar_one_or_none(self):
            return self.revision

    class Connection:
        def __init__(self, revision):
            self.revision = revision

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, traceback):
            return None

        async def execute(self, statement):
            return Result(self.revision)

    class Engine:
        def __init__(self, revision):
            self.revision = revision

        def connect(self):
            return Connection(self.revision)

    monkeypatch.setattr(
        main_module.ScriptDirectory,
        "from_config",
        lambda config: SimpleNamespace(get_current_head=lambda: "0014_discovery_source_status"),
    )
    monkeypatch.setattr(main_module, "engine", Engine("0014_discovery_source_status"))
    ready = client.get("/health/ready")
    assert ready.status_code == 200
    assert ready.json()["status"] == "ready"
    assert ready.json()["schema_revision"] == "0014_discovery_source_status"

    monkeypatch.setattr(main_module, "engine", Engine("0013_currency_assets"))
    behind = client.get("/health/ready")
    assert behind.status_code == 503
    assert behind.json() == {"detail": "service_not_ready"}
