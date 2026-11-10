"""Add curated dynamic community project catalog.

Revision ID: 0009_dynamic_community_catalog
Revises: 0008_project_wallet_assignments
"""
from alembic import op
import sqlalchemy as sa

revision = "0009_dynamic_community_catalog"
down_revision = "0008_project_wallet_assignments"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "community_project_catalog",
        sa.Column("project_official_id", sa.String(length=120), primary_key=True),
        sa.Column("name", sa.String(length=180), nullable=False),
        sa.Column("chain", sa.String(length=120), nullable=False, server_default="Unknown"),
        sa.Column("logo", sa.String(length=12), nullable=False, server_default=""),
        sa.Column("description", sa.Text(), nullable=False, server_default=""),
        sa.Column("tags", sa.JSON(), nullable=False, server_default="[]"),
        sa.Column("website_url", sa.String(length=1000), nullable=False, server_default=""),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("sort_order", sa.Integer(), nullable=False, server_default="100"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_community_project_catalog_name", "community_project_catalog", ["name"])
    op.create_index("ix_community_project_catalog_chain", "community_project_catalog", ["chain"])
    op.create_index("ix_community_project_catalog_is_active", "community_project_catalog", ["is_active"])

    projects = [
        ("linea", "Linea", "Consensys zkEVM", "L", ["L2", "零撸", "明牌空投"], 10),
        ("scroll", "Scroll", "zkEVM", "S", ["L2", "需测试网", "明牌空投"], 20),
        ("zksync", "zkSync", "ZK Rollup", "Z", ["L2", "高Gas", "明牌空投"], 30),
        ("starknet", "Starknet", "Validity Rollup", "✦", ["L2", "需测试网", "高Gas"], 40),
        ("base", "Base", "Ethereum L2", "B", ["L2", "零撸", "高Gas"], 50),
        ("arbitrum", "Arbitrum", "Ethereum L2", "A", ["L2", "高Gas"], 60),
        ("optimism", "Optimism", "Superchain", "O", ["L2", "零撸", "明牌空投"], 70),
    ]
    table = sa.table(
        "community_project_catalog",
        sa.column("project_official_id", sa.String),
        sa.column("name", sa.String),
        sa.column("chain", sa.String),
        sa.column("logo", sa.String),
        sa.column("tags", sa.JSON),
        sa.column("is_active", sa.Boolean),
        sa.column("sort_order", sa.Integer),
    )
    op.bulk_insert(table, [
        {"project_official_id": key, "name": name, "chain": chain, "logo": logo,
         "tags": tags, "is_active": True, "sort_order": order}
        for key, name, chain, logo, tags, order in projects
    ])


def downgrade() -> None:
    op.drop_index("ix_community_project_catalog_is_active", table_name="community_project_catalog")
    op.drop_index("ix_community_project_catalog_chain", table_name="community_project_catalog")
    op.drop_index("ix_community_project_catalog_name", table_name="community_project_catalog")
    op.drop_table("community_project_catalog")
