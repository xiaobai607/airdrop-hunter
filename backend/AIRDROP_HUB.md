# Airdrop Hub data pipeline — current implementation

This document describes the active importer, not the retired public-search/RSS experiments. The Android app reads normalized records from the backend; it does not scrape arbitrary websites.

## Active source adapters

The scheduled sync calls four isolated adapters:

1. **Binance official CMS announcements** — reads the configured Binance CMS announcement categories. Records are announcement leads, not an inferred campaign database. The app displays the official headline and original link; the detail endpoint retrieves the original Binance article body.
2. **CoinGecko Airdrop category** — requires `COINGECKO_DEMO_API_KEY`. Category/market rows are token discovery observations only. A token appearing in this category is not proof of a currently claimable airdrop.
3. **Operator-approved GitHub Raw lists** — configured through `AIRDROP_GITHUB_RAW_SOURCE_APPROVALS`, a JSON array of manually reviewed source records. Every record must include a unique source ID, exact repository identity, HTTPS Raw file URL pinned to a full 40-character commit SHA, SPDX-style license identifier, HTTPS license reference, explicit attribution text, `review_status: "approved"`, and an ISO review date. The repository identity must match the Raw URL owner/repository. URL-only legacy configuration is ignored. The entire configuration fails closed if any entry is incomplete, unapproved, duplicated, unsafe, unpinned, or if more than 20 entries are supplied. The operator must update/review the record when changing the pinned commit.
4. **DexScreener new-pool observations** — optional search terms come from `DEXSCREENER_SEARCH_QUERIES` (comma-separated, maximum eight). The current filter requires a pool no older than seven days, at least USD 10,000 liquidity, at least USD 50,000 24-hour volume, and at least 20 24-hour buys/sells combined. These are market-activity clues only, never confirmed airdrops.

Binance CMS access does not require an API key. CoinGecko requires the Demo key. GitHub Raw files are not fetched unless the operator supplies a complete manually approved source record. The code validates the shape of that approval record; it does **not** independently verify the license text, the license URL's legal effect, the repository owner's authority, or whether the license covers the intended public-catalog reuse. An operator must inspect the repository's license and attribution/redistribution terms before marking a source approved. A public Raw URL, a syntactically valid license identifier, or a license URL alone is not permission.

## Trust and publication boundary

- `TRUSTED_AIRDROP_PROVIDER_LABELS` is currently empty. No third-party directory provider is approved for automatic `PUBLISHED_SAFE` publication.
- CoinGecko, GitHub Raw and DexScreener rows remain `TRUST_LEVEL_0` / `OBSERVATION`. They must not be presented as confirmed airdrop campaigns.
- GitHub Raw attribution, license identifier/reference and review date are preserved in each imported lead's internal description for provenance. This does not convert a lead into a verified campaign.
- Binance announcement rows also remain observation records. The public `GET /api/airdrops/hub` list intentionally exposes only Binance announcement records, with the visible status set to `待核实`; the source is official, but the app does not invent reward amounts, dates, eligibility or participation terms.
- The Binance detail endpoint displays the original CMS HTML. Missing terms stay missing; the app does not summarize or guess them.
- Source failures are isolated. A failed source is rolled back and recorded as failed without deleting that source's last-known-good rows or blocking the other adapters.

## Explicitly inactive legacy paths

- The old `web3-discover` JSON/MCP adapter has been removed; its former endpoints and parser are no longer present in the active importer.
- URL-only `AIRDROP_GITHUB_RAW_SOURCES` configuration is no longer accepted. It cannot bypass the license/attribution approval gate.
- RSS/Atom ingestion is not called by the scheduled importer. Legacy RSS parser, candidate tables and review endpoints remain dormant for compatibility; the scheduled workflow no longer injects RSS configuration, and no wild RSS feeds are configured or automatically searched.
- Do not re-enable public web search, arbitrary RSS discovery, or unreviewed aggregators as a shortcut.

