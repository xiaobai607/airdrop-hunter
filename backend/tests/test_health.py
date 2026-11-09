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

    from alembic.config import Config
    from pathlib import Path

    migration_config = Config()
    migration_config.set_main_option(
        "script_location",
        str(Path(main_module.__file__).resolve().parent / "migrations"),
    )
    current_head = main_module.ScriptDirectory.from_config(migration_config).get_current_head()
    monkeypatch.setattr(
        main_module.ScriptDirectory,
        "from_config",
        lambda config: SimpleNamespace(get_current_head=lambda: current_head),
    )
    monkeypatch.setattr(main_module, "engine", Engine(current_head))
    ready = client.get("/health/ready")
    assert ready.status_code == 200
    assert ready.json()["status"] == "ready"
    assert ready.json()["schema_revision"] == current_head

    monkeypatch.setattr(main_module, "engine", Engine("older-migration-revision"))
    behind = client.get("/health/ready")
    assert behind.status_code == 503
    assert behind.json() == {"detail": "service_not_ready"}
