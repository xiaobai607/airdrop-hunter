# Database migrations

PostgreSQL is the primary database. Schema changes are managed with Alembic.

## Current baseline

- Revision: `0001_bootstrap`.
- The first revision creates missing tables from the current SQLAlchemy metadata.
- It also applies the idempotent compatibility upgrades previously executed at
  FastAPI startup, so an existing Render database can be adopted without
  dropping user data.
- The baseline is intentionally non-destructive and has no downgrade operation.

## Running migrations

From `backend/`:

```bash
alembic current
alembic upgrade head
```

The API runs `alembic upgrade head` during application startup, before serving
requests. The command executes in a worker thread so it does not block FastAPI's
event loop. Future schema changes must be added as new numbered revisions;
do not edit an already deployed revision.

## Data invariants

- Store public wallet addresses only; never store seed phrases or private keys.
- Keep estimated rewards separate from user-confirmed realized results.
- Record source, fetched_at, and verification status for imported data.
- Make transaction ingestion idempotent using chain + transaction hash.
- Enforce user-level data isolation before exposing account-specific endpoints.
