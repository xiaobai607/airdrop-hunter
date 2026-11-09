import uuid
from datetime import datetime
from sqlalchemy import CheckConstraint, DateTime, ForeignKey, Index, Integer, String, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base


class CommunityProjectRating(Base):
    """Private account-level rating row; only aggregate score/count are public."""
    __tablename__ = "community_project_ratings"
    __table_args__ = (
        UniqueConstraint("user_id", "project_official_id", name="uq_community_project_rating_user_project"),
        CheckConstraint("score >= 1 AND score <= 5", name="ck_community_project_rating_score_range"),
        Index("ix_community_project_ratings_project_official_id", "project_official_id"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    project_official_id: Mapped[str] = mapped_column(String(120), nullable=False)
    score: Mapped[int] = mapped_column(Integer, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)
