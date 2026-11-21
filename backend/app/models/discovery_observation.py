import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import DateTime, ForeignKey, Index, JSON, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class AirdropDiscoveryObservation(Base):
    """Append-only, sanitized evidence captured for each provider refresh."""

    __tablename__ = "airdrop_discovery_observations"
    __table_args__ = (
        Index("ix_airdrop_discovery_observations_candidate_observed", "candidate_id", "observed_at"),
        Index("ix_airdrop_discovery_observations_source_observed", "source_name", "observed_at"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    candidate_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("airdrop_discovery_candidates.id", ondelete="CASCADE"), nullable=False
    )
    source_name: Mapped[str] = mapped_column(String(120), nullable=False)
    source_record_key: Mapped[str] = mapped_column(String(240), nullable=False)
    source_url: Mapped[str] = mapped_column(String(1000), nullable=False)
    item_url: Mapped[str] = mapped_column(String(1000), nullable=False)
    observed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    payload_sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    raw_payload: Mapped[dict[str, Any]] = mapped_column(JSON, nullable=False, default=dict)
    normalized_payload: Mapped[dict[str, Any]] = mapped_column(JSON, nullable=False, default=dict)
    filter_decision: Mapped[dict[str, Any]] = mapped_column(JSON, nullable=False, default=dict)
    identity_decision: Mapped[dict[str, Any]] = mapped_column(JSON, nullable=False, default=dict)
    normalizer_version: Mapped[str] = mapped_column(String(80), nullable=False)
    filter_version: Mapped[str] = mapped_column(String(80), nullable=False)
