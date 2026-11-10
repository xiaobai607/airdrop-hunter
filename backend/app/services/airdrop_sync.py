from sqlalchemy.ext.asyncio import AsyncSession
from app.services.airdrop_hub_sync import sync_airdrop_hub

async def sync_airdrops(db: AsyncSession) -> int:
    """Public service entry point for syncing real open airdrop catalog records."""
    return await sync_airdrop_hub(db)
