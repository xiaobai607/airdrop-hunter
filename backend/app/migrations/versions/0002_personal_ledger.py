"""Add account-scoped personal ledger records.

Revision ID: 0002_personal_ledger
Revises: 0001_bootstrap
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0002_personal_ledger"
down_revision = "0001_bootstrap"
branch_labels = None
depends_on = None

def upgrade() -> None:
    op.create_table(
        "personal_ledger_entries",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True, nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("client_record_id", sa.String(64), nullable=False),
        sa.Column("project_id", sa.String(120), nullable=True),
        sa.Column("project_name", sa.String(180), nullable=False, server_default=""),
        sa.Column("chain", sa.String(40), nullable=False, server_default="unknown"),
        sa.Column("wallet_address", sa.String(160), nullable=False, server_default=""),
        sa.Column("tx_hash", sa.String(180), nullable=False, server_default=""),
        sa.Column("interaction_method", sa.String(120), nullable=False, server_default=""),
        sa.Column("expected_gas_cost_usd", sa.Numeric(20, 8), nullable=False, server_default="0"),
        sa.Column("expected_reward_usd", sa.Numeric(20, 8), nullable=False, server_default="0"),
        sa.Column("actual_gas_cost_usd", sa.Numeric(20, 8), nullable=False, server_default="0"),
        sa.Column("actual_reward_usd", sa.Numeric(20, 8), nullable=False, server_default="0"),
        sa.Column("occurred_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("extra", postgresql.JSONB(astext_type=sa.Text()), nullable=False, server_default=sa.text("'{}'::jsonb")),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("user_id", "client_record_id", name="uq_personal_ledger_user_client_record"),
    )
    op.create_index("ix_personal_ledger_user_occurred", "personal_ledger_entries", ["user_id", "occurred_at"])
    op.create_index("ix_personal_ledger_user_project", "personal_ledger_entries", ["user_id", "project_id"])

def downgrade() -> None:
    op.drop_index("ix_personal_ledger_user_project", table_name="personal_ledger_entries")
    op.drop_index("ix_personal_ledger_user_occurred", table_name="personal_ledger_entries")
    op.drop_table("personal_ledger_entries")
