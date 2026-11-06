import asyncio

from app.database import AsyncSessionLocal
from app.services.airdrop_hub_sync import sync_airdrop_hub


async def main() -> None:
    async with AsyncSessionLocal() as db:
        count = await sync_airdrop_hub(db)
        print(f"Imported or updated {count} airdrop chain entries.")


if __name__ == "__main__":
    asyncio.run(main())
