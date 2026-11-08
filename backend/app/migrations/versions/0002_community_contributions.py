"""Add user-confirmed community contribution ledger and atomic aggregates.

Revision ID: 0002_community_contributions
Revises: 0001_bootstrap
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0002_community_contributions"
down_revision = "0001_bootstrap"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "community_project_stats",
        sa.Column("project_official_id", sa.String(length=120), nullable=False),
        sa.Column("total_gas_cost_usd", sa.Numeric(20, 8), nullable=False, server_default="0"),
        sa.Column("total_actual_reward_usd", sa.Numeric(20, 8), nullable=False, server_default="0"),
        sa.Column("total_interactions", sa.BigInteger(), nullable=False, server_default="0"),
        sa.Column("contribution_count", sa.BigInteger(), nullable=False, server_default="0"),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.PrimaryKeyConstraint("project_official_id"),
    )
    op.create_table(
        "community_contributions",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("project_official_id", sa.String(length=120), nullable=False),
        sa.Column("gas_cost_usd", sa.Numeric(20, 8), nullable=False),
        sa.Column("actual_reward_usd", sa.Numeric(20, 8), nullable=False),
        sa.Column("interaction_count", sa.BigInteger(), nullable=False, server_default="1"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_community_contributions_user_id", "community_contributions", ["user_id"])
    op.create_index("ix_community_contributions_project_official_id", "community_contributions", ["project_official_id"])


def downgrade() -> None:
    op.drop_index("ix_community_contributions_project_official_id", table_name="community_contributions")
    op.drop_index("ix_community_contributions_user_id", table_name="community_contributions")
    op.drop_table("community_contributions")
    op.drop_table("community_project_stats")
