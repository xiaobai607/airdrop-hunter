from fastapi import FastAPI

app = FastAPI(
    title="Airdrop Hunter API",
    version="0.1.0",
    description="Private API for the Airdrop Hunter application.",
)


@app.get("/health", tags=["system"])
async def health() -> dict[str, str]:
    """Basic liveness endpoint; does not imply provider or database readiness."""
    return {"status": "ok", "service": "airdrop-hunter-api"}
