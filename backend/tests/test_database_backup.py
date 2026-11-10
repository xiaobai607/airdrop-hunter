import pytest

from app.services.database_backup import (
    _assert_restore_target_empty,
    _connection_options,
    _validate_restore_target,
)


def test_backup_connection_options_accept_postgres_and_asyncpg_urls_without_echoing_password():
    args, env, identity = _connection_options(
        "postgresql+asyncpg://backup_user:secret%21@db.example.test:5433/airdrop_hunter",
        "BACKUP_SOURCE_DATABASE_URL",
    )
    assert identity == ("db.example.test", 5433, "airdrop_hunter")
    assert "secret!" not in " ".join(args)
    assert env["PGPASSWORD"] == "secret!"


def test_backup_restore_target_must_be_a_different_dedicated_database():
    source = ("db.example.test", 5432, "airdrop_hunter")
    _validate_restore_target(source, ("db.example.test", 5432, "airdrop_hunter_restore_check"))
    with pytest.raises(ValueError, match="different databases"):
        _validate_restore_target(source, source)
    with pytest.raises(ValueError, match="end with _restore_check"):
        _validate_restore_target(source, ("db.example.test", 5432, "airdrop_hunter_restore"))


def test_restore_target_preflight_allows_a_truly_empty_database(monkeypatch):
    from app.services import database_backup

    monkeypatch.setattr(database_backup, "_query", lambda *_args: "0")
    _assert_restore_target_empty([], {})


def test_restore_target_preflight_rejects_existing_objects_before_restore(monkeypatch):
    from app.services import database_backup

    captured = {}

    def query(_args, _env, sql):
        captured["sql"] = sql
        return "4"

    monkeypatch.setattr(database_backup, "_query", query)
    with pytest.raises(RuntimeError, match="not empty; no restore was attempted"):
        _assert_restore_target_empty([], {})
    assert "pg_class" in captured["sql"]
    assert "pg_proc" in captured["sql"]
    assert "pg_type" in captured["sql"]


def test_restore_target_preflight_fails_closed_when_count_cannot_be_parsed(monkeypatch):
    from app.services import database_backup

    monkeypatch.setattr(database_backup, "_query", lambda *_args: "unknown")
    with pytest.raises(RuntimeError, match="Could not verify"):
        _assert_restore_target_empty([], {})


def test_backup_connection_rejects_non_postgresql_or_incomplete_urls():
    with pytest.raises(ValueError):
        _connection_options("https://example.test/database", "BACKUP_SOURCE_DATABASE_URL")
    with pytest.raises(ValueError):
        _connection_options("postgresql://", "BACKUP_SOURCE_DATABASE_URL")


def test_backup_connection_accepts_render_postgres_scheme():
    args, env, identity = _connection_options(
        "postgres://backup_user:secret@db.example.test:5432/airdrop_hunter",
        "BACKUP_SOURCE_DATABASE_URL",
    )
    assert identity == ("db.example.test", 5432, "airdrop_hunter")
    assert env["PGPASSWORD"] == "secret"
