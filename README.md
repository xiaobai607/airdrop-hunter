# Airdrop Hunter

Airdrop Hunter is an Android-first app for discovering verified airdrop announcements, organizing projects and tasks, tracking public wallet activity, and recording Gas costs and realized rewards.

The authoritative product scope, privacy rules, confirmed UI behavior, implementation status, and next development order live in **[Product Requirements & Progress Ledger](docs/PRODUCT_REQUIREMENTS.md)**. That document is the single source of truth; this README is only a repository and setup guide.

## Product boundaries

- The primary discovery destination is **空投库**. It displays explicit airdrop campaign/announcement evidence, not a generic token screener or source-monitoring dashboard.
- Current approved discovery sources are Binance public announcements, CoinGecko Demo API, manually reviewed GitHub Raw lists, and DexScreener public API. CoinGecko/GitHub/DexScreener observations are leads only; they are not proof of an active airdrop.
- No paid data APIs, paid scraping, paid proxies, paid trials, or hidden metered fallbacks.
- Personal wallets, detailed personal ledger entries, transaction hashes, private project/task records, and personal P&L remain local-only in this release. Only eligible anonymous/de-identified aggregate contribution data may be sent to the public community service.
- Registration/login and private cloud restore are not current-release features.
- Demo data is isolated from real data and cannot enter real publication or public statistics.
- Wallet support is non-custodial. The app never collects seed phrases/private keys or signs transactions for the user.
- Chat, groups, DMs, mentoring, and teaching/social features are deferred.

## Repository layout

- `android/`: Kotlin + Jetpack Compose Android app.
- `backend/`: FastAPI service, SQLAlchemy models, and Alembic migrations.
- `database/`: PostgreSQL schema and migration notes.
- `docs/`: product requirements, progress ledger, development and security notes.
- `.github/workflows/`: Android/backend validation and optional scheduled publishing workflows.

## Quick start

### Android

Requires JDK 17 and Android SDK. From `android/`:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions runs Android unit tests and builds a debug APK when Android source files change.

### Backend

Requires Python 3.11+ and PostgreSQL:

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
alembic upgrade head
pytest
uvicorn app.main:app --reload
```

Health endpoint: `GET /health`. Review [Development Guide](docs/DEVELOPMENT.md) before changing or applying database migrations.

## Configuration and secrets

Set `DATABASE_URL` for PostgreSQL and `JWT_SECRET_KEY` for server-side signing. Optional discovery settings are documented in `backend/.env.example` and [Airdrop Hub data pipeline](backend/AIRDROP_HUB.md).

- `COINGECKO_DEMO_API_KEY` is optional; without it, CoinGecko discovery remains unavailable.
- `AIRDROP_GITHUB_RAW_SOURCE_APPROVALS` accepts only complete, manually reviewed, license/attribution-approved records pinned to a full commit SHA. A public URL alone is not permission to reuse content.
- `DEXSCREENER_SEARCH_QUERIES` is an optional bounded list of discovery queries.
- Binance public CMS announcements do not require an API key.
- Scheduled production sync remains disabled until the free-use path, source permissions, hosting/database limits, and live-data rendering are verified.

Never commit API keys, wallet secrets, access tokens, or production credentials. Provider credentials belong in backend environment variables or GitHub Actions secrets; the Android APK must not contain provider secrets.

## Current implementation boundaries

- A successful CI run proves only the checks that actually ran. It does not prove physical-device acceptance, current provider availability, production deployment, or backup/restore recovery.
- CoinGecko, GitHub Raw, and DexScreener observations remain internal discovery leads unless separate explicit campaign evidence is obtained.
- The Binance announcement detail page displays the original CMS article body without AI rewriting or invented campaign terms. JavaScript remains disabled in the WebView.
- Community snapshot generation, Room caching, and CDN client code do not imply that production CDN/storage or scheduled publishing is configured.
- Production backup/restore drills, monitoring, retention, incident response, and final release acceptance remain operational work.

## Project status

See the top-level checklist in [Product Requirements & Progress Ledger](docs/PRODUCT_REQUIREMENTS.md) for the current code/CI, real-data, device, production, and release status. PR #135 must remain Draft/Open until the user explicitly authorizes merging.
