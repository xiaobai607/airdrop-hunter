import uuid
from sqlalchemy import BigInteger, Boolean, ForeignKey, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class CommunityFavorite(Base):
    __tablename__ = "community_favorites"

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True
    )
    project_official_id: Mapped[str] = mapped_column(String(120), primary_key=True)
    project_name: Mapped[str] = mapped_column(String(180), nullable=False)
    is_favorite: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    updated_at: Mapped[int] = mapped_column(BigInteger, nullable=False)
