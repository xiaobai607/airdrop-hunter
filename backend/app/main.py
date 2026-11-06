import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sqlalchemy import text
from app.routers.auth import router as auth_router
from app.routers.projects import router as projects_router
from app.routers.market import router as market_router
from app.routers.wallet import router as wallet_router
from app.api.tasks import router as tasks_router
from app.api.stats import router as stats_router
from app.api.airdrops import router as airdrops_router
from app.api.airdrop_hub import router as airdrop_hub_router, sync_router as airdrop_sync_router
from app.api.sources import router as sources_router
from app.database import Base, engine
from app import models  # noqa: F401

logger = logging.getLogger("airdrop_hunter.api")

async def migrate_existing_schema() -> None:
    """Idempotent additive migration. Existing rows are retained."""
    statements = [
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255)",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS user_id UUID",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS official_url VARCHAR(1000) NOT NULL DEFAULT ''",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS twitter_url VARCHAR(1000) NOT NULL DEFAULT ''",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS deadline TIMESTAMPTZ NULL",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS expected_income NUMERIC(20,8) NOT NULL DEFAULT 0",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS gas_cost NUMERIC(20,8) NOT NULL DEFAULT 0",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS actual_income NUMERIC(20,8) NOT NULL DEFAULT 0",
        "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS step_order INTEGER",
        """WITH ranked AS (
            SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id, project_id ORDER BY created_at, id) AS position
            FROM tasks WHERE step_order IS NULL
        )
        UPDATE tasks SET step_order = ranked.position FROM ranked WHERE tasks.id = ranked.id""",
        "ALTER TABLE tasks ALTER COLUMN step_order SET DEFAULT 1",
        "ALTER TABLE tasks ALTER COLUMN step_order SET NOT NULL",
        "CREATE INDEX IF NOT EXISTS ix_projects_user_id ON projects(user_id)",
        "CREATE INDEX IF NOT EXISTS ix_tasks_user_id ON tasks(user_id)",
        "CREATE INDEX IF NOT EXISTS ix_tasks_project_id ON tasks(project_id)",
        "CREATE INDEX IF NOT EXISTS ix_tasks_user_project_order ON tasks(user_id, project_id, step_order)",
        "CREATE INDEX IF NOT EXISTS ix_coins_user_id ON coins(user_id)",
        "CREATE INDEX IF NOT EXISTS ix_wallets_user_id ON wallets(user_id)",
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

app = FastAPI(title="Airdrop Hunter API", version="0.7.0", description="Airdrop discovery, project tracking and market aggregation API.", lifespan=lifespan)

@app.exception_handler(Exception)
async def unexpected_exception_handler(request: Request, exc: Exception):
    logger.error("Unhandled API error on %s %s", request.method, request.url.path, exc_info=(type(exc), exc, exc.__traceback__))
    return JSONResponse(status_code=500, content={"detail": "服务器繁忙"})

for router in (auth_router, projects_router, market_router, wallet_router, tasks_router, stats_router, airdrops_router, airdrop_hub_router, airdrop_sync_router, sources_router):
    app.include_router(router)

@app.get("/health", tags=["system"])
async def health():
    return {"status": "ok", "service": "airdrop-hunter-api"}
