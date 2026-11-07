import os
from collections.abc import Mapping

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase


_LOCAL_DATABASE_URL = (
    "postgresql+asyncpg://postgres:postgres@localhost:5432/airdrop_hunter"
)


def resolve_database_url(environ: Mapping[str, str] | None = None) -> str:
    """Resolve a SQLAlchemy async database URL with safe Render defaults."""
    values = os.environ if environ is None else environ
    database_url = (values.get("DATABASE_URL") or "").strip()

    if not database_url:
        is_production = (
            values.get("RENDER", "").lower() == "true"
            or values.get("APP_ENV", "").lower() == "production"
        )
        if is_production:
            raise RuntimeError(
                "DATABASE_URL is required in production; configure it in the service environment."
            )
        database_url = _LOCAL_DATABASE_URL

    # This project uses create_async_engine and asyncpg, not the sync psycopg2 driver.
    if database_url.startswith("postgres://"):
        database_url = "postgresql+asyncpg://" + database_url[len("postgres://"):]
    elif database_url.startswith("postgresql://"):
        database_url = "postgresql+asyncpg://" + database_url[len("postgresql://"):]

    return database_url


DATABASE_URL = resolve_database_url()


class Base(DeclarativeBase):
    pass


engine = create_async_engine(DATABASE_URL, pool_pre_ping=True)
AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
)


async def get_db():
    async with AsyncSessionLocal() as session:
        yield session
