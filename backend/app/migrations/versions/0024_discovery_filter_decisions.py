"""Persist centralized discovery-filter decisions.
Revision ID: 0024_discovery_filter_decisions
Revises: 0023_trusted_catalog_source
"""
from alembic import op
import sqlalchemy as sa
revision = "0024_discovery_filter_decisions"
down_revision = "0023_trusted_catalog_source"
branch_labels = None
depends_on = None

def upgrade() -> None:
    op.add_column("airdrop_discovery_candidates", sa.Column("filter_status", sa.String(32), nullable=False, server_default="not_evaluated"))
    op.add_column("airdrop_discovery_candidates", sa.Column("filter_reasons", sa.JSON(), nullable=False, server_default=sa.text("'[]'")))
    op.add_column("airdrop_discovery_candidates", sa.Column("filter_metadata", sa.JSON(), nullable=False, server_default=sa.text("'{}'")))
    op.create_index("ix_airdrop_discovery_candidates_filter_status", "airdrop_discovery_candidates", ["filter_status"])
    op.add_column("airdrop_hub_projects", sa.Column("filter_status", sa.String(32), nullable=False, server_default="not_evaluated"))
    op.add_column("airdrop_hub_projects", sa.Column("filter_reasons", sa.JSON(), nullable=False, server_default=sa.text("'[]'")))
    op.add_column("airdrop_hub_projects", sa.Column("filter_metadata", sa.JSON(), nullable=False, server_default=sa.text("'{}'")))
    op.create_index("ix_airdrop_hub_projects_filter_status", "airdrop_hub_projects", ["filter_status"])

def downgrade() -> None:
    op.drop_index("ix_airdrop_hub_projects_filter_status", table_name="airdrop_hub_projects")
    op.drop_column("airdrop_hub_projects", "filter_metadata")
    op.drop_column("airdrop_hub_projects", "filter_reasons")
    op.drop_column("airdrop_hub_projects", "filter_status")
    op.drop_index("ix_airdrop_discovery_candidates_filter_status", table_name="airdrop_discovery_candidates")
    op.drop_column("airdrop_discovery_candidates", "filter_metadata")
    op.drop_column("airdrop_discovery_candidates", "filter_reasons")
    op.drop_column("airdrop_discovery_candidates", "filter_status")
