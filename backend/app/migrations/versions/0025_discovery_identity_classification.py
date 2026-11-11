"""Persist conservative project and campaign identity decisions.
Revision ID: 0025_discovery_identity_classification
Revises: 0024_discovery_filter_decisions
"""
from alembic import op
import sqlalchemy as sa
revision = "0025_discovery_identity_classification"
down_revision = "0024_discovery_filter_decisions"
branch_labels = None
depends_on = None

def upgrade() -> None:
    for table in ("airdrop_hub_projects", "airdrop_discovery_candidates"):
        op.add_column(table, sa.Column("project_identity_key", sa.String(64), nullable=True))
        op.add_column(table, sa.Column("campaign_identity_key", sa.String(64), nullable=True))
        op.add_column(table, sa.Column("identity_status", sa.String(48), nullable=False, server_default="insufficient_identity_evidence"))
        op.add_column(table, sa.Column("identity_evidence", sa.JSON(), nullable=False, server_default=sa.text("'{}'")))
        op.add_column(table, sa.Column("matched_source_key", sa.String(240), nullable=True))
        op.create_index(f"ix_{table}_project_identity_key", table, ["project_identity_key"])
        op.create_index(f"ix_{table}_campaign_identity_key", table, ["campaign_identity_key"])
        op.create_index(f"ix_{table}_identity_status", table, ["identity_status"])

def downgrade() -> None:
    for table in ("airdrop_discovery_candidates", "airdrop_hub_projects"):
        op.drop_index(f"ix_{table}_identity_status", table_name=table)
        op.drop_index(f"ix_{table}_campaign_identity_key", table_name=table)
        op.drop_index(f"ix_{table}_project_identity_key", table_name=table)
        op.drop_column(table, "matched_source_key")
        op.drop_column(table, "identity_evidence")
        op.drop_column(table, "identity_status")
        op.drop_column(table, "campaign_identity_key")
        op.drop_column(table, "project_identity_key")
