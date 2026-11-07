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


@pytest.mark.parametrize(
    "url",
    [
        "POSTGRES://user:pass@host:5432/db",
        '"postgres://user:pass@host:5432/db"',
        "'postgresql://user:pass@host:5432/db'",
    ],
)
def test_normalizes_case_and_accidental_dashboard_quotes(url):
    assert resolve_database_url({"DATABASE_URL": url}) == (
        "postgresql+asyncpg://user:pass@host:5432/db"
    )


@pytest.mark.parametrize(
    "url",
    [
        "postgresql+psycopg://user:pass@host:5432/db",
        "postgresql+psycopg2://user:pass@host:5432/db",
    ],
)
def test_normalizes_sync_postgres_drivers_to_asyncpg(url):
    assert resolve_database_url({"DATABASE_URL": url}) == (
        "postgresql+asyncpg://user:pass@host:5432/db"
    )


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
    with pytest.raises(RuntimeError, match="DATABASE_URL is missing"):
        resolve_database_url(environment)


def test_malformed_database_url_fails_with_safe_diagnostic():
    with pytest.raises(RuntimeError, match="not a valid SQLAlchemy URL"):
        resolve_database_url({"DATABASE_URL": "this-is-not-a-url"})


def test_non_postgres_database_url_is_rejected():
    with pytest.raises(RuntimeError, match="must resolve to postgresql\+asyncpg"):
        resolve_database_url({"DATABASE_URL": "sqlite:///tmp/test.db"})
