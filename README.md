# Airdrop Hunter

Airdrop Hunter is an Android-first workspace for discovering airdrop projects, organizing public wallet addresses, tracking project tasks, recording Gas and realized rewards, and viewing anonymous community project statistics.

The authoritative product scope, privacy rules, confirmed UI behavior, implementation status, and next development order live in **[Product Requirements & Progress Ledger](docs/PRODUCT_REQUIREMENTS.md)**. That document is the single source of truth; this README is only a repository and setup guide.

## Product boundaries

- Guest mode is fully usable. Guest personal ledgers and addresses stay in app-private local storage; they are not uploaded as a private cloud backup.
- Logged-in users can sync their own detailed personal ledger to their account-private cloud storage and restore it on another device.
- Both guests and logged-in users may contribute eligible, user-confirmed Gas, actual rewards, and participation totals to public community statistics. Public contributions are anonymous aggregates only.
- Wallet support is read-only. The app does not collect seed phrases/private keys or custody assets, sign transactions, transfer tokens, swap, or approve contracts.
- Public micro-community project discovery, project statistics, rankings, and star ratings are in scope. Chat, groups, DMs, mentoring, and teaching/social features are deferred.
- Project discovery is free-first: stable public APIs, permitted RSS/Atom feeds, and user submissions; no paid data APIs or arbitrary-site scraping.

## Repository layout

- `android/`: Kotlin + Jetpack Compose Android app.
- `backend/`: FastAPI service, SQLAlchemy models, and Alembic migrations.
- `database/`: PostgreSQL schema and migration notes.
- `docs/`: product requirements, progress ledger, development and security notes.
- `.github/workflows/`: Android build/backend tests and optional static community snapshot publishing.

## Quick start

### Android

Requires JDK 17 and Android SDK. From `android/`:

```bash
gradle :app:assembleDebug
```

GitHub Actions builds the debug APK and runs Android unit tests on pushes and pull requests.

### Backend

Requires Python 3.11+ and PostgreSQL:

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
alembic upgrade head
uvicorn app.main:app --reload
```

Health endpoint: `GET /health`. Review the migration rules in [Development Guide](docs/DEVELOPMENT.md) before applying schema changes.

## Configuration and secrets

Set `DATABASE_URL` to a PostgreSQL connection URL. Production deployments must provide it explicitly. Do not commit API keys, wallet seed phrases, private keys, access tokens, or production credentials. Provider credentials belong in backend environment variables or GitHub Actions secrets. The Android APK must not contain server/provider secrets.

The community static snapshot publisher remains disabled until its object-storage/CDN settings and database access are configured. See the product requirements for the exact variables and activation conditions.

## Current implementation boundaries

- Core Android/backend code has CI coverage, but real-device acceptance is still required for WalletConnect/WebView behavior, transaction import, guest data clearing, account switching, and cross-device private-ledger restore.
- Community snapshot generation, Room caching, and CDN client code are merged; production CDN/R2 and scheduled publishing are not yet configured.
- The current public contribution schema stores USD-denominated totals. Token-by-token public amount aggregation is a future schema extension; unlike tokens must never be summed as if they were the same currency.
- Provider integrations are enabled individually only after checking free access, terms, quotas, and reliability.
- Push notifications, production backup/restore verification, alerting, retention, and incident response remain operational work.
