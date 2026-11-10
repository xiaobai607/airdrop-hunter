"""Add tags and notes to user-owned wallets.

Revision ID: 0007_wallet_metadata
Revises: 0006_community_ratings
"""
from alembic import op
import sqlalchemy as sa

revision = "0007_wallet_metadata"
down_revision = "0006_community_ratings"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "wallets",
        sa.Column("tags", sa.JSON(), nullable=False, server_default=sa.text("'[]'")),
    )
    op.add_column(
        "wallets",
        sa.Column("notes", sa.String(length=500), nullable=False, server_default=""),
    )


def downgrade() -> None:
    op.drop_column("wallets", "notes")
    op.drop_column("wallets", "tags")
