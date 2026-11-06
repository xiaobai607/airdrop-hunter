from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from fastapi import FastAPI
from sqlalchemy import text
from app.api.auth import router as auth_router
from app.api.projects import router as projects_router
from app.api.airdrops import router as airdrops_router
from app.api.sources import router as sources_router
from app.database import Base, engine
from app import models  # noqa: F401

async def migrate_auth_columns() -> None:
    """Add auth columns to existing PostgreSQL databases without dropping data."""
    async with engine.begin() as conn:
        await conn.execute(text("ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255)"))
        await conn.execute(text("ALTER TABLE projects ADD COLUMN IF NOT EXISTS user_id UUID"))
        await conn.execute(text("""
            DO $$
            BEGIN
                IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_projects_user_id_users') THEN
                    ALTER TABLE projects ADD CONSTRAINT fk_projects_user_id_users
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
                END IF;
            END
            $$;
        """))
        await conn.execute(text("CREATE INDEX IF NOT EXISTS ix_projects_user_id ON projects(user_id)"))

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    await migrate_auth_columns()
    yield
    await engine.dispose()

app = FastAPI(title="Airdrop Hunter API", version="0.3.0",
              description="Private API for the Airdrop Hunter application.", lifespan=lifespan)
app.include_router(auth_router)
app.include_router(projects_router)
app.include_router(airdrops_router)
app.include_router(sources_router)

@app.get("/health", tags=["system"])
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "airdrop-hunter-api"}
