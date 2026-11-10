import asyncio
import logging

from app.database import AsyncSessionLocal
from app.services.airdrop_hub_sync import sync_airdrop_hub
from app.services.rss_discovery import sync_rss_discovery_candidates


async def main() -> None:
    async with AsyncSessionLocal() as db:
        try:
            count = await sync_airdrop_hub(db)
            print(f"Imported or updated {count} airdrop chain entries.")
        except Exception as exc:
            logging.exception("Primary airdrop catalog import failed: %s", exc)
        try:
            count = await sync_rss_discovery_candidates(db)
            print(f"Imported or updated {count} RSS/Atom discovery candidates.")
        except Exception as exc:
            logging.exception("RSS discovery import failed: %s", exc)


if __name__ == "__main__":
    asyncio.run(main())
