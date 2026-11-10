import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String, Text, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class Airdrop(Base):
    __tablename__ = "airdrops"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    # NULL keeps pre-existing public catalogue entries readable and immutable.
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE", name="fk_airdrops_user_id_users"),
        nullable=True, index=True
    )
    name: Mapped[str] = mapped_column(String(160), nullable=False, index=True)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="")
    category: Mapped[str] = mapped_column(String(80), nullable=False, default="other")
    chain: Mapped[str] = mapped_column(String(80), nullable=False, default="")
    status: Mapped[str] = mapped_column(String(24), nullable=False, default="unknown")
    reward_details: Mapped[str] = mapped_column(Text, nullable=False, default="")
    eligibility: Mapped[str] = mapped_column(Text, nullable=False, default="")
    official_url: Mapped[str] = mapped_column(String(1000), nullable=False, default="")
    source_name: Mapped[str] = mapped_column(String(120), nullable=False, default="手动录入")
    source_url: Mapped[str] = mapped_column(String(1000), nullable=False, default="")
    source_type: Mapped[str] = mapped_column(String(24), nullable=False, default="manual")
    verification_status: Mapped[str] = mapped_column(
        String(24), nullable=False, default="unverified"
    )
    starts_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    ends_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    last_checked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(),
        onupdate=func.now(), nullable=False
    )
