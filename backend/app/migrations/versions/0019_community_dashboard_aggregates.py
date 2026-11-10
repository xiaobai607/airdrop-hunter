"""Add anonymous contributor and estimated reward fields for public dashboard.

Revision ID: 0019_community_dashboard_aggregates
Revises: 0018_idempotent_project_creation
"""
import sqlalchemy as sa
from alembic import op

revision = "0019_community_dashboard_aggregates"
down_revision = "0018_idempotent_project_creation"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "community_contributions",
        sa.Column("network", sa.String(length=40), nullable=False, server_default="unknown"),
    )
    op.add_column(
        "community_contributions",
        sa.Column("estimated_reward_usd", sa.Numeric(20, 8), nullable=True),
    )
    op.add_column(
        "community_contributions",
        sa.Column("actual_reward_confirmed", sa.Boolean(), nullable=False, server_default=sa.true()),
    )
    op.add_column(
        "community_contributions",
        sa.Column("contributor_fingerprint", sa.String(length=64), nullable=True),
    )
    op.create_index(
        "ix_community_contributions_contributor_fingerprint",
        "community_contributions",
        ["contributor_fingerprint"],
        unique=False,
    )

    op.add_column(
        "community_project_stats",
        sa.Column("total_estimated_reward_usd", sa.Numeric(20, 8), nullable=False, server_default="0"),
    )
    op.add_column(
        "community_project_stats",
        sa.Column("total_reward_usd", sa.Numeric(20, 8), nullable=False, server_default="0"),
    )
    op.execute(
        "UPDATE community_project_stats "
        "SET total_reward_usd = total_actual_reward_usd, total_estimated_reward_usd = 0"
    )


def downgrade() -> None:
    op.drop_column("community_project_stats", "total_reward_usd")
    op.drop_column("community_project_stats", "total_estimated_reward_usd")
    op.drop_index("ix_community_contributions_contributor_fingerprint", table_name="community_contributions")
    op.drop_column("community_contributions", "contributor_fingerprint")
    op.drop_column("community_contributions", "actual_reward_confirmed")
    op.drop_column("community_contributions", "estimated_reward_usd")
    op.drop_column("community_contributions", "network")
