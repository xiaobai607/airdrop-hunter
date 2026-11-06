from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from fastapi import FastAPI
from sqlalchemy import text
from app.api.auth import router as auth_router
from app.api.projects import router as projects_router
from app.api.tasks import router as tasks_router
from app.api.market import router as market_router
from app.api.stats import router as stats_router
from app.api.wallet import router as wallet_router
from app.api.airdrops import router as airdrops_router
from app.api.sources import router as sources_router
from app.database import Base, engine
from app import models  # noqa: F401

async def migrate_existing_schema() -> None:
    """Idempotent additive migration; never drops or reassigns legacy records."""
    statements=[
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255)",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS user_id UUID",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS official_url VARCHAR(1000) NOT NULL DEFAULT ''",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS twitter_url VARCHAR(1000) NOT NULL DEFAULT ''",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS deadline TIMESTAMPTZ NULL",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS expected_income NUMERIC(20,8) NOT NULL DEFAULT 0",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS gas_cost NUMERIC(20,8) NOT NULL DEFAULT 0",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS actual_income NUMERIC(20,8) NOT NULL DEFAULT 0",
        "CREATE INDEX IF NOT EXISTS ix_projects_user_id ON projects(user_id)",
    ]
    async with engine.begin() as conn:
        for statement in statements:
            await conn.execute(text(statement))
        await conn.execute(text("""
            DO $$
            BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_projects_user_id_users') THEN
                ALTER TABLE projects ADD CONSTRAINT fk_projects_user_id_users
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
              END IF;
            END $$;
        """))

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    await migrate_existing_schema()
    yield
    await engine.dispose()

app=FastAPI(title="Airdrop Hunter API",version="0.4.0",
    description="Airdrop discovery, project tracking and market aggregation API.",lifespan=lifespan)
for router in (auth_router,projects_router,tasks_router,market_router,stats_router,wallet_router,airdrops_router,sources_router):
    app.include_router(router)

@app.get("/health",tags=["system"])
async def health():
    return {"status":"ok","service":"airdrop-hunter-api"}
