"""Allow unknown expected Gas estimates in private ledger rows.

Revision ID: 0016_nullable_expected_gas
Revises: 0015_community_favorites
"""
import sqlalchemy as sa
from alembic import op

revision = "0016_nullable_expected_gas"
down_revision = "0015_community_favorites"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.alter_column(
        "personal_ledger_entries",
        "expected_gas_cost_usd",
        existing_type=sa.Numeric(20, 8),
        nullable=True,
        server_default=None,
    )


def downgrade() -> None:
    op.execute(
        "UPDATE personal_ledger_entries SET expected_gas_cost_usd = 0 "
        "WHERE expected_gas_cost_usd IS NULL"
    )
    op.alter_column(
        "personal_ledger_entries",
        "expected_gas_cost_usd",
        existing_type=sa.Numeric(20, 8),
        nullable=False,
        server_default="0",
    )
