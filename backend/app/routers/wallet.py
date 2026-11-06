import re
import httpx
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

def identify_chain(address: str) -> str | None:
    value = address.strip()
    if re.fullmatch(r"0x[a-fA-F0-9]{40}", value):
        return "Ethereum"
    if re.fullmatch(r"T[1-9A-HJ-NP-Za-km-z]{33}", value):
        return "Tron"
    if re.fullmatch(r"bc1[ac-hj-np-z02-9]{11,71}", value, re.IGNORECASE) or re.fullmatch(r"[13][1-9A-HJ-NP-Za-km-z]{25,34}", value):
        return "Bitcoin"
    if re.fullmatch(r"[1-9A-HJ-NP-Za-km-z]{32,44}", value):
        return "Solana"
    return None

async def verify_address_on_network(address: str, chain: str) -> tuple[bool, str]:
    timeout = httpx.Timeout(15.0, connect=8.0)
    async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": "AirdropHunter/1.0"}) as client:
        if chain == "Ethereum":
            response = await client.post("https://cloudflare-eth.com", json={
                "jsonrpc": "2.0", "id": 1, "method": "eth_getBalance", "params": [address, "latest"]
            })
            response.raise_for_status()
            payload = response.json()
            if payload.get("error") or not isinstance(payload.get("result"), str):
                return False, "地址格式错误或不存在"
            return True, "地址格式有效，已成功连接 Ethereum 公链"
        if chain == "Solana":
            response = await client.post("https://api.mainnet-beta.solana.com", json={
                "jsonrpc": "2.0", "id": 1, "method": "getAccountInfo",
                "params": [address, {"encoding": "base64", "commitment": "confirmed"}]
            })
            response.raise_for_status()
            payload = response.json()
            account = (payload.get("result") or {}).get("value")
            if payload.get("error") or account is None:
                return False, "地址格式错误或不存在"
            return True, "已在 Solana 主网查询到账户"
        if chain == "Tron":
            response = await client.get("https://apilist.tronscanapi.com/api/account", params={"address": address})
            response.raise_for_status()
            payload = response.json()
            if not payload or not payload.get("address"):
                return False, "地址格式错误或不存在"
            return True, "已在 Tron 网络查询到账户"
        if chain == "Bitcoin":
            response = await client.get("https://blockstream.info/api/address/" + address)
            if response.status_code in (400, 404):
                return False, "地址格式错误或不存在"
            response.raise_for_status()
            payload = response.json()
            if not isinstance(payload, dict) or "chain_stats" not in payload:
                return False, "地址格式错误或不存在"
            return True, "已成功查询 Bitcoin 网络"
    return False, "暂不支持该地址所属的公链"

@router.get("/check")
async def check_wallet(address: str = Query(min_length=21, max_length=120)):
    value = address.strip()
    chain = identify_chain(value)
    if chain is None:
        return {"valid": False, "message": "地址格式错误或不存在"}
    try:
        valid, message = await verify_address_on_network(value, chain)
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=503, detail="暂时无法连接公链网络，请稍后重试") from exc
    except (ValueError, TypeError, KeyError) as exc:
        raise HTTPException(status_code=503, detail="公链返回数据暂时不可用，请稍后重试") from exc
    return {"valid": valid, "message": message, "address": value, "chain": chain,
            "eligibility_available": False, "eligible": None, "estimated_reward_usd": None}

@router.get("", response_model=list[WalletResponse])
async def list_wallets(db: Annotated[AsyncSession, Depends(get_db)], user: Annotated[User, Depends(get_current_user)]):
    rows = await db.scalars(select(Wallet).where(Wallet.user_id == user.id).order_by(Wallet.created_at.desc()))
    return [WalletResponse(id=str(w.id), public_key=w.public_key, chain=w.chain,
        daily_gas_usd=float(w.daily_gas_usd), recent_airdrop_income_usd=float(w.recent_airdrop_income_usd),
        last_synced_at=w.last_synced_at) for w in rows]

@router.get("/sync")
async def save_wallet_address(public_key: str = Query(min_length=21, max_length=120),
        db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    address = public_key.strip()
    chain = identify_chain(address)
    if chain is None:
        raise HTTPException(status_code=422, detail="公开钱包地址格式无效")
    wallet = await db.scalar(select(Wallet).where(Wallet.user_id == user.id, Wallet.public_key == address))
    if wallet is None:
        wallet = Wallet(user_id=user.id, public_key=address, chain=chain,
            daily_gas_usd=Decimal("0"), recent_airdrop_income_usd=Decimal("0"))
        db.add(wallet)
    wallet.chain = chain
    wallet.daily_gas_usd = Decimal("0")
    wallet.recent_airdrop_income_usd = Decimal("0")
    wallet.last_synced_at = None
    await db.commit()
    await db.refresh(wallet)
    return {"wallet_id": str(wallet.id), "public_key": address, "chain": chain,
        "daily_gas_usd": 0, "recent_airdrop_income_usd": 0, "status": "saved",
        "notice": "已保存公开地址；链上资产与收益统计尚未接入。"}
