"""Persist source trust and safe-publication state for the public airdrop catalog.

Revision ID: 0023_trusted_catalog_source
Revises: 0022_campaign_heat_source
"""
from alembic import op
import sqlalchemy as sa

revision = "0023_trusted_catalog_source"
down_revision = "0022_campaign_heat_source"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "airdrop_hub_projects",
        sa.Column(
            "trust_level",
            sa.String(length=24),
            nullable=False,
            server_default="TRUST_LEVEL_0",
        ),
    )
    op.add_column(
        "airdrop_hub_projects",
        sa.Column(
            "publication_status",
            sa.String(length=24),
            nullable=False,
            server_default="OBSERVATION",
        ),
    )
    op.create_index(
        "ix_airdrop_hub_projects_publication_status",
        "airdrop_hub_projects",
        ["publication_status"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        "ix_airdrop_hub_projects_publication_status",
        table_name="airdrop_hub_projects",
    )
    op.drop_column("airdrop_hub_projects", "publication_status")
    op.drop_column("airdrop_hub_projects", "trust_level")
