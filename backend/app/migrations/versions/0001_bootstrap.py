"""Bootstrap current schema and adopt databases created by legacy startup DDL.

Revision ID: 0001_bootstrap
Revises:
"""
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql
from sqlalchemy.schema import MetaData, Table, Column, ForeignKey, Index, UniqueConstraint

from alembic import op

revision = "0001_bootstrap"
down_revision = None
branch_labels = None
depends_on = None

# Frozen schema snapshot for this baseline. Do not import live ORM metadata here:
# future model changes must be represented by new Alembic revisions.
metadata = MetaData()

Table(
    "users", metadata,
    Column("id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
    Column("username", sa.String(24), nullable=False),
    Column("uid", sa.String(6), nullable=False),
    Column("email", sa.String(320), nullable=True),
    Column("password_hash", sa.String(255), nullable=True),
    Column("display_name", sa.String(80), nullable=True),
    Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
)
Index("ix_users_username", metadata.tables["users"].c.username, unique=True)
Index("ix_users_uid", metadata.tables["users"].c.uid, unique=True)
Index("ix_users_email", metadata.tables["users"].c.email, unique=True)

Table(
    "projects", metadata,
    Column("id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
    Column("user_id", postgresql.UUID(as_uuid=True), ForeignKey("users.id", name="fk_projects_user_id_users", ondelete="CASCADE"), nullable=True),
    Column("name", sa.String(120), nullable=False),
    Column("description", sa.Text(), nullable=False),
    Column("status", sa.String(24), nullable=False),
    Column("official_url", sa.String(1000), nullable=False),
    Column("claim_url", sa.String(1000), nullable=False),
    Column("twitter_url", sa.String(1000), nullable=False),
    Column("deadline", sa.DateTime(timezone=True), nullable=True),
    Column("expected_income", sa.Numeric(20, 8), nullable=False),
    Column("gas_cost", sa.Numeric(20, 8), nullable=False),
    Column("actual_income", sa.Numeric(20, 8), nullable=False),
    Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
)
Index("ix_projects_name", metadata.tables["projects"].c.name)

Table(
    "airdrops", metadata,
    Column("id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
    Column("user_id", postgresql.UUID(as_uuid=True), ForeignKey("users.id", name="fk_airdrops_user_id_users", ondelete="CASCADE"), nullable=True),
    Column("name", sa.String(160), nullable=False),
    Column("description", sa.Text(), nullable=False),
    Column("category", sa.String(80), nullable=False),
    Column("chain", sa.String(80), nullable=False),
    Column("status", sa.String(24), nullable=False),
    Column("reward_details", sa.Text(), nullable=False),
    Column("eligibility", sa.Text(), nullable=False),
    Column("official_url", sa.String(1000), nullable=False),
    Column("source_name", sa.String(120), nullable=False),
    Column("source_url", sa.String(1000), nullable=False),
    Column("source_type", sa.String(24), nullable=False),
    Column("verification_status", sa.String(24), nullable=False),
    Column("starts_at", sa.DateTime(timezone=True), nullable=True),
    Column("ends_at", sa.DateTime(timezone=True), nullable=True),
    Column("last_checked_at", sa.DateTime(timezone=True), nullable=True),
    Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
)
Index("ix_airdrops_name", metadata.tables["airdrops"].c.name)
Index("ix_airdrops_user_id", metadata.tables["airdrops"].c.user_id)

Table(
    "airdrop_hub_projects", metadata,
    Column("id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
    Column("source_key", sa.String(240), nullable=False),
    Column("name", sa.String(180), nullable=False),
    Column("chain", sa.String(80), nullable=False),
    Column("description", sa.Text(), nullable=False),
    Column("status", sa.String(40), nullable=False),
    Column("link", sa.String(1000), nullable=False),
    Column("source_name", sa.String(120), nullable=False),
    Column("imported_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    UniqueConstraint("source_key", name="uq_airdrop_hub_source_key"),
)
Index("ix_airdrop_hub_projects_source_key", metadata.tables["airdrop_hub_projects"].c.source_key, unique=True)
Index("ix_airdrop_hub_projects_name", metadata.tables["airdrop_hub_projects"].c.name)
Index("ix_airdrop_hub_projects_chain", metadata.tables["airdrop_hub_projects"].c.chain)

Table(
    "wallets", metadata,
    Column("id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
    Column("user_id", postgresql.UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
    Column("public_key", sa.String(120), nullable=False),
    Column("address", sa.String(120), nullable=False),
    Column("chain", sa.String(24), nullable=False),
    Column("network_icon", sa.String(24), nullable=False),
    Column("wallet_name", sa.String(80), nullable=False),
    Column("daily_gas_usd", sa.Numeric(20, 8), nullable=False),
    Column("recent_airdrop_income_usd", sa.Numeric(20, 8), nullable=False),
    Column("last_synced_at", sa.DateTime(timezone=True), nullable=True),
    Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    UniqueConstraint("user_id", "address", name="uq_wallets_user_address"),
)
Index("ix_wallets_user_id", metadata.tables["wallets"].c.user_id)
Index("ix_wallets_user_synced", metadata.tables["wallets"].c.user_id, metadata.tables["wallets"].c.last_synced_at)

Table(
    "coins", metadata,
    Column("id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
    Column("user_id", postgresql.UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
    Column("coinpaprika_id", sa.String(100), nullable=False),
    Column("symbol", sa.String(20), nullable=False),
    Column("name", sa.String(100), nullable=False),
    Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    UniqueConstraint("user_id", "coinpaprika_id", name="uq_coins_user_coinpaprika"),
)
Index("ix_coins_user_id", metadata.tables["coins"].c.user_id)

Table(
    "tasks", metadata,
    Column("id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
    Column("user_id", postgresql.UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
    Column("project_id", postgresql.UUID(as_uuid=True), ForeignKey("projects.id", ondelete="CASCADE"), nullable=False),
    Column("task_name", sa.String(240), nullable=False),
    Column("step_order", sa.Integer(), nullable=False),
    Column("is_completed", sa.Boolean(), nullable=False),
    Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
)
Index("ix_tasks_user_id", metadata.tables["tasks"].c.user_id)
Index("ix_tasks_project_id", metadata.tables["tasks"].c.project_id)
Index("ix_tasks_user_project_order", metadata.tables["tasks"].c.user_id, metadata.tables["tasks"].c.project_id, metadata.tables["tasks"].c.step_order)


LEGACY_SCHEMA_UPGRADES = (
    "ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(24)",
    "ALTER TABLE users ADD COLUMN IF NOT EXISTS uid VARCHAR(6)",
    "ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255)",
    "UPDATE users SET username = 'legacy_' || substr(replace(id::text, '-', ''), 1, 17) WHERE username IS NULL OR btrim(username) = ''",
    """WITH numbered AS (
        SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS n
        FROM users WHERE uid IS NULL OR btrim(uid) = ''
    ) UPDATE users AS u SET uid = lpad((99999 + numbered.n)::text, 6, '0')
      FROM numbered WHERE u.id = numbered.id""",
    "ALTER TABLE users ALTER COLUMN username SET NOT NULL",
    "ALTER TABLE users ALTER COLUMN uid SET NOT NULL",
    "ALTER TABLE users ALTER COLUMN email DROP NOT NULL",
    "CREATE UNIQUE INDEX IF NOT EXISTS ix_users_username ON users (username)",
    "CREATE UNIQUE INDEX IF NOT EXISTS ix_users_uid ON users (uid)",
    "ALTER TABLE projects ADD COLUMN IF NOT EXISTS user_id UUID",
    "ALTER TABLE airdrops ADD COLUMN IF NOT EXISTS user_id UUID",
    "ALTER TABLE projects ADD COLUMN IF NOT EXISTS official_url VARCHAR(1000) NOT NULL DEFAULT ''",
    "ALTER TABLE projects ADD COLUMN IF NOT EXISTS claim_url VARCHAR(1000) NOT NULL DEFAULT ''",
    "ALTER TABLE projects ADD COLUMN IF NOT EXISTS twitter_url VARCHAR(1000) NOT NULL DEFAULT ''",
    "ALTER TABLE projects ADD COLUMN IF NOT EXISTS deadline TIMESTAMPTZ NULL",
    "ALTER TABLE projects ADD COLUMN IF NOT EXISTS expected_income NUMERIC(20,8) NOT NULL DEFAULT 0",
    "ALTER TABLE projects ADD COLUMN IF NOT EXISTS gas_cost NUMERIC(20,8) NOT NULL DEFAULT 0",
    "ALTER TABLE projects ADD COLUMN IF NOT EXISTS actual_income NUMERIC(20,8) NOT NULL DEFAULT 0",
    "ALTER TABLE wallets ADD COLUMN IF NOT EXISTS address VARCHAR(120) NOT NULL DEFAULT ''",
    "ALTER TABLE wallets ADD COLUMN IF NOT EXISTS wallet_name VARCHAR(80) NOT NULL DEFAULT 'Wallet'",
    "ALTER TABLE wallets ADD COLUMN IF NOT EXISTS network_icon VARCHAR(24) NOT NULL DEFAULT '?'",
    "UPDATE wallets SET address = public_key WHERE address = ''",
    "UPDATE wallets SET wallet_name = 'Wallet' WHERE wallet_name = ''",
    "ALTER TABLE wallets ALTER COLUMN address SET NOT NULL",
    "CREATE UNIQUE INDEX IF NOT EXISTS uq_wallets_user_address ON wallets(user_id, address)",
    "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS step_order INTEGER",
    """WITH ranked AS (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id, project_id ORDER BY created_at, id) AS position
        FROM tasks WHERE step_order IS NULL
    ) UPDATE tasks SET step_order = ranked.position FROM ranked WHERE tasks.id = ranked.id""",
    "ALTER TABLE tasks ALTER COLUMN step_order SET DEFAULT 1",
    "ALTER TABLE tasks ALTER COLUMN step_order SET NOT NULL",
    "CREATE INDEX IF NOT EXISTS ix_projects_user_id ON projects(user_id)",
    "CREATE INDEX IF NOT EXISTS ix_tasks_user_id ON tasks(user_id)",
    "CREATE INDEX IF NOT EXISTS ix_tasks_project_id ON tasks(project_id)",
    "CREATE INDEX IF NOT EXISTS ix_tasks_user_project_order ON tasks(user_id, project_id, step_order)",
    "CREATE INDEX IF NOT EXISTS ix_coins_user_id ON coins(user_id)",
    "CREATE INDEX IF NOT EXISTS ix_wallets_user_id ON wallets(user_id)",
)


def upgrade() -> None:
    bind = op.get_bind()
    # This frozen baseline creates only missing tables; existing Render tables remain intact.
    metadata.create_all(bind=bind)
    for statement in LEGACY_SCHEMA_UPGRADES:
        op.execute(statement)
    op.execute("""
        DO $$ BEGIN
          IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_projects_user_id_users') THEN
            ALTER TABLE projects ADD CONSTRAINT fk_projects_user_id_users
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
          END IF;
          IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_airdrops_user_id_users') THEN
            ALTER TABLE airdrops ADD CONSTRAINT fk_airdrops_user_id_users
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
          END IF;
        END $$;
    """)
    op.execute("CREATE INDEX IF NOT EXISTS ix_airdrops_user_id ON airdrops(user_id)")


def downgrade() -> None:
    # This baseline adopts production data and is intentionally non-destructive.
    # Future revisions must provide explicit, reversible changes where possible.
    pass
