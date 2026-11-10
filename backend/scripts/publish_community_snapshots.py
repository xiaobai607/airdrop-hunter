import argparse
import asyncio
import logging
from datetime import datetime
from zoneinfo import ZoneInfo

from app.database import AsyncSessionLocal
from app.services.community_snapshot_schedule import due_windows
from app.services.community_snapshot_storage import publisher_from_environment
from app.services.community_snapshots import generate_snapshot

logger = logging.getLogger("community-snapshots")
SHANGHAI = ZoneInfo("Asia/Shanghai")


async def publish_windows(windows: list[str]) -> dict:
    publisher = publisher_from_environment()
    generated_at = datetime.now(SHANGHAI)
    async with AsyncSessionLocal() as db:
        snapshots = {
            window: await generate_snapshot(db, window, generated_at)
            for window in windows
        }
    # Upload all immutable objects first; manifest is switched only after all succeed.
    return publisher.publish(snapshots)


async def main(selected_window: str | None = None) -> None:
    now = datetime.now(SHANGHAI)
    windows = [selected_window] if selected_window else due_windows(now)
    manifest = await publish_windows(windows)
    logger.info("Published snapshots=%s manifest_windows=%s", windows, sorted(manifest["snapshots"]))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Build and publish read-only community snapshots")
    parser.add_argument("--window", choices=("overall", "7d", "3d", "1h"))
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    asyncio.run(main(args.window))
