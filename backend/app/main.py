import asyncio
import logging
from pathlib import Path
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.exception_handlers import request_validation_exception_handler
from fastapi.responses import JSONResponse
from slowapi.errors import RateLimitExceeded
from slowapi import _rate_limit_exceeded_handler
from alembic import command
from alembic.config import Config
from app.routers.auth import router as auth_router, limiter
from app.routers.projects import router as projects_router
from app.routers.market import router as market_router
from app.routers.gas import router as gas_router
from app.routers.wallet import router as wallet_router, wallets_router
from app.api.tasks import router as tasks_router
from app.api.stats import router as stats_router
from app.api.airdrops import router as airdrops_router
from app.api.airdrop_hub import router as airdrop_hub_router, sync_router as airdrop_sync_router
from app.api.sources import router as sources_router
from app.database import engine
from app import models  # noqa: F401

logger = logging.getLogger("airdrop_hunter.api")


async def run_database_migrations() -> None:
    """Run versioned PostgreSQL migrations off the event loop."""
    config_path = Path(__file__).resolve().parent.parent / "alembic.ini"
    config = Config(str(config_path))
    await asyncio.to_thread(command.upgrade, config, "head")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    await run_database_migrations()
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
    auth_router, projects_router, market_router, gas_router, wallet_router, wallets_router,
    tasks_router, stats_router, airdrops_router, airdrop_hub_router,
    airdrop_sync_router, sources_router,
):
    app.include_router(router)


@app.get("/health", tags=["system"])
async def health():
    return {"status": "ok", "service": "airdrop-hunter-api"}
