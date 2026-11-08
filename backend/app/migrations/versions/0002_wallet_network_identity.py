"""Persist EVM wallet network identity."""
from alembic import op

revision = "0002_wallet_network_identity"
down_revision = "0001_bootstrap"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("ALTER TABLE wallets DROP CONSTRAINT IF EXISTS uq_wallets_user_address")
    op.execute("DROP INDEX IF EXISTS uq_wallets_user_address")
    op.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_wallets_user_address_chain ON wallets (user_id, address, chain)")


def downgrade() -> None:
    op.execute("DROP INDEX IF EXISTS uq_wallets_user_address_chain")
    op.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_wallets_user_address ON wallets (user_id, address)")
