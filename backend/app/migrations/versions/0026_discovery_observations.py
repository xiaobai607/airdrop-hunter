"""Append immutable source observations for discovery audit history.
Revision ID: 0026_discovery_observations
Revises: 0025_identity_classification
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0026_discovery_observations"
down_revision = "0025_identity_classification"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "airdrop_discovery_observations",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("candidate_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("source_name", sa.String(length=120), nullable=False),
        sa.Column("source_record_key", sa.String(length=240), nullable=False),
        sa.Column("source_url", sa.String(length=1000), nullable=False),
        sa.Column("item_url", sa.String(length=1000), nullable=False),
        sa.Column("observed_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("payload_sha256", sa.String(length=64), nullable=False),
        sa.Column("raw_payload", sa.JSON(), nullable=False),
        sa.Column("normalized_payload", sa.JSON(), nullable=False),
        sa.Column("filter_decision", sa.JSON(), nullable=False),
        sa.Column("identity_decision", sa.JSON(), nullable=False),
        sa.Column("normalizer_version", sa.String(length=80), nullable=False),
        sa.Column("filter_version", sa.String(length=80), nullable=False),
        sa.ForeignKeyConstraint(["candidate_id"], ["airdrop_discovery_candidates.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_airdrop_discovery_observations_candidate_observed", "airdrop_discovery_observations", ["candidate_id", "observed_at"])
    op.create_index("ix_airdrop_discovery_observations_source_observed", "airdrop_discovery_observations", ["source_name", "observed_at"])


def downgrade() -> None:
    op.drop_index("ix_airdrop_discovery_observations_source_observed", table_name="airdrop_discovery_observations")
    op.drop_index("ix_airdrop_discovery_observations_candidate_observed", table_name="airdrop_discovery_observations")
    op.drop_table("airdrop_discovery_observations")
