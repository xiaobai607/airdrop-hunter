"""Bootstrap current schema and adopt databases created by legacy startup DDL.

Revision ID: 0001_bootstrap
Revises:
"""
from alembic import op

from app.database import Base
from app import models  # noqa: F401

revision = "0001_bootstrap"
down_revision = None
branch_labels = None
depends_on = None

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
    # create_all creates only missing tables; existing Render tables are left intact.
    Base.metadata.create_all(bind=bind)
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
