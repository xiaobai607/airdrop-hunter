import logging
import os
from collections.abc import Mapping

from sqlalchemy import exc as sqlalchemy_exc
from sqlalchemy.engine import make_url
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase


logger = logging.getLogger(__name__)

_LOCAL_DATABASE_URL = (
    "postgresql+asyncpg://postgres:postgres@localhost:5432/airdrop_hunter"
)


def resolve_database_url(environ: Mapping[str, str] | None = None) -> str:
    """Resolve and validate a PostgreSQL URL for SQLAlchemy's asyncpg engine."""
    values = os.environ if environ is None else environ
    database_url = (values.get("DATABASE_URL") or "").strip()

    # Some dashboard/manual configurations accidentally include shell quotes.
    if len(database_url) >= 2 and database_url[0] == database_url[-1] and database_url[0] in ("'", '"'):
        database_url = database_url[1:-1].strip()

    if not database_url:
        is_production = (
            values.get("RENDER", "").lower() == "true"
            or values.get("APP_ENV", "").lower() == "production"
        )
        if is_production:
            raise RuntimeError(
                "DATABASE_URL is missing in production. Set the Render DATABASE_URL "
                "environment variable to the PostgreSQL connection string."
            )
        database_url = _LOCAL_DATABASE_URL

    # Render may provide postgres://; SQLAlchemy async requires asyncpg explicitly.
    lowered_url = database_url.lower()
    if lowered_url.startswith("postgres://"):
        database_url = "postgresql+asyncpg://" + database_url[len("postgres://"):]
    elif lowered_url.startswith("postgresql://"):
        database_url = "postgresql+asyncpg://" + database_url[len("postgresql://"):]
    elif lowered_url.startswith("postgresql+psycopg2://"):
        database_url = "postgresql+asyncpg://" + database_url[len("postgresql+psycopg2://"):]
    elif lowered_url.startswith("postgresql+psycopg://"):
        database_url = "postgresql+asyncpg://" + database_url[len("postgresql+psycopg://"):]

    try:
        parsed_url = make_url(database_url)
    except (sqlalchemy_exc.ArgumentError, ValueError) as exc:
        # Never include the raw URL here: it contains the database password.
        scheme = database_url.partition(":")[0] or "<missing>"
        raise RuntimeError(
            f"DATABASE_URL is not a valid SQLAlchemy URL (received scheme: {scheme}). "
            "Set it to the Render PostgreSQL Internal Database URL."
        ) from exc

    if parsed_url.drivername != "postgresql+asyncpg":
        raise RuntimeError(
            "DATABASE_URL must resolve to postgresql+asyncpg:// for this async backend."
        )

    logger.info("Database URL validated; SQLAlchemy driver=%s", parsed_url.drivername)
    return database_url


DATABASE_URL = resolve_database_url()


class Base(DeclarativeBase):
    pass


engine = create_async_engine(
    DATABASE_URL,
    echo=False,
    pool_pre_ping=True,
)
AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
)


async def get_db():
    async with AsyncSessionLocal() as session:
        yield session
