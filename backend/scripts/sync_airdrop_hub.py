import asyncio
import logging

from app.database import AsyncSessionLocal
from app.services.airdrop_hub_sync import sync_airdrop_hub
from app.services.airdrop_campaign_scoring import recalculate_campaign_scores
from app.services.airdrop_market_data import refresh_campaign_market_data


async def main() -> None:
    failures: list[str] = []
    async with AsyncSessionLocal() as db:
        try:
            count = await sync_airdrop_hub(db)
            print(f"Imported or updated {count} airdrop chain entries.")
        except Exception as exc:
            failures.append("discovery_sync")
            logging.exception("Primary airdrop catalog import failed: %s", exc)
        # RSS/Atom discovery is intentionally disabled by the trusted-provider-only strategy.
        try:
            market_result = await refresh_campaign_market_data(db)
            print(
                "Campaign market enrichment: "
                f"considered={market_result['campaigns_considered']}, "
                f"dex_updated={market_result['dex_updated']}, "
                f"gecko_updated={market_result['gecko_updated']}."
            )
        except Exception as exc:
            failures.append("market_enrichment")
            logging.exception("Campaign market enrichment failed: %s", exc)
        try:
            score_result = await recalculate_campaign_scores(db)
            print(
                "Campaign scoring: "
                f"evaluated={score_result['evaluated']}, updated={score_result['updated']}, "
                f"publishable={score_result['publishable']}, held={score_result['held']}."
            )
        except Exception as exc:
            failures.append("campaign_scoring")
            logging.exception("Campaign scoring failed: %s", exc)

    # A scheduled run must be visibly red if any required pipeline stage failed.
    # Do not let a successful enrichment/scoring stage mask failed source ingestion.
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    asyncio.run(main())
