import asyncio
import logging

from app.database import AsyncSessionLocal
from app.services.airdrop_hub_sync import sync_airdrop_hub
from app.services.rss_discovery import sync_rss_discovery_candidates
from app.services.rss_campaign_observations import promote_pending_candidates_to_campaign_observations
from app.services.airdrop_campaign_scoring import recalculate_campaign_scores


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
            mirrored = await promote_pending_candidates_to_campaign_observations(db)
            print(f"Created {mirrored} unpublished campaign observations from pending candidates.")
            score_result = await recalculate_campaign_scores(db)
            print(
                "Campaign scoring: "
                f"evaluated={score_result['evaluated']}, updated={score_result['updated']}, "
                f"publishable={score_result['publishable']}, held={score_result['held']}."
            )
        except Exception as exc:
            logging.exception("RSS discovery import failed: %s", exc)


if __name__ == "__main__":
    asyncio.run(main())
