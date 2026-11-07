import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.exception_handlers import request_validation_exception_handler
from fastapi.responses import JSONResponse
from slowapi.errors import RateLimitExceeded
from slowapi import _rate_limit_exceeded_handler
from sqlalchemy import text
from app.routers.auth import router as auth_router, limiter
from app.routers.projects import router as projects_router
from app.routers.market import router as market_router
from app.routers.wallet import router as wallet_router, wallets_router
from app.api.tasks import router as tasks_router
from app.api.stats import router as stats_router
from app.api.airdrops import router as airdrops_router
from app.api.airdrop_hub import router as airdrop_hub_router, sync_router as airdrop_sync_router
from app.api.sources import router as sources_router
from app.database import Base, engine
from app import models  # noqa: F401

logger = logging.getLogger("airdrop_hunter.api")


async def migrate_existing_schema() -> None:
    statements = [
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(24)",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS uid VARCHAR(6)",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255)",
        "UPDATE users SET username = 'legacy_' || substr(replace(id::text, '-', ''), 1, 17) WHERE username IS NULL OR btrim(username) = ''",
        """WITH numbered AS (
            SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS n
            FROM users WHERE uid IS NULL OR btrim(uid) = ''
        ) UPDATE users AS u SET uid = lpad((99999 + numbered.n)::text, 6, '0')
          FROM numbered WHERE u.id = numbered.id""",
        "ALTER TABLE users ALTER COLUMN username SET NOT NULL",
        "ALTER TABLE users ALTER COLUMN uid SET NOT NULL",
        "ALTER TABLE users ALTER COLUMN email DROP NOT NULL",
        "CREATE UNIQUE INDEX IF NOT EXISTS ix_users_username ON users (username)",
        "CREATE UNIQUE INDEX IF NOT EXISTS ix_users_uid ON users (uid)",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS user_id UUID",
        "ALTER TABLE airdrops ADD COLUMN IF NOT EXISTS user_id UUID",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS official_url VARCHAR(1000) NOT NULL DEFAULT ''",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS claim_url VARCHAR(1000) NOT NULL DEFAULT ''",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS twitter_url VARCHAR(1000) NOT NULL DEFAULT ''",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS deadline TIMESTAMPTZ NULL",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS expected_income NUMERIC(20,8) NOT NULL DEFAULT 0",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS gas_cost NUMERIC(20,8) NOT NULL DEFAULT 0",
        "ALTER TABLE projects ADD COLUMN IF NOT EXISTS actual_income NUMERIC(20,8) NOT NULL DEFAULT 0",
        "ALTER TABLE wallets ADD COLUMN IF NOT EXISTS address VARCHAR(120) NOT NULL DEFAULT ''",
        "ALTER TABLE wallets ADD COLUMN IF NOT EXISTS wallet_name VARCHAR(80) NOT NULL DEFAULT 'Wallet'",
        "ALTER TABLE wallets ADD COLUMN IF NOT EXISTS network_icon VARCHAR(24) NOT NULL DEFAULT '?'",
        "UPDATE wallets SET address = public_key WHERE address = ''",
        "UPDATE wallets SET wallet_name = 'Wallet' WHERE wallet_name = ''",
        "ALTER TABLE wallets ALTER COLUMN address SET NOT NULL",
        "CREATE UNIQUE INDEX IF NOT EXISTS uq_wallets_user_address ON wallets(user_id, address)",
        "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS step_order INTEGER",
        """WITH ranked AS (
            SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id, project_id ORDER BY created_at, id) AS position
            FROM tasks WHERE step_order IS NULL
        ) UPDATE tasks SET step_order = ranked.position FROM ranked WHERE tasks.id = ranked.id""",
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
            DO $$ BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_projects_user_id_users') THEN
                ALTER TABLE projects ADD CONSTRAINT fk_projects_user_id_users
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
              END IF;
              IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_airdrops_user_id_users') THEN
                ALTER TABLE airdrops ADD CONSTRAINT fk_airdrops_user_id_users
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
              END IF;
            END $$;
        """))
        await conn.execute(text(
            "CREATE INDEX IF NOT EXISTS ix_airdrops_user_id ON airdrops(user_id)"
        ))


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    await migrate_existing_schema()
    yield
    await engine.dispose()


app = FastAPI(
    title="Airdrop Hunter API",
    version="0.8.0",
    description="Airdrop discovery, project tracking and wallet management API.",
    lifespan=lifespan,
)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)


@app.middleware("http")
async def add_security_headers(request: Request, call_next):
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["Referrer-Policy"] = "no-referrer"
    response.headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()"
    response.headers["Cache-Control"] = "no-store"
    return response


@app.exception_handler(RequestValidationError)
async def auth_validation_exception_handler(request: Request, exc: RequestValidationError):
    if request.url.path in ("/api/auth/register", "/api/auth/login") and request.method == "POST":
        fields = {str(error.get("loc", [""])[-1]) for error in exc.errors()}
        if "username" in fields:
            detail = "账号只能由4到20位英文字母和数字组成"
        elif "password" in fields:
            detail = "密码长度必须为8到30位"
        else:
            detail = "账号或密码格式不正确"
        return JSONResponse(status_code=400, content={"detail": detail})
    return await request_validation_exception_handler(request, exc)


@app.exception_handler(Exception)
async def unexpected_exception_handler(request: Request, exc: Exception):
    logger.error(
        "Unhandled API error on %s %s",
        request.method,
        request.url.path,
        exc_info=(type(exc), exc, exc.__traceback__),
    )
    return JSONResponse(status_code=500, content={"detail": "服务器繁忙"})


for router in (
    auth_router, projects_router, market_router, wallet_router, wallets_router,
    tasks_router, stats_router, airdrops_router, airdrop_hub_router,
    airdrop_sync_router, sources_router,
):
    app.include_router(router)


@app.get("/health", tags=["system"])
async def health():
    return {"status": "ok", "service": "airdrop-hunter-api"}
