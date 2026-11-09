import pytest

from app.services.database_backup import _connection_options, _validate_restore_target


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
