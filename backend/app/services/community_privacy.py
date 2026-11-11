import hashlib
import hmac
import os
from uuid import UUID


def anonymous_contributor_fingerprint(contributor_id: UUID | str | None) -> str | None:
    """Return a keyed, non-reversible installation fingerprint; never persist the raw client ID."""
    if contributor_id is None:
        return None
    secret = (
        os.getenv("COMMUNITY_CONTRIBUTOR_HMAC_SECRET", "").strip()
        or os.getenv("JWT_SECRET_KEY", "").strip()
    )
    if len(secret) < 32:
        return None
    value = str(contributor_id).strip().lower()
    if not value:
        return None
    message = f"airdrop-hunter:community-contributor:v1:{value}".encode("utf-8")
    return hmac.new(secret.encode("utf-8"), message, hashlib.sha256).hexdigest()
