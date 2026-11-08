"""Persist EVM wallet network identity."""
from alembic import op

revision = "0002_wallet_network_identity"
down_revision = "0001_bootstrap"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("ALTER TABLE wallets DROP CONSTRAINT IF EXISTS uq_wallets_user_address")
    op.execute("DROP INDEX IF EXISTS uq_wallets_user_address")
    op.create_unique_constraint("uq_wallets_user_address_chain", "wallets", ["user_id", "address", "chain"])


def downgrade() -> None:
    op.drop_constraint("uq_wallets_user_address_chain", "wallets", type_="unique")
    op.create_unique_constraint("uq_wallets_user_address", "wallets", ["user_id", "address"])