## Configuration

- `COINGECKO_DEMO_API_KEY`: optional; without it the CoinGecko adapter reports a source-level configuration failure.
- `AIRDROP_GITHUB_RAW_SOURCE_APPROVALS`: optional JSON array of complete manually approved records. Each entry has exactly these fields:

```json
[
  {
    "source_id": "operator-reviewed-list",
    "url": "https://raw.githubusercontent.com/OWNER/REPO/FULL_40_CHARACTER_COMMIT_SHA/path/to/list.json",
    "repository": "OWNER/REPO",
    "license_id": "MIT",
    "license_url": "https://spdx.org/licenses/MIT.html",
    "attribution": "Exact attribution text required by the repository terms",
    "review_status": "approved",
    "reviewed_at": "2026-11-11"
  }
]
```

  The example is a schema illustration, not an approved real source. Do not copy it as a production source or invent repository details. Every URL must use the exact `raw.githubusercontent.com` host, HTTPS, and a full commit SHA; branch names such as `main` are rejected.
- `DEXSCREENER_SEARCH_QUERIES`: optional comma-separated query terms; defaults to `SOL,ETH,USDC` and is capped at eight terms.
- Binance CMS categories are code-configured.

## API

- `GET /api/airdrops/hub` — Binance announcement leads only.
- `GET /api/airdrops/hub?chain=Solana` — SQL-filtered results; Binance announcements currently use `Unknown` chain unless the source explicitly supplies one.
- `GET /api/airdrops/hub/{id}` — fetches the original Binance CMS article body and metadata.
- `GET /api/airdrops/hub/status` — latest in-process sync status.
- `POST /api/airdrops/hub/refresh` and compatibility alias `POST /api/airdrops/sync` — trigger the same four-source sync, subject to rate limits. The scheduled GitHub workflow invokes the importer only when `AIRDROP_DISCOVERY_SYNC_ENABLED=true` and `DATABASE_URL` is configured.

## Scheduled import

From the repository's `backend` directory:

```bash
python -m scripts.sync_airdrop_hub
```

The script runs the four-source catalog sync, then attempts campaign market enrichment and score recalculation as separate steps. A failure in one enrichment step is logged without fabricating an airdrop record. Configure a daily Render Cron Job only if the deployment is intended to run this script; this document does not claim that a production cron is already configured.

## Validation boundary

Automated tests and CI validate the authored parser, fail-closed approval metadata, commit pinning, filtering, source isolation and API contracts. Exact-head Push workflow #1111 for commit `2546778c` passed with 213 backend tests. CI does not prove that external providers are currently available, that the operator's legal review is correct, that a real campaign is claimable, or that the Binance article HTML renders correctly on a physical phone.

### Binance CMS category IDs (verified 2026-11-11)

- `128` is Binance's **Crypto Airdrop** category (`/en/support/announcement/list/128`).
- `93` is Binance's **Latest Activities** category (`/en/support/announcement/list/93`). This is the activity feed used by the importer; titles are still keyword-filtered for airdrop/Launchpool/HODLer/Megadrop relevance.
- `49` is **not** Latest Activities; the official announcement UI identifies it as **Latest Binance News**. Do not use it as the activity category.
- The importer therefore requests only catalog IDs `(128, 93)`. It does not ingest the general news category.

### Binance announcement list endpoint compatibility (2026-11-11)

- The current RSSHub Binance announcement adapter uses Binance's newer observed route `/bapi/apex/v1/public/apex/cms/article/list/query` with the same `type/pageNo/pageSize/catalogId` parameters and `data.catalogs[].articles[]` response structure.
- The importer now tries that route first and falls back per category to the former `/bapi/composite/v1/public/cms/article/list/query` route if the request fails or the response does not contain `data.catalogs`.
- This is a compatibility measure based on observed frontend usage, not a Binance-supported public API guarantee. A live provider smoke test is still required; MockTransport tests only verify request construction and fallback behavior.
