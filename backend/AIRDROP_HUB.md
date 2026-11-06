# Airdrop Hub data import

The public catalog is stored in the Neon PostgreSQL table `airdrop_hub_projects`.
The API serves only rows persisted in that table; Android does not scrape third-party pages.

## Sources

1. Primary: `https://web3-discover.vercel.app/api/airdrops.json`
2. Fallback: the public `list_active_airdrops` JSON-RPC tool on `https://web3-discover.vercel.app/api/mcp`

The importer accepts common dataset shapes (`airdrops`, `data`, `projects`, `items`) and maps project, chain, description, status, and official website fields. Rows without a name or a valid HTTP(S) project URL are discarded. A multi-chain entry is stored once per chain. `source_key` provides idempotent upserts.

## Initial import / scheduled refresh

From the repository's `backend` directory, run:

```bash
python -m scripts.sync_airdrop_hub
```

Run this command once after deploying the API, then schedule it daily with a Render Cron Job (working directory: `backend`, command above). The app also attempts a one-time import the first time a user opens an empty hub. The in-app refresh action calls `POST /api/airdrops/hub/refresh`; requests are rate-limited to one per five minutes per API process.

## API

- `GET /api/airdrops/hub` — all catalog rows.
- `GET /api/airdrops/hub?chain=Solana` — SQL-filtered chain results.
- `GET /api/airdrops/hub/status` — last import attempt, result count, and upstream error.
- `POST /api/airdrops/hub/refresh` — retry the upstream import.

A source outage does not delete existing catalog records. The API returns an explicit error on refresh failure, and the app shows the error with a retry action.
