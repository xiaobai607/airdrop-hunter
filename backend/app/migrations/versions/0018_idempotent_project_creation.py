"""Add per-user idempotency key to project creation.

Revision ID: 0018_idempotent_project_creation
Revises: 0017_verified_contrib
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0018_idempotent_project_creation"
down_revision = "0017_verified_contrib"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "projects",
        sa.Column("client_project_id", postgresql.UUID(as_uuid=True), nullable=True),
    )
    op.create_index(
        "uq_projects_user_client_project_id",
        "projects",
        ["user_id", "client_project_id"],
        unique=True,
    )


def downgrade() -> None:
    op.drop_index("uq_projects_user_client_project_id", table_name="projects")
    op.drop_column("projects", "client_project_id")
