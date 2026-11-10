"""Persist discovery source sync health.

Revision ID: 0014_discovery_source_status
Revises: 0013_currency_assets
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0014_discovery_source_status"
down_revision = "0013_currency_assets"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "airdrop_discovery_source_status",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("source_key", sa.String(length=64), nullable=False),
        sa.Column("source_name", sa.String(length=120), nullable=False),
        sa.Column("source_url", sa.String(length=1000), nullable=False),
        sa.Column("last_attempt_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_success_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("last_status", sa.String(length=20), server_default="pending", nullable=False),
        sa.Column("last_item_count", sa.Integer(), server_default="0", nullable=False),
        sa.Column("last_error", sa.String(length=120), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("source_key", name="uq_airdrop_discovery_source_status_key"),
    )
    op.create_index(
        "ix_airdrop_discovery_source_status_source_name",
        "airdrop_discovery_source_status",
        ["source_name"],
    )


def downgrade() -> None:
    op.drop_index(
        "ix_airdrop_discovery_source_status_source_name",
        table_name="airdrop_discovery_source_status",
    )
    op.drop_table("airdrop_discovery_source_status")
