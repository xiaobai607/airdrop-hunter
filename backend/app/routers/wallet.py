import hashlib
import re
from datetime import datetime, timezone
from decimal import Decimal
from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.routers.auth import get_current_user
from app.database import get_db
from app.models import User, Wallet

router = APIRouter(prefix="/api/wallet", tags=["wallet"])

class WalletResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    public_key: str
    chain: str
    daily_gas_usd: float
    recent_airdrop_income_usd: float
    last_synced_at: datetime | None

def validate_public_key(value: str) -> str:
    value = value.strip()
    evm = bool(re.fullmatch(r"0x[a-fA-F0-9]{40}", value))
    base58 = bool(re.fullmatch(r"[1-9A-HJ-NP-Za-km-z]{20,120}", value))
    if not (evm or base58):
        raise HTTPException(422, "请输入有效的公开钱包地址")
    return value

@router.get("/check")
async def check_wallet(address: str = Query(min_length=20, max_length=120)):
    value = validate_public_key(address)
    digest = hashlib.sha256(value.encode("utf-8")).digest()
    eligible = digest[0] % 2 == 0
    reward = round(10 + (int.from_bytes(digest[1:3], "big") % 9000) / 100, 2) if eligible else 0.0
    return {"address": value, "eligible": eligible, "estimated_reward_usd": reward, "status": "simulated",
            "notice": "模拟结果，仅用于界面联调；不代表真实链上资格或官方奖励。"}

@router.get("", response_model=list[WalletResponse])
async def list_wallets(db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    rows = await db.scalars(select(Wallet).where(Wallet.user_id == user.id).order_by(Wallet.created_at.desc()))
    return [WalletResponse(id=str(w.id), public_key=w.public_key, chain=w.chain, daily_gas_usd=float(w.daily_gas_usd), recent_airdrop_income_usd=float(w.recent_airdrop_income_usd), last_synced_at=w.last_synced_at) for w in rows]

@router.get("/sync")
async def sync_wallet(public_key: str = Query(min_length=20, max_length=120), db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    address = validate_public_key(public_key)
    digest = hashlib.sha256(address.encode("utf-8")).digest()
    # Deterministic demo data; deliberately not represented as verified on-chain accounting.
    gas = Decimal(str(round(0.15 + (int.from_bytes(digest[:2], "big") % 480) / 100, 2)))
    reward = Decimal(str(round((int.from_bytes(digest[2:4], "big") % 5000) / 100, 2)))
    chain = "ethereum" if address.startswith("0x") else "base58/unknown"
    wallet = await db.scalar(select(Wallet).where(Wallet.user_id == user.id, Wallet.public_key == address))
    if wallet is None:
        wallet = Wallet(user_id=user.id, public_key=address, chain=chain)
        db.add(wallet)
    wallet.chain = chain
    wallet.daily_gas_usd = gas
    wallet.recent_airdrop_income_usd = reward
    wallet.last_synced_at = datetime.now(timezone.utc)
    await db.commit()
    await db.refresh(wallet)
    return {"wallet_id": str(wallet.id), "public_key": address, "chain": chain,
        "daily_gas_usd": float(gas), "recent_airdrop_income_usd": float(reward),
        "status": "simulated", "synced_at": wallet.last_synced_at,
        "notice": "模拟数据，并未连接区块链浏览器或核验真实交易。"}
