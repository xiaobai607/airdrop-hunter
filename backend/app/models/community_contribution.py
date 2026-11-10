import uuid
from datetime import datetime
from decimal import Decimal

from sqlalchemy import Boolean, DateTime, ForeignKey, Index, Numeric, String, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class CommunityProjectStats(Base):
    """Public aggregate totals. No wallet addresses or private transaction details are stored here."""

    __tablename__ = "community_project_stats"

    project_official_id: Mapped[str] = mapped_column(String(120), primary_key=True)
    total_gas_cost_usd: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False, default=0)
    total_actual_reward_usd: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False, default=0)
    total_estimated_reward_usd: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False, default=0)
    total_reward_usd: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False, default=0)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )


class CommunityContribution(Base):
    """One verified, anonymous, idempotent contribution to public project aggregates."""

    __tablename__ = "community_contributions"
    __table_args__ = (
        Index("uq_community_contributions_tx_fingerprint", "tx_fingerprint", unique=True),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=True, index=True
    )
    project_official_id: Mapped[str] = mapped_column(String(120), nullable=False, index=True)
    network: Mapped[str] = mapped_column(String(40), nullable=False, default="unknown")
    gas_cost_usd: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False)
    actual_reward_usd: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False)
    estimated_reward_usd: Mapped[Decimal | None] = mapped_column(Numeric(20, 8), nullable=True)
    actual_reward_confirmed: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    contributor_fingerprint: Mapped[str | None] = mapped_column(String(64), nullable=True, index=True)
    asset_breakdown: Mapped[list] = mapped_column(JSONB, nullable=False, default=list)
    tx_fingerprint: Mapped[str | None] = mapped_column(String(64), nullable=True)
    verification_tier: Mapped[str] = mapped_column(String(32), nullable=False, default="user_reported")
    verification_metadata: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
