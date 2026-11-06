from fastapi import FastAPI

from app.api.projects import router as projects_router

app = FastAPI(
    title="Airdrop Hunter API",
    version="0.2.0",
    description="Private API for the Airdrop Hunter application.",
)

app.include_router(projects_router)


@app.get("/health", tags=["system"])
async def health() -> dict[str, str]:
    """Basic liveness endpoint; does not imply provider or database readiness."""
    return {"status": "ok", "service": "airdrop-hunter-api"}
