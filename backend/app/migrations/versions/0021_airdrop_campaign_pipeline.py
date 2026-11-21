"""Add campaign-aware discovery, source snapshots, market snapshots and score history.

Revision ID: 0021_airdrop_campaigns
Revises: 0020_airdrop_filters
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0021_airdrop_campaigns"
down_revision = "0020_airdrop_filters"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "airdrop_campaigns",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("project_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column("source_key", sa.String(240), nullable=False),
        sa.Column("source_name", sa.String(120), nullable=False),
        sa.Column("source_url", sa.String(1000), nullable=False),
        sa.Column("name", sa.String(240), nullable=False),
        sa.Column("chain", sa.String(80), nullable=True),
        sa.Column("description", sa.Text(), nullable=False, server_default=""),
        sa.Column("official_url", sa.String(1000), nullable=True),
        sa.Column("campaign_type", sa.String(40), nullable=False, server_default="unknown"),
        sa.Column("campaign_status", sa.String(40), nullable=False, server_default="unknown"),
        sa.Column("starts_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("ends_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("snapshot_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("claim_starts_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("claim_ends_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("retroactive_possible", sa.Boolean(), nullable=True),
        sa.Column("temperature", sa.Integer(), nullable=True),
        sa.Column("opportunity_score", sa.Integer(), nullable=True),
        sa.Column("is_kyc_free", sa.Boolean(), nullable=True),
        sa.Column("requires_kyc", sa.Boolean(), nullable=True),
        sa.Column("requires_phone", sa.Boolean(), nullable=True),
        sa.Column("difficulty", sa.String(32), nullable=True),
        sa.Column("cost_to_farm_usd", sa.Numeric(20, 8), nullable=True),
        sa.Column("token_network", sa.String(80), nullable=True),
        sa.Column("token_contract_address", sa.String(160), nullable=True),
        sa.Column("trust_status", sa.String(32), nullable=False, server_default="unverified"),
        sa.Column("source_conflict", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("corroborated_risk", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("risk_flags", sa.JSON(), nullable=False, server_default=sa.text("'[]'")),
        sa.Column("dex_pair_count", sa.Integer(), nullable=True),
        sa.Column("dex_liquidity_usd", sa.Numeric(20, 8), nullable=True),
        sa.Column("dex_volume_24h_usd", sa.Numeric(20, 8), nullable=True),
        sa.Column("gecko_trending", sa.Boolean(), nullable=True),
        sa.Column("score", sa.Integer(), nullable=True),
        sa.Column("score_breakdown", sa.JSON(), nullable=False, server_default=sa.text("'{}'")),
        sa.Column("scoring_version", sa.String(32), nullable=False, server_default="v1"),
        sa.Column("tier", sa.String(24), nullable=False, server_default="observation"),
        sa.Column("is_published", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("listing_reason", sa.Text(), nullable=False, server_default=""),
        sa.Column("review_reason", sa.Text(), nullable=False, server_default=""),
        sa.Column("low_heat_success_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("last_source_success_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("first_seen_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("last_verified_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.ForeignKeyConstraint(["project_id"], ["airdrop_hub_projects.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("source_key", name="uq_airdrop_campaign_source_key"),
    )
    op.create_index("ix_airdrop_campaigns_source_key", "airdrop_campaigns", ["source_key"], unique=True)
    op.create_index("ix_airdrop_campaigns_name", "airdrop_campaigns", ["name"])
    op.create_index("ix_airdrop_campaigns_chain", "airdrop_campaigns", ["chain"])
    op.create_index("ix_airdrop_campaigns_campaign_status", "airdrop_campaigns", ["campaign_status"])
    op.create_index("ix_airdrop_campaigns_tier", "airdrop_campaigns", ["tier"])
    op.create_index("ix_airdrop_campaigns_is_published", "airdrop_campaigns", ["is_published"])
    op.create_index("ix_airdrop_campaigns_tier_status", "airdrop_campaigns", ["tier", "campaign_status"])
    op.create_index("ix_airdrop_campaigns_project_id", "airdrop_campaigns", ["project_id"])

    op.create_table(
        "airdrop_campaign_source_snapshots",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("campaign_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("source_name", sa.String(120), nullable=False),
        sa.Column("source_url", sa.String(1000), nullable=False),
        sa.Column("snapshot_hash", sa.String(64), nullable=False),
        sa.Column("normalized_payload", sa.JSON(), nullable=False, server_default=sa.text("'{}'")),
        sa.Column("captured_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.ForeignKeyConstraint(["campaign_id"], ["airdrop_campaigns.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("campaign_id", "source_name", "snapshot_hash", name="uq_airdrop_campaign_source_snapshot"),
    )
    op.create_index("ix_airdrop_campaign_source_snapshots_campaign_time", "airdrop_campaign_source_snapshots", ["campaign_id", "captured_at"])

    op.create_table(
        "airdrop_campaign_market_snapshots",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("campaign_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("provider", sa.String(40), nullable=False),
        sa.Column("network", sa.String(80), nullable=False),
        sa.Column("contract_address", sa.String(160), nullable=False),
        sa.Column("pair_address", sa.String(160), nullable=True),
        sa.Column("liquidity_usd", sa.Numeric(20, 8), nullable=True),
        sa.Column("volume_24h_usd", sa.Numeric(20, 8), nullable=True),
        sa.Column("is_trending", sa.Boolean(), nullable=True),
        sa.Column("captured_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.ForeignKeyConstraint(["campaign_id"], ["airdrop_campaigns.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_airdrop_campaign_market_snapshots_campaign_time", "airdrop_campaign_market_snapshots", ["campaign_id", "captured_at"])

    op.create_table(
        "airdrop_pipeline_runs",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("source_name", sa.String(120), nullable=False),
        sa.Column("status", sa.String(20), nullable=False, server_default="running"),
        sa.Column("items_seen", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("items_created", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("items_updated", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("items_rejected", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("error_code", sa.String(120), nullable=True),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("finished_at", sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_airdrop_pipeline_runs_started_at", "airdrop_pipeline_runs", ["started_at"])

    op.create_table(
        "airdrop_campaign_score_history",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("campaign_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("pipeline_run_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column("scoring_version", sa.String(32), nullable=False),
        sa.Column("score", sa.Integer(), nullable=True),
        sa.Column("tier", sa.String(24), nullable=False),
        sa.Column("score_breakdown", sa.JSON(), nullable=False, server_default=sa.text("'{}'")),
        sa.Column("listing_reason", sa.Text(), nullable=False, server_default=""),
        sa.Column("evaluated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.ForeignKeyConstraint(["campaign_id"], ["airdrop_campaigns.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["pipeline_run_id"], ["airdrop_pipeline_runs.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_airdrop_campaign_score_history_campaign_time", "airdrop_campaign_score_history", ["campaign_id", "evaluated_at"])

    op.create_table(
        "airdrop_campaign_status_history",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("campaign_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("previous_status", sa.String(40), nullable=True),
        sa.Column("new_status", sa.String(40), nullable=False),
        sa.Column("reason", sa.Text(), nullable=False, server_default=""),
        sa.Column("source_name", sa.String(120), nullable=False),
        sa.Column("changed_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.ForeignKeyConstraint(["campaign_id"], ["airdrop_campaigns.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_airdrop_campaign_status_history_campaign_time", "airdrop_campaign_status_history", ["campaign_id", "changed_at"])


def downgrade() -> None:
    op.drop_index("ix_airdrop_campaign_status_history_campaign_time", table_name="airdrop_campaign_status_history")
    op.drop_table("airdrop_campaign_status_history")
    op.drop_index("ix_airdrop_campaign_score_history_campaign_time", table_name="airdrop_campaign_score_history")
    op.drop_table("airdrop_campaign_score_history")
    op.drop_index("ix_airdrop_pipeline_runs_started_at", table_name="airdrop_pipeline_runs")
    op.drop_table("airdrop_pipeline_runs")
    op.drop_index("ix_airdrop_campaign_market_snapshots_campaign_time", table_name="airdrop_campaign_market_snapshots")
    op.drop_table("airdrop_campaign_market_snapshots")
    op.drop_index("ix_airdrop_campaign_source_snapshots_campaign_time", table_name="airdrop_campaign_source_snapshots")
    op.drop_table("airdrop_campaign_source_snapshots")
    op.drop_index("ix_airdrop_campaigns_project_id", table_name="airdrop_campaigns")
    op.drop_index("ix_airdrop_campaigns_tier_status", table_name="airdrop_campaigns")
    op.drop_index("ix_airdrop_campaigns_is_published", table_name="airdrop_campaigns")
    op.drop_index("ix_airdrop_campaigns_tier", table_name="airdrop_campaigns")
    op.drop_index("ix_airdrop_campaigns_campaign_status", table_name="airdrop_campaigns")
    op.drop_index("ix_airdrop_campaigns_chain", table_name="airdrop_campaigns")
    op.drop_index("ix_airdrop_campaigns_name", table_name="airdrop_campaigns")
    op.drop_index("ix_airdrop_campaigns_source_key", table_name="airdrop_campaigns")
    op.drop_table("airdrop_campaigns")
