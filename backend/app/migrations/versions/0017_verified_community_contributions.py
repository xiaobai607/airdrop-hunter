"""Require verified chain transaction identity for public contributions.

Revision ID: 0017_verified_community_contributions
Revises: 0016_nullable_expected_gas
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0017_verified_contrib"
down_revision = "0016_nullable_expected_gas"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "community_contributions",
        sa.Column("tx_fingerprint", sa.String(length=64), nullable=True),
    )
    op.add_column(
        "community_contributions",
        sa.Column("verification_tier", sa.String(length=32), nullable=False, server_default="user_reported"),
    )
    op.add_column(
        "community_contributions",
        sa.Column("verification_metadata", postgresql.JSONB(astext_type=sa.Text()), nullable=False, server_default=sa.text("'{}'::jsonb")),
    )
    # Existing rows predate transaction verification and remain explicitly user-reported.
    op.execute("UPDATE community_contributions SET verification_tier = 'user_reported'")

    op.create_index(
        "uq_community_contributions_tx_fingerprint",
        "community_contributions",
        ["tx_fingerprint"],
        unique=True,
    )


def downgrade() -> None:
    op.drop_index("uq_community_contributions_tx_fingerprint", table_name="community_contributions")
    op.drop_column("community_contributions", "verification_metadata")
    op.drop_column("community_contributions", "verification_tier")
    op.drop_column("community_contributions", "tx_fingerprint")
