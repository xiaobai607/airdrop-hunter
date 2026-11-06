# Development guide

## Principles

1. Integrate mature providers when their APIs, permissions, quotas, and terms fit.
2. Build the unified project/campaign/task/wallet/points/earnings experience ourselves.
3. Treat provider data as sourced and time-stamped; distinguish verified facts from estimates.
4. Keep the first release small and inexpensive. Prefer free tiers and cache responsibly.
5. Never store wallet secrets or execute transactions on behalf of the user.

## Local checks

Backend:

```bash
cd backend
python -m pip install -e ".[dev]"
pytest
uvicorn app.main:app --reload
```

Android:

```bash
cd android
gradle :app:assembleDebug
```

## Before production

- Add authentication and per-user authorization.
- Add PostgreSQL models and Alembic migrations.
- Add provider adapters only after checking official API documentation and terms.
- Add rate limits, retries with backoff, caching, observability, and sync-run history.
- Add security review for external URLs, contract/address inputs, and imported content.
- Configure secrets in the deployment environment, never in source control.
