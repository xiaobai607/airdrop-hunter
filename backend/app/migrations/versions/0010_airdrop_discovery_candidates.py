"""Store normalized RSS/Atom discovery candidates separately from verified projects.

Revision ID: 0010_airdrop_discovery_candidates
Revises: 0009_dynamic_community_catalog
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0010_airdrop_discovery_candidates"
down_revision = "0009_dynamic_community_catalog"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "airdrop_discovery_candidates",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("source_key", sa.String(length=64), nullable=False),
        sa.Column("source_name", sa.String(length=120), nullable=False),
        sa.Column("source_url", sa.String(length=1000), nullable=False),
        sa.Column("title", sa.String(length=300), nullable=False),
        sa.Column("item_url", sa.String(length=1000), nullable=False),
        sa.Column("summary", sa.Text(), nullable=False, server_default=""),
        sa.Column("published_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("first_seen_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("source_key", name="uq_airdrop_discovery_source_key"),
    )
    op.create_index("ix_airdrop_discovery_candidates_source_key", "airdrop_discovery_candidates", ["source_key"], unique=True)
    op.create_index("ix_airdrop_discovery_candidates_title", "airdrop_discovery_candidates", ["title"])


def downgrade() -> None:
    op.drop_index("ix_airdrop_discovery_candidates_title", table_name="airdrop_discovery_candidates")
    op.drop_index("ix_airdrop_discovery_candidates_source_key", table_name="airdrop_discovery_candidates")
    op.drop_table("airdrop_discovery_candidates")
