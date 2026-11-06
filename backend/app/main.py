from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

from fastapi import FastAPI

from app.api.projects import router as projects_router
from app.api.airdrops import router as airdrops_router
from app.database import Base, engine
# Import models so their tables are registered in Base.metadata before create_all.
from app import models  # noqa: F401


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Development convenience: create missing tables at application startup."""
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    yield
    await engine.dispose()


app = FastAPI(
    title="Airdrop Hunter API",
    version="0.2.0",
    description="Private API for the Airdrop Hunter application.",
    lifespan=lifespan,
)

app.include_router(projects_router)
app.include_router(airdrops_router)


@app.get("/health", tags=["system"])
async def health() -> dict[str, str]:
    """Liveness endpoint; startup also requires the configured database."""
    return {"status": "ok", "service": "airdrop-hunter-api"}
