# Airdrop Hunter

Airdrop discovery and personal campaign management for Android.

> First-stage bootstrap. This repository is a development skeleton, not a production-ready wallet or trading application.

## Product scope (v1)

- Discover and organize airdrop projects and campaigns.
- Track tasks and manually confirm completion.
- Organize multiple public wallet addresses (non-custodial; never store seed phrases or private keys).
- Record points snapshots, expenses, claims, and realized income.
- Monitor supported public/authorized data sources and send reminders.
- Surface security findings and clearly label data sources and freshness.

Community/chat features are intentionally out of scope for v1.

## Repository layout

- `android/`: Kotlin + Jetpack Compose Android app.
- `backend/`: FastAPI service and provider connector interfaces.
- `database/`: PostgreSQL schema and migration notes.
- `docs/`: development and security notes.
- `.github/workflows/`: CI build.

## Quick start

### Android

Requires JDK 17 and Android SDK. From `android/`:

```bash
gradle :app:assembleDebug
```

The GitHub Actions workflow builds the debug APK on pushes and pull requests.

### Backend

Requires Python 3.11+:

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
uvicorn app.main:app --reload
```

Health endpoint: `GET /health`.

## Configuration and secrets

Do not commit API keys, wallet seed phrases, private keys, access tokens, or production credentials. Provider credentials belong in backend environment variables or GitHub Actions secrets. The Android APK must not contain server/provider secrets.

## Current limitations

- No live provider integrations are enabled yet.
- No wallet signing, custody, transaction execution, or automated on-chain actions.
- Authentication, user isolation, production database wiring, and push notifications are not implemented in this bootstrap.
- Provider integrations must be checked for API access, quotas, terms, and commercial-use permissions before activation.
