"""Add review state to discovery candidates.

Revision ID: 0011_candidate_review
Revises: 0010_rss_candidates
"""
from alembic import op
import sqlalchemy as sa

revision = "0011_candidate_review"
down_revision = "0010_rss_candidates"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "airdrop_discovery_candidates",
        sa.Column("review_status", sa.String(length=20), server_default="pending", nullable=False),
    )
    op.add_column(
        "airdrop_discovery_candidates",
        sa.Column("reviewed_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.add_column(
        "airdrop_discovery_candidates",
        sa.Column("reviewed_catalog_id", sa.String(length=120), nullable=True),
    )
    op.create_index(
        "ix_airdrop_discovery_candidates_review_status",
        "airdrop_discovery_candidates",
        ["review_status"],
    )


def downgrade() -> None:
    op.drop_index("ix_airdrop_discovery_candidates_review_status", table_name="airdrop_discovery_candidates")
    op.drop_column("airdrop_discovery_candidates", "reviewed_catalog_id")
    op.drop_column("airdrop_discovery_candidates", "reviewed_at")
    op.drop_column("airdrop_discovery_candidates", "review_status")
