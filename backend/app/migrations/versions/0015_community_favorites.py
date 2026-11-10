"""Persist account-scoped community favorite states and deletion tombstones.

Revision ID: 0015_community_favorites
Revises: 0014_discovery_source_status
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0015_community_favorites"
down_revision = "0014_discovery_source_status"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "community_favorites",
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("project_official_id", sa.String(length=120), nullable=False),
        sa.Column("project_name", sa.String(length=180), nullable=False),
        sa.Column("is_favorite", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("updated_at", sa.BigInteger(), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("user_id", "project_official_id"),
    )


def downgrade() -> None:
    op.drop_table("community_favorites")
