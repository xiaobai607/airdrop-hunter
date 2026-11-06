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

class AuthRequest(BaseModel):
    email: str = Field(min_length=3, max_length=320)
    password: str = Field(min_length=8, max_length=128)
    display_name: str | None = Field(default=None, max_length=80)

class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: str
    email: str
    display_name: str | None

def auth_response(user: User) -> AuthResponse:
    return AuthResponse(access_token=create_access_token(user.id), user_id=str(user.id), email=user.email, display_name=user.display_name)

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
    user = User(email=email, display_name=(payload.display_name or "").strip() or None, password_hash=hash_password(payload.password))
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
