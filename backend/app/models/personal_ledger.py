import uuid
from datetime import datetime
from decimal import Decimal
from sqlalchemy import DateTime, ForeignKey, Index, Numeric, String, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base

class PersonalLedgerEntry(Base):
    """Private, account-owned ledger row. Never used as a public dashboard response."""
    __tablename__ = "personal_ledger_entries"
    __table_args__ = (
        UniqueConstraint("user_id", "client_record_id", name="uq_personal_ledger_user_client_record"),
        Index("ix_personal_ledger_user_occurred", "user_id", "occurred_at"),
        Index("ix_personal_ledger_user_project", "user_id", "project_id"),
    )
    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    client_record_id: Mapped[str] = mapped_column(String(64), nullable=False)
    project_id: Mapped[str | None] = mapped_column(String(120), nullable=True)
    project_name: Mapped[str] = mapped_column(String(180), nullable=False, default="")
    chain: Mapped[str] = mapped_column(String(40), nullable=False, default="unknown")
    wallet_address: Mapped[str] = mapped_column(String(160), nullable=False, default="")
    tx_hash: Mapped[str] = mapped_column(String(180), nullable=False, default="")
    interaction_method: Mapped[str] = mapped_column(String(120), nullable=False, default="")
    expected_gas_cost_usd: Mapped[Decimal | None] = mapped_column(Numeric(20, 8), nullable=True, default=None)
    expected_reward_usd: Mapped[Decimal | None] = mapped_column(Numeric(20, 8), nullable=True, default=None)
    actual_gas_cost_usd: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False, default=0)
    actual_reward_usd: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False, default=0)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    extra: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)
