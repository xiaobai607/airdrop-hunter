"""Add native token asset breakdown to public contribution rows.

Revision ID: 0013_currency_assets
Revises: 0012_public_metrics
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0013_currency_assets"
down_revision = "0012_public_metrics"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "community_contributions",
        sa.Column(
            "asset_breakdown",
            postgresql.JSONB(astext_type=sa.Text()),
            nullable=False,
            server_default=sa.text("'[]'::jsonb"),
        ),
    )


def downgrade() -> None:
    op.drop_column("community_contributions", "asset_breakdown")
