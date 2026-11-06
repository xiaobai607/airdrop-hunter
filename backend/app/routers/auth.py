import time
from typing import Annotated
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.models import User
from app.security import create_access_token, decode_access_token, hash_password, verify_password

router = APIRouter(prefix="/api/auth", tags=["authentication"])
bearer_scheme = HTTPBearer(auto_error=False)
MOCK_CODE = "123456"
CODE_TTL_SECONDS = 600
CODE_COOLDOWN_SECONDS = 60
_verification_codes: dict[str, tuple[str, float, float]] = {}

class SendCodeRequest(BaseModel):
    email: str = Field(min_length=3, max_length=320)

class SendCodeResponse(BaseModel):
    message: str
    mock_code: str
    retry_after_seconds: int
    expires_in_seconds: int

class AuthRequest(BaseModel):
    email: str = Field(min_length=3, max_length=320)
    password: str = Field(min_length=8, max_length=128)
    verification_code: str | None = Field(default=None, min_length=6, max_length=6)

class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: str
    email: str
    display_name: str | None

def auth_response(user: User) -> AuthResponse:
    return AuthResponse(access_token=create_access_token(user.id), user_id=str(user.id), email=user.email, display_name=user.display_name)

@router.post("/send-code", response_model=SendCodeResponse)
async def send_code(payload: SendCodeRequest) -> SendCodeResponse:
    email = payload.email.strip().lower()
    if "@" not in email or email.startswith("@") or email.endswith("@"):
        raise HTTPException(422, "请输入有效的邮箱地址")
    now = time.monotonic()
    previous = _verification_codes.get(email)
    if previous and now - previous[2] < CODE_COOLDOWN_SECONDS:
        remaining = CODE_COOLDOWN_SECONDS - int(now - previous[2])
        raise HTTPException(429, f"请等待 {remaining} 秒后再获取验证码")
    _verification_codes[email] = (MOCK_CODE, now + CODE_TTL_SECONDS, now)
    print(f"[MOCK EMAIL] verification code for {email}: {MOCK_CODE}")
    return SendCodeResponse(message="Mock verification code generated", mock_code=MOCK_CODE,
                            retry_after_seconds=CODE_COOLDOWN_SECONDS, expires_in_seconds=CODE_TTL_SECONDS)

async def get_current_user(credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)], db: Annotated[AsyncSession, Depends(get_db)]) -> User:
    unauthorized = HTTPException(401, "登录状态无效或已过期，请重新登录", headers={"WWW-Authenticate": "Bearer"})
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise unauthorized
    try:
        user_id: UUID = decode_access_token(credentials.credentials)
    except Exception:
        raise unauthorized
    user = await db.scalar(select(User).where(User.id == user_id))
    if user is None:
        raise unauthorized
    return user

@router.post("/register", response_model=AuthResponse, status_code=status.HTTP_201_CREATED)
async def register(payload: AuthRequest, db: Annotated[AsyncSession, Depends(get_db)]) -> AuthResponse:
    email = payload.email.strip().lower()
    if "@" not in email or email.startswith("@") or email.endswith("@"):
        raise HTTPException(422, "请输入有效的邮箱地址")
    issued = _verification_codes.get(email)
    if not payload.verification_code or issued is None or issued[0] != payload.verification_code or time.monotonic() > issued[1]:
        raise HTTPException(400, "验证码错误或已过期，请重新获取")
    user = User(email=email, password_hash=hash_password(payload.password))
    db.add(user)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(409, "该邮箱已注册，请直接登录")
    await db.refresh(user)
    return auth_response(user)

@router.post("/login", response_model=AuthResponse)
async def login(payload: AuthRequest, db: Annotated[AsyncSession, Depends(get_db)]) -> AuthResponse:
    user = await db.scalar(select(User).where(User.email == payload.email.strip().lower()))
    if user is None or not verify_password(payload.password, user.password_hash):
        raise HTTPException(401, "邮箱或密码错误", headers={"WWW-Authenticate": "Bearer"})
    return auth_response(user)
