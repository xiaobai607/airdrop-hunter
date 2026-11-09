# Airdrop Hub data import

The public airdrop catalog is stored in Neon PostgreSQL. Android reads normalized records from the backend; it does not scrape third-party pages.

## Existing structured source

1. Primary: `https://web3-discover.vercel.app/api/airdrops.json`
2. Fallback: the public `list_active_airdrops` JSON-RPC tool on `https://web3-discover.vercel.app/api/mcp`

The importer maps project, chain, description, status, and source project URL fields. Rows without a name or valid HTTP(S) link are discarded. A multi-chain entry is stored once per chain. `source_key` provides idempotent upserts.

## RSS/Atom discovery candidates

The scheduled importer also supports explicitly configured RSS 2.0 and Atom feeds. These records are stored in the separate `airdrop_discovery_candidates` table and exposed as candidates; they are NOT silently promoted into the verified community project catalog and do not create Gas, reward, rating, or participation metrics.

Configure Render environment variables:

- `AIRDROP_RSS_FEEDS`: semicolon-separated `name=https://host/path.xml` entries.
- `AIRDROP_RSS_ALLOWED_HOSTS`: comma-separated exact hostnames permitted for feed retrieval, e.g. `feeds.example,feeds.other.example`.

Every feed must use HTTPS and its exact hostname must be allowlisted. Redirects are disabled, each feed is isolated from failures in other feeds, response size is capped, and malformed/unsafe feed entries are ignored. Configure only feeds whose availability and terms permit automated retrieval. RSSHub can be used as a feed endpoint when you operate or trust the instance and have checked its terms; it is not assumed to support every website.

The importer stores title, item URL, summary, feed attribution, publication time, and first/last-seen times. It does not crawl linked pages or infer that a feed item is an official project website.

## Initial import / scheduled refresh

From the repository's `backend` directory, run:

```bash
python -m scripts.sync_airdrop_hub
```

Schedule this command daily as a Render Cron Job (working directory: `backend`). It attempts the structured source and configured RSS feeds independently, so one provider failure does not block the other. If no RSS feeds are configured, RSS intake safely does nothing.

## API

- `GET /api/airdrops/hub` — structured airdrop catalog.
- `GET /api/airdrops/hub?chain=Solana` — SQL-filtered chain results.
- `GET /api/airdrops/hub/status` — structured source sync status.
- `POST /api/airdrops/hub/refresh` — retry the structured source import.
- `GET /api/airdrops/discovery/candidates` — normalized RSS/Atom candidate list.
- `GET /api/airdrops/discovery/candidates?q=testnet&limit=50` — title search.

The RSS candidate import currently runs through the scheduled backend script. Existing catalog rows are not deleted when a source is unavailable.
