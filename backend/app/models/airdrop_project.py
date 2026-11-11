import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import DateTime, JSON, String, Text, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class AirdropProject(Base):
    """Public discovery catalog imported from a third-party open dataset."""

    __tablename__ = "airdrop_hub_projects"
    __table_args__ = (UniqueConstraint("source_key", name="uq_airdrop_hub_source_key"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    source_key: Mapped[str] = mapped_column(String(240), nullable=False, unique=True, index=True)
    name: Mapped[str] = mapped_column(String(180), nullable=False, index=True)
    chain: Mapped[str] = mapped_column(String(80), nullable=False, default="Unknown", index=True)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="")
    status: Mapped[str] = mapped_column(String(40), nullable=False, default="未知")
    link: Mapped[str] = mapped_column(String(1000), nullable=False)
    source_name: Mapped[str] = mapped_column(String(120), nullable=False, default="web3-discover")
    trust_level: Mapped[str] = mapped_column(String(24), nullable=False, default="TRUST_LEVEL_0", server_default="TRUST_LEVEL_0")
    publication_status: Mapped[str] = mapped_column(String(24), nullable=False, default="OBSERVATION", server_default="OBSERVATION", index=True)
    filter_status: Mapped[str] = mapped_column(String(32), nullable=False, default="not_evaluated", server_default="not_evaluated", index=True)
    filter_reasons: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list, server_default="[]")
    filter_metadata: Mapped[dict[str, Any]] = mapped_column(JSON, nullable=False, default=dict, server_default="{}")
    project_identity_key: Mapped[str | None] = mapped_column(String(64), nullable=True, index=True)
    campaign_identity_key: Mapped[str | None] = mapped_column(String(64), nullable=True, index=True)
    identity_status: Mapped[str] = mapped_column(String(48), nullable=False, default="insufficient_identity_evidence", server_default="insufficient_identity_evidence", index=True)
    identity_evidence: Mapped[dict[str, Any]] = mapped_column(JSON, nullable=False, default=dict, server_default="{}")
    matched_source_key: Mapped[str | None] = mapped_column(String(240), nullable=True)
    imported_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)
