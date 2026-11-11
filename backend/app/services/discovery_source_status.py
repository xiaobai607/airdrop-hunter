import hashlib
from datetime import datetime
from urllib.parse import urlsplit, urlunsplit

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AirdropDiscoverySourceStatus


def _safe_source_url(value: str) -> str:
    """Strip query/fragment credentials before persisting or exposing source URLs."""
    parsed = urlsplit(value.strip())
    host = parsed.hostname or ""
    try:
        port = parsed.port
    except ValueError:
        port = None
    netloc = f"{host}:{port}" if port is not None else host
    return urlunsplit((parsed.scheme, netloc, parsed.path, "", ""))[:1000]


async def record_discovery_source_status(
    db: AsyncSession,
    source_name: str,
    source_url: str,
    attempted_at: datetime,
    status: str,
    item_count: int | None = None,
    error: str | None = None,
) -> AirdropDiscoverySourceStatus:
    """Persist only safe source health metadata; never store response bodies or credentials."""
    if status not in {"success", "empty", "stale", "failed"}:
        raise ValueError("Unsupported discovery source status")
    normalized_name = source_name.strip()[:120]
    safe_url = _safe_source_url(source_url)
    source_key = hashlib.sha256(
        f"{normalized_name.casefold()}|{safe_url}".encode("utf-8")
    ).hexdigest()
    row = await db.scalar(
        select(AirdropDiscoverySourceStatus).where(
            AirdropDiscoverySourceStatus.source_key == source_key
        )
    )
    if row is None:
        row = AirdropDiscoverySourceStatus(
            source_key=source_key,
            source_name=normalized_name,
            source_url=safe_url,
            last_attempt_at=attempted_at,
            last_status=status,
            last_item_count=max(0, item_count or 0) if status == "success" else 0,
            last_error=(error or "")[:120] or None,
            last_success_at=attempted_at if status == "success" else None,
        )
        db.add(row)
        return row

    row.source_name = normalized_name
    row.source_url = safe_url
    row.last_attempt_at = attempted_at
    row.last_status = status
    row.last_error = (error or "")[:120] or None
    if status == "success":
        row.last_success_at = attempted_at
        row.last_item_count = max(0, item_count or 0)
    return row
