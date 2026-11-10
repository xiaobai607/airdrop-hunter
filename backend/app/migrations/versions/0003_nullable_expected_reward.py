"""Allow unknown expected reward estimates in personal ledger.

Revision ID: 0003_nullable_expected_reward
Revises: 0002_personal_ledger
"""
import sqlalchemy as sa
from alembic import op

revision = "0003_nullable_expected_reward"
down_revision = "0002_personal_ledger"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.alter_column(
        "personal_ledger_entries",
        "expected_reward_usd",
        existing_type=sa.Numeric(20, 8),
        nullable=True,
        server_default=None,
    )


def downgrade() -> None:
    op.execute(
        "UPDATE personal_ledger_entries SET expected_reward_usd = 0 "
        "WHERE expected_reward_usd IS NULL"
    )
    op.alter_column(
        "personal_ledger_entries",
        "expected_reward_usd",
        existing_type=sa.Numeric(20, 8),
        nullable=False,
        server_default="0",
    )
