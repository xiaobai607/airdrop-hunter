"""Remove public project participation counters and activity metrics.

Revision ID: 0012_public_metrics
Revises: 0011_candidate_review
"""
from alembic import op
import sqlalchemy as sa

revision = "0012_public_metrics"
down_revision = "0011_candidate_review"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.drop_column("community_contributions", "interaction_count")
    op.drop_column("community_project_stats", "total_interactions")
    op.drop_column("community_project_stats", "contribution_count")


def downgrade() -> None:
    # Removed counters cannot be reconstructed; restore empty defaults only.
    op.add_column(
        "community_project_stats",
        sa.Column("contribution_count", sa.BigInteger(), nullable=False, server_default="0"),
    )
    op.add_column(
        "community_project_stats",
        sa.Column("total_interactions", sa.BigInteger(), nullable=False, server_default="0"),
    )
    op.add_column(
        "community_contributions",
        sa.Column("interaction_count", sa.BigInteger(), nullable=False, server_default="1"),
    )
