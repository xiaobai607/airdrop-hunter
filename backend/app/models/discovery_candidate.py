import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import DateTime, JSON, String, Text, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class AirdropDiscoveryCandidate(Base):
    """Unreviewed project leads collected from explicitly configured public feeds."""

    __tablename__ = "airdrop_discovery_candidates"
    __table_args__ = (UniqueConstraint("source_key", name="uq_airdrop_discovery_source_key"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    source_key: Mapped[str] = mapped_column(String(64), nullable=False, unique=True, index=True)
    source_name: Mapped[str] = mapped_column(String(120), nullable=False)
    source_url: Mapped[str] = mapped_column(String(1000), nullable=False)
    title: Mapped[str] = mapped_column(String(300), nullable=False, index=True)
    item_url: Mapped[str] = mapped_column(String(1000), nullable=False)
    summary: Mapped[str] = mapped_column(Text, nullable=False, default="")
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    first_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)
    review_status: Mapped[str] = mapped_column(String(20), nullable=False, default="pending", server_default="pending", index=True)
    reviewed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    reviewed_catalog_id: Mapped[str | None] = mapped_column(String(120), nullable=True)
    filter_status: Mapped[str] = mapped_column(String(32), nullable=False, default="not_evaluated", server_default="not_evaluated", index=True)
    filter_reasons: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list, server_default="[]")
    filter_metadata: Mapped[dict[str, Any]] = mapped_column(JSON, nullable=False, default=dict, server_default="{}")
    project_identity_key: Mapped[str | None] = mapped_column(String(64), nullable=True, index=True)
    campaign_identity_key: Mapped[str | None] = mapped_column(String(64), nullable=True, index=True)
    identity_status: Mapped[str] = mapped_column(String(48), nullable=False, default="insufficient_identity_evidence", server_default="insufficient_identity_evidence", index=True)
    identity_evidence: Mapped[dict[str, Any]] = mapped_column(JSON, nullable=False, default=dict, server_default="{}")
    matched_source_key: Mapped[str | None] = mapped_column(String(240), nullable=True)
