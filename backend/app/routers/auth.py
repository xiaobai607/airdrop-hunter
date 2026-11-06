import secrets
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
    username: str = Field(min_length=3, max_length=24, pattern=r"^[A-Za-z0-9_]+$")
    password: str = Field(min_length=8, max_length=128)


class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: str
    username: str
    uid: str


def auth_response(user: User) -> AuthResponse:
    return AuthResponse(
        access_token=create_access_token(user.id),
        user_id=str(user.id),
        username=user.username,
        uid=user.uid,
    )


async def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> User:
    unauthorized = HTTPException(
        401,
        "登录状态无效或已过期，请重新登录",
        headers={"WWW-Authenticate": "Bearer"},
    )
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
async def register(
    payload: AuthRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> AuthResponse:
    username = payload.username.strip().lower()
    existing = await db.scalar(select(User).where(User.username == username))
    if existing is not None:
        raise HTTPException(400, "该账号已被占用")

    # Six-digit IDs are allocated randomly and protected by a database unique index.
    for _ in range(10):
        uid = f"{secrets.randbelow(900000) + 100000:06d}"
        user = User(
            username=username,
            uid=uid,
            email=None,
            display_name=username,
            password_hash=hash_password(payload.password),
        )
        db.add(user)
        try:
            await db.commit()
            await db.refresh(user)
            return auth_response(user)
        except IntegrityError:
            await db.rollback()
            duplicate = await db.scalar(select(User).where(User.username == username))
            if duplicate is not None:
                raise HTTPException(400, "该账号已被占用")
    raise HTTPException(503, "暂时无法分配用户编号，请稍后重试")


@router.post("/login", response_model=AuthResponse)
async def login(
    payload: AuthRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> AuthResponse:
    username = payload.username.strip().lower()
    user = await db.scalar(select(User).where(User.username == username))
    if user is None or not verify_password(payload.password, user.password_hash):
        raise HTTPException(401, "账号或密码错误", headers={"WWW-Authenticate": "Bearer"})
    return auth_response(user)


@router.get("/me")
async def get_me(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
    db: Annotated[AsyncSession, Depends(get_db)],
):
    user = await get_current_user(credentials, db)
    return {"user_id": str(user.id), "username": user.username, "uid": user.uid}
