"""Track the provider used for consecutive low-heat observations.

Revision ID: 0022_campaign_heat_source
Revises: 0021_airdrop_campaigns
"""
from alembic import op
import sqlalchemy as sa

revision = "0022_campaign_heat_source"
down_revision = "0021_airdrop_campaigns"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "airdrop_campaigns",
        sa.Column("last_heat_source_name", sa.String(length=120), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("airdrop_campaigns", "last_heat_source_name")
