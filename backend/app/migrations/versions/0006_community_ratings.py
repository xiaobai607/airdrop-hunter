"""Add verified community project star ratings.

Revision ID: 0006_community_ratings
Revises: 0005_anonymize_contributions
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0006_community_ratings"
down_revision = "0005_anonymize_contributions"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "community_project_ratings",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("project_official_id", sa.String(length=120), nullable=False),
        sa.Column("score", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.CheckConstraint("score >= 1 AND score <= 5", name="ck_community_project_rating_score_range"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("user_id", "project_official_id", name="uq_community_project_rating_user_project"),
    )
    op.create_index(
        "ix_community_project_ratings_project_official_id",
        "community_project_ratings",
        ["project_official_id"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("ix_community_project_ratings_project_official_id", table_name="community_project_ratings")
    op.drop_table("community_project_ratings")
