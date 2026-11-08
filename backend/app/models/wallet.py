import uuid
from datetime import datetime
from decimal import Decimal
from sqlalchemy import DateTime, ForeignKey, Index, Numeric, String, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base

class Wallet(Base):
    __tablename__ = "wallets"
    __table_args__ = (
        UniqueConstraint("user_id", "address", "chain", name="uq_wallets_user_address_chain"),
        Index("ix_wallets_user_synced", "user_id", "last_synced_at"),
    )
    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    # public_key is retained for compatibility with older app builds.
    public_key: Mapped[str] = mapped_column(String(120), nullable=False, default="")
    address: Mapped[str] = mapped_column(String(120), nullable=False, default="")
    chain: Mapped[str] = mapped_column(String(24), nullable=False, default="unknown")
    network_icon: Mapped[str] = mapped_column(String(24), nullable=False, default="?")
    wallet_name: Mapped[str] = mapped_column(String(80), nullable=False, default="Wallet")
    daily_gas_usd: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False, default=0)
    recent_airdrop_income_usd: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False, default=0)
    last_synced_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
