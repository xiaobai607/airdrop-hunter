import uuid
from datetime import datetime
from decimal import Decimal

from sqlalchemy import (
    DateTime, ForeignKey, Index, Integer, JSON, Numeric, String, Text,
    UniqueConstraint, func,
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class AirdropCampaign(Base):
    """One distinct season, task campaign, snapshot or claim opportunity."""

    __tablename__ = "airdrop_campaigns"
    __table_args__ = (
        UniqueConstraint("source_key", name="uq_airdrop_campaign_source_key"),
        Index("ix_airdrop_campaigns_tier_status", "tier", "campaign_status"),
        Index("ix_airdrop_campaigns_project_id", "project_id"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    project_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("airdrop_hub_projects.id", ondelete="SET NULL"),
        nullable=True,
    )
    source_key: Mapped[str] = mapped_column(String(240), nullable=False, unique=True, index=True)
    source_name: Mapped[str] = mapped_column(String(120), nullable=False)
    source_url: Mapped[str] = mapped_column(String(1000), nullable=False)
    name: Mapped[str] = mapped_column(String(240), nullable=False, index=True)
    chain: Mapped[str | None] = mapped_column(String(80), nullable=True, index=True)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="")
    official_url: Mapped[str | None] = mapped_column(String(1000), nullable=True)
    campaign_type: Mapped[str] = mapped_column(String(40), nullable=False, default="unknown")
    campaign_status: Mapped[str] = mapped_column(String(40), nullable=False, default="unknown", index=True)
    starts_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    ends_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    snapshot_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    claim_starts_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    claim_ends_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    retroactive_possible: Mapped[bool | None] = mapped_column(nullable=True)
    temperature: Mapped[int | None] = mapped_column(Integer, nullable=True)
    opportunity_score: Mapped[int | None] = mapped_column(Integer, nullable=True)
    is_kyc_free: Mapped[bool | None] = mapped_column(nullable=True)
    requires_kyc: Mapped[bool | None] = mapped_column(nullable=True)
    requires_phone: Mapped[bool | None] = mapped_column(nullable=True)
    difficulty: Mapped[str | None] = mapped_column(String(32), nullable=True)
    cost_to_farm_usd: Mapped[Decimal | None] = mapped_column(Numeric(20, 8), nullable=True)
    token_network: Mapped[str | None] = mapped_column(String(80), nullable=True)
    token_contract_address: Mapped[str | None] = mapped_column(String(160), nullable=True)
    trust_status: Mapped[str] = mapped_column(String(32), nullable=False, default="unverified")
    source_conflict: Mapped[bool] = mapped_column(nullable=False, default=False, server_default="false")
    corroborated_risk: Mapped[bool] = mapped_column(nullable=False, default=False, server_default="false")
    risk_flags: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    dex_pair_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    dex_liquidity_usd: Mapped[Decimal | None] = mapped_column(Numeric(20, 8), nullable=True)
    dex_volume_24h_usd: Mapped[Decimal | None] = mapped_column(Numeric(20, 8), nullable=True)
    gecko_trending: Mapped[bool | None] = mapped_column(nullable=True)
    score: Mapped[int | None] = mapped_column(Integer, nullable=True)
    score_breakdown: Mapped[dict] = mapped_column(JSON, nullable=False, default=dict)
    scoring_version: Mapped[str] = mapped_column(String(32), nullable=False, default="v1")
    tier: Mapped[str] = mapped_column(String(24), nullable=False, default="observation", index=True)
    is_published: Mapped[bool] = mapped_column(nullable=False, default=False, server_default="false", index=True)
    listing_reason: Mapped[str] = mapped_column(Text, nullable=False, default="")
    review_reason: Mapped[str] = mapped_column(Text, nullable=False, default="")
    low_heat_success_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    last_source_success_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    first_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    last_verified_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)


class AirdropCampaignSourceSnapshot(Base):
    __tablename__ = "airdrop_campaign_source_snapshots"
    __table_args__ = (
        UniqueConstraint("campaign_id", "source_name", "snapshot_hash", name="uq_airdrop_campaign_source_snapshot"),
        Index("ix_airdrop_campaign_source_snapshots_campaign_time", "campaign_id", "captured_at"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    campaign_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("airdrop_campaigns.id", ondelete="CASCADE"), nullable=False)
    source_name: Mapped[str] = mapped_column(String(120), nullable=False)
    source_url: Mapped[str] = mapped_column(String(1000), nullable=False)
    snapshot_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    normalized_payload: Mapped[dict] = mapped_column(JSON, nullable=False, default=dict)
    captured_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class AirdropCampaignMarketSnapshot(Base):
    __tablename__ = "airdrop_campaign_market_snapshots"
    __table_args__ = (Index("ix_airdrop_campaign_market_snapshots_campaign_time", "campaign_id", "captured_at"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    campaign_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("airdrop_campaigns.id", ondelete="CASCADE"), nullable=False)
    provider: Mapped[str] = mapped_column(String(40), nullable=False)
    network: Mapped[str] = mapped_column(String(80), nullable=False)
    contract_address: Mapped[str] = mapped_column(String(160), nullable=False)
    pair_address: Mapped[str | None] = mapped_column(String(160), nullable=True)
    liquidity_usd: Mapped[Decimal | None] = mapped_column(Numeric(20, 8), nullable=True)
    volume_24h_usd: Mapped[Decimal | None] = mapped_column(Numeric(20, 8), nullable=True)
    is_trending: Mapped[bool | None] = mapped_column(nullable=True)
    captured_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class AirdropPipelineRun(Base):
    __tablename__ = "airdrop_pipeline_runs"
    __table_args__ = (Index("ix_airdrop_pipeline_runs_started_at", "started_at"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    source_name: Mapped[str] = mapped_column(String(120), nullable=False)
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="running")
    items_seen: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    items_created: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    items_updated: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    items_rejected: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    error_code: Mapped[str | None] = mapped_column(String(120), nullable=True)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class AirdropCampaignScoreHistory(Base):
    __tablename__ = "airdrop_campaign_score_history"
    __table_args__ = (Index("ix_airdrop_campaign_score_history_campaign_time", "campaign_id", "evaluated_at"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    campaign_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("airdrop_campaigns.id", ondelete="CASCADE"), nullable=False)
    pipeline_run_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("airdrop_pipeline_runs.id", ondelete="SET NULL"), nullable=True)
    scoring_version: Mapped[str] = mapped_column(String(32), nullable=False)
    score: Mapped[int | None] = mapped_column(Integer, nullable=True)
    tier: Mapped[str] = mapped_column(String(24), nullable=False)
    score_breakdown: Mapped[dict] = mapped_column(JSON, nullable=False, default=dict)
    listing_reason: Mapped[str] = mapped_column(Text, nullable=False, default="")
    evaluated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class AirdropCampaignStatusHistory(Base):
    __tablename__ = "airdrop_campaign_status_history"
    __table_args__ = (Index("ix_airdrop_campaign_status_history_campaign_time", "campaign_id", "changed_at"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    campaign_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("airdrop_campaigns.id", ondelete="CASCADE"), nullable=False)
    previous_status: Mapped[str | None] = mapped_column(String(40), nullable=True)
    new_status: Mapped[str] = mapped_column(String(40), nullable=False)
    reason: Mapped[str] = mapped_column(Text, nullable=False, default="")
    source_name: Mapped[str] = mapped_column(String(120), nullable=False)
    changed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
