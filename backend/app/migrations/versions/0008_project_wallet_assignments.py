"""Persist account-owned project to wallet assignments.

Revision ID: 0008_project_wallet_assignments
Revises: 0007_wallet_metadata
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0008_project_wallet_assignments"
down_revision = "0007_wallet_metadata"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "project_wallet_assignments",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("project_key", sa.String(length=160), nullable=False),
        sa.Column("wallet_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["wallet_id"], ["wallets.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("user_id", "project_key", "wallet_id", name="uq_project_wallet_assignment"),
    )
    op.create_index("ix_project_wallet_assignments_user_id", "project_wallet_assignments", ["user_id"])
    op.create_index("ix_project_wallet_assignments_project_key", "project_wallet_assignments", ["project_key"])
    op.create_index("ix_project_wallet_assignments_wallet_id", "project_wallet_assignments", ["wallet_id"])


def downgrade() -> None:
    op.drop_index("ix_project_wallet_assignments_wallet_id", table_name="project_wallet_assignments")
    op.drop_index("ix_project_wallet_assignments_project_key", table_name="project_wallet_assignments")
    op.drop_index("ix_project_wallet_assignments_user_id", table_name="project_wallet_assignments")
    op.drop_table("project_wallet_assignments")
