# Airdrop Hub data pipeline — current implementation

This document describes the active importer, not the retired public-search/RSS experiments. The Android app reads normalized records from the backend; it does not scrape arbitrary websites.

## Active source adapters

The scheduled sync calls four isolated adapters:

1. **Binance official CMS announcements** — reads the configured Binance CMS announcement categories. Records are announcement leads, not an inferred campaign database. The app displays the official headline and original link; the detail endpoint retrieves the original Binance article body.
2. **CoinGecko Airdrop category** — requires `COINGECKO_DEMO_API_KEY`. Category/market rows are token discovery observations only. A token appearing in this category is not proof of a currently claimable airdrop.
3. **Operator-configured GitHub Raw lists** — configured through `AIRDROP_GITHUB_RAW_SOURCES`, a JSON array of HTTPS Raw file URLs. The validator restricts the host to the exact `raw.githubusercontent.com` domain, rejects credentials, ports, query/fragment strings, malformed escapes and unsafe decoded path segments, and caps the list at 20 files. JSON entries must provide an explicit `website`, `official_url` or `officialUrl`; generic `url` fields are not treated as project websites. Markdown links must be HTTPS.
4. **DexScreener new-pool observations** — optional search terms come from `DEXSCREENER_SEARCH_QUERIES` (comma-separated, maximum eight). The current filter requires a pool no older than seven days, at least USD 10,000 liquidity, at least USD 50,000 24-hour volume, and at least 20 24-hour buys/sells combined. These are market-activity clues only, never confirmed airdrops.

Binance CMS access does not require an API key. CoinGecko requires the Demo key. GitHub Raw files must be explicitly configured by the operator. Do not configure an open-source list until its repository license, attribution requirements and reuse terms have been reviewed; the URL validator does not itself perform legal/license verification.

## Trust and publication boundary

- `TRUSTED_AIRDROP_PROVIDER_LABELS` is currently empty. No third-party directory provider is approved for automatic `PUBLISHED_SAFE` publication.
- CoinGecko, GitHub Raw and DexScreener rows remain `TRUST_LEVEL_0` / `OBSERVATION`. They must not be presented as confirmed airdrop campaigns.
- Binance announcement rows also remain observation records. The public `GET /api/airdrops/hub` list intentionally exposes only Binance announcement records, with the visible status set to `待核实`; the source is official, but the app does not invent reward amounts, dates, eligibility or participation terms.
- The Binance detail endpoint displays the original CMS HTML. Missing terms stay missing; the app does not summarize or guess them.
- Source failures are isolated. A failed source is rolled back and recorded as failed without deleting that source's last-known-good rows or blocking the other adapters.

## Explicitly inactive legacy paths

- The old `web3-discover` JSON/MCP directory is not part of `sync_airdrop_hub` and is not an approved provider.
- RSS/Atom ingestion is not called by the scheduled importer. Legacy RSS parser, candidate tables and review endpoints may remain in the repository for compatibility, but no wild RSS feeds are configured or automatically searched.
- Do not re-enable public web search, arbitrary RSS discovery, or unreviewed aggregators as a shortcut.

## Configuration

- `COINGECKO_DEMO_API_KEY`: optional; without it the CoinGecko adapter reports a source-level configuration failure.
- `AIRDROP_GITHUB_RAW_SOURCES`: optional JSON array of exact HTTPS GitHub Raw file URLs. Configure only reviewed sources.
- `DEXSCREENER_SEARCH_QUERIES`: optional comma-separated query terms; defaults to `SOL,ETH,USDC` and is capped at eight terms.
- Binance CMS categories are code-configured.

## API

- `GET /api/airdrops/hub` — Binance announcement leads only.
- `GET /api/airdrops/hub?chain=Solana` — SQL-filtered results; Binance announcements currently use `Unknown` chain unless the source explicitly supplies one.
- `GET /api/airdrops/hub/{id}` — fetches the original Binance CMS article body and metadata.
- `GET /api/airdrops/hub/status` — latest in-process sync status.
- `POST /api/airdrops/hub/refresh` and compatibility alias `POST /api/airdrops/sync` — trigger the same four-source sync, subject to rate limits.

## Scheduled import

From the repository's `backend` directory:

```bash
python -m scripts.sync_airdrop_hub
```

The script runs the four-source catalog sync, then attempts campaign market enrichment and score recalculation as separate steps. A failure in one enrichment step is logged without fabricating an airdrop record. Configure a daily Render Cron Job only if the deployment is intended to run this script; this document does not claim that a production cron is already configured.

## Validation boundary

Automated tests and CI validate the authored parsing, filtering, source isolation and API contracts. They do not prove that external providers are currently available, that an operator-configured repository license permits reuse, that a real campaign is claimable, or that the Binance article HTML renders correctly on a physical phone.
