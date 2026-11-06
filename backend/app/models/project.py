import uuid
from datetime import datetime
from decimal import Decimal
from sqlalchemy import DateTime, ForeignKey, Numeric, String, Text, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base

class Project(Base):
    __tablename__ = "projects"
    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id", name="fk_projects_user_id_users", ondelete="CASCADE"), nullable=True, index=True)
    name: Mapped[str] = mapped_column(String(120), nullable=False, index=True)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="")
    status: Mapped[str] = mapped_column(String(24), nullable=False, default="upcoming")
    official_url: Mapped[str] = mapped_column(String(1000), nullable=False, default="")
    claim_url: Mapped[str] = mapped_column(String(1000), nullable=False, default="")
    twitter_url: Mapped[str] = mapped_column(String(1000), nullable=False, default="")
    deadline: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    expected_income: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False, default=0)
    gas_cost: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False, default=0)
    actual_income: Mapped[Decimal] = mapped_column(Numeric(20, 8), nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)
