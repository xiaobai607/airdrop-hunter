from datetime import datetime

from sqlalchemy import Boolean, DateTime, Integer, JSON, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class CommunityProjectCatalogEntry(Base):
    """Curated public directory metadata; contribution aggregates remain in separate tables."""

    __tablename__ = "community_project_catalog"

    project_official_id: Mapped[str] = mapped_column(String(120), primary_key=True)
    name: Mapped[str] = mapped_column(String(180), nullable=False, index=True)
    chain: Mapped[str] = mapped_column(String(120), nullable=False, default="Unknown", index=True)
    logo: Mapped[str] = mapped_column(String(12), nullable=False, default="")
    description: Mapped[str] = mapped_column(Text, nullable=False, default="")
    tags: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    supported_chains: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    categories: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    participation_requirements: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    funding_requirement: Mapped[str] = mapped_column(String(32), nullable=False, default="unknown")
    campaign_stage: Mapped[str] = mapped_column(String(32), nullable=False, default="unknown")
    reward_types: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    risk_flags: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    effort_level: Mapped[str] = mapped_column(String(32), nullable=False, default="unknown")
    criteria_source_url: Mapped[str] = mapped_column(String(1000), nullable=False, default="")
    metadata_verified_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    metadata_note: Mapped[str] = mapped_column(Text, nullable=False, default="")
    website_url: Mapped[str] = mapped_column(String(1000), nullable=False, default="")
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False, index=True)
    sort_order: Mapped[int] = mapped_column(Integer, nullable=False, default=100)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)
