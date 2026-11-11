"""Add structured airdrop discovery metadata to the official catalog.

Revision ID: 0020_airdrop_filters
Revises: 0019_comm_dash
"""
from alembic import op
import sqlalchemy as sa

revision = "0020_airdrop_filters"
down_revision = "0019_comm_dash"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("community_project_catalog", sa.Column(
        "supported_chains", sa.JSON(), nullable=False, server_default=sa.text("'[]'")))
    op.add_column("community_project_catalog", sa.Column(
        "categories", sa.JSON(), nullable=False, server_default=sa.text("'[]'")))
    op.add_column("community_project_catalog", sa.Column(
        "participation_requirements", sa.JSON(), nullable=False, server_default=sa.text("'[]'")))
    op.add_column("community_project_catalog", sa.Column(
        "funding_requirement", sa.String(length=32), nullable=False, server_default="unknown"))
    op.add_column("community_project_catalog", sa.Column(
        "campaign_stage", sa.String(length=32), nullable=False, server_default="unknown"))
    op.add_column("community_project_catalog", sa.Column(
        "reward_types", sa.JSON(), nullable=False, server_default=sa.text("'[]'")))
    op.add_column("community_project_catalog", sa.Column(
        "risk_flags", sa.JSON(), nullable=False, server_default=sa.text("'[]'")))
    op.add_column("community_project_catalog", sa.Column(
        "effort_level", sa.String(length=32), nullable=False, server_default="unknown"))
    op.add_column("community_project_catalog", sa.Column(
        "criteria_source_url", sa.String(length=1000), nullable=False, server_default=""))
    op.add_column("community_project_catalog", sa.Column(
        "metadata_verified_at", sa.DateTime(timezone=True), nullable=True))
    op.add_column("community_project_catalog", sa.Column(
        "metadata_note", sa.Text(), nullable=False, server_default=""))

    # These seven rows represent ecosystems, not individual campaigns.
    # Only their primary network and broad Layer 2 category are sufficiently
    # established by the existing curated directory. All campaign-specific
    # requirements, costs, stages, rewards and risks deliberately remain unknown.
    updates = {
        "linea": ["linea"],
        "scroll": ["scroll"],
        "zksync": ["zksync-era"],
        "starknet": ["starknet"],
        "base": ["base"],
        "arbitrum": ["arbitrum-one", "arbitrum-nova"],
        "optimism": ["op-mainnet"],
    }
    for project_id, chains in updates.items():
        statement = sa.text(
            "UPDATE community_project_catalog "
            "SET supported_chains = :chains, categories = :categories "
            "WHERE project_official_id = :project_id"
        ).bindparams(
            sa.bindparam("chains", type_=sa.JSON()),
            sa.bindparam("categories", type_=sa.JSON()),
        )
        op.get_bind().execute(statement, {
            "chains": chains,
            "categories": ["layer2"],
            "project_id": project_id,
        })


def downgrade() -> None:
    for column in (
        "metadata_note", "metadata_verified_at", "criteria_source_url",
        "effort_level", "risk_flags", "reward_types", "campaign_stage",
        "funding_requirement", "participation_requirements", "categories",
        "supported_chains",
    ):
        op.drop_column("community_project_catalog", column)
