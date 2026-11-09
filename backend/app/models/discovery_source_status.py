import uuid
from datetime import datetime

from sqlalchemy import DateTime, Integer, String, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class AirdropDiscoverySourceStatus(Base):
    """Persistent health and freshness status for one approved discovery feed/API."""

    __tablename__ = "airdrop_discovery_source_status"
    __table_args__ = (
        UniqueConstraint("source_key", name="uq_airdrop_discovery_source_status_key"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    source_key: Mapped[str] = mapped_column(String(64), nullable=False)
    source_name: Mapped[str] = mapped_column(String(120), nullable=False)
    source_url: Mapped[str] = mapped_column(String(1000), nullable=False)
    last_attempt_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    last_success_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    last_status: Mapped[str] = mapped_column(String(20), nullable=False, default="pending", server_default="pending")
    last_item_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    last_error: Mapped[str | None] = mapped_column(String(120), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )
