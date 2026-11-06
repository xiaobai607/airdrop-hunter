# Database

PostgreSQL is the planned primary database. The first bootstrap intentionally
does not connect to a database yet.

Initial domain model to implement in migrations:

- users and user_preferences
- projects and campaigns (one project may have multiple campaigns)
- project_sources and source_sync_runs
- chains, wallets, wallet_groups, wallet_campaigns
- tasks, task_steps, task_progress
- points_snapshots
- transactions, expenses, claims, realized_income
- notifications and risk_findings
- audit_logs

Important invariants:

- Store public wallet addresses only; never store seed phrases or private keys.
- Keep estimated rewards separate from user-confirmed realized results.
- Record source, fetched_at, and verification status for imported data.
- Make transaction ingestion idempotent using chain + transaction hash.
- Enforce user-level data isolation before exposing account-specific endpoints.

Alembic migrations and SQLAlchemy models will be added after the API/domain
contracts are reviewed.
