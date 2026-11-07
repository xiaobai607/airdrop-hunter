import pytest

from app.database import resolve_database_url


def test_normalizes_render_postgres_scheme_to_asyncpg():
    assert resolve_database_url(
        {"DATABASE_URL": "postgres://user:pass@host:5432/db"}
    ) == "postgresql+asyncpg://user:pass@host:5432/db"


def test_normalizes_postgresql_scheme_to_asyncpg():
    assert resolve_database_url(
        {"DATABASE_URL": "postgresql://user:pass@host:5432/db"}
    ) == "postgresql+asyncpg://user:pass@host:5432/db"


def test_preserves_explicit_asyncpg_scheme():
    url = "postgresql+asyncpg://user:pass@host:5432/db"
    assert resolve_database_url({"DATABASE_URL": url}) == url


def test_missing_database_url_uses_local_default_outside_production():
    assert resolve_database_url({}).startswith("postgresql+asyncpg://")


@pytest.mark.parametrize(
    "environment",
    [
        {"RENDER": "true"},
        {"APP_ENV": "production"},
    ],
)
def test_missing_database_url_fails_fast_in_production(environment):
    with pytest.raises(RuntimeError, match="DATABASE_URL is required"):
        resolve_database_url(environment)
