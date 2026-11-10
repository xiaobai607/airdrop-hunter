import os
from datetime import datetime, timedelta, timezone
from uuid import UUID
import jwt
from pwdlib import PasswordHash

_password_hash = PasswordHash.recommended()
ALGORITHM = "HS256"

def _secret_key() -> str:
    secret = os.getenv("JWT_SECRET_KEY", "").strip()
    if len(secret) < 32:
        raise RuntimeError("JWT_SECRET_KEY must be configured with at least 32 characters")
    return secret

def hash_password(password: str) -> str:
    return _password_hash.hash(password)

def verify_password(password: str, password_hash: str | None) -> bool:
    if not password_hash:
        return False
    try:
        return _password_hash.verify(password, password_hash)
    except Exception:
        return False

def create_access_token(user_id: UUID) -> str:
    now = datetime.now(timezone.utc)
    minutes = int(os.getenv("JWT_EXPIRE_MINUTES", "10080"))
    payload = {"sub": str(user_id), "iat": now, "exp": now + timedelta(minutes=minutes)}
    return jwt.encode(payload, _secret_key(), algorithm=ALGORITHM)

def decode_access_token(token: str) -> UUID:
    payload = jwt.decode(token, _secret_key(), algorithms=[ALGORITHM],
                         options={"require": ["sub", "exp", "iat"]})
    return UUID(payload["sub"])
