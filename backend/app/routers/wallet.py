import re
import hashlib
import httpx
from decimal import Decimal
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.routers.auth import get_current_user, limiter
from app.database import get_db
from app.models import User, Wallet

router = APIRouter(prefix="/api/wallet", tags=["wallet"])
wallets_router = APIRouter(prefix="/api/wallets", tags=["wallets"])

# Legacy chain labels remain accepted for older app builds.
NETWORKS = {
    "EVM": ("EVM", "E"), "Ethereum": ("ERC20", "E"), "ERC20": ("ERC20", "E"),
    "TRON": ("TRC20", "T"), "Tron": ("TRC20", "T"), "TRC20": ("TRC20", "T"),
    "SOL": ("Solana", "S"), "Solana": ("Solana", "S"),
    "Bitcoin": ("Bitcoin", "B"),
}

class WalletWrite(BaseModel):
    wallet_name: str = Field(min_length=1, max_length=80)
    chain: str = Field(min_length=1, max_length=24)
    address: str = Field(min_length=21, max_length=120)

def _is_valid_bitcoin_base58(value: str) -> bool:
    alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    if not 26 <= len(value) <= 35 or any(char not in alphabet for char in value):
        return False
    number = 0
    for char in value:
        number = number * 58 + alphabet.index(char)
    decoded = number.to_bytes((number.bit_length() + 7) // 8, "big") if number else b""
    leading_zeros = len(value) - len(value.lstrip("1"))
    payload = (b"\x00" * leading_zeros) + decoded
    if len(payload) != 25 or payload[0] not in (0x00, 0x05):
        return False
    checksum = hashlib.sha256(hashlib.sha256(payload[:21]).digest()).digest()[:4]
    return checksum == payload[21:]


def identify_chain(address: str) -> str | None:
    value = address.strip()
    if re.fullmatch(r"0x[a-fA-F0-9]{40}", value): return "EVM"
    if re.fullmatch(r"T[1-9A-HJ-NP-Za-km-z]{33}", value): return "TRON"
    if re.fullmatch(r"bc1[ac-hj-np-z02-9]{11,71}", value, re.IGNORECASE) or _is_valid_bitcoin_base58(value): return "Bitcoin"
    if re.fullmatch(r"[1-9A-HJ-NP-Za-km-z]{32,44}", value): return "SOL"
    return None

def _serialize(w: Wallet) -> dict:
    detected = identify_chain(w.address or w.public_key)
    canonical = detected or w.chain
    return {"id": str(w.id), "wallet_name": w.wallet_name, "chain": canonical,
        "network_icon": {"EVM": "E", "TRON": "T", "SOL": "S", "Bitcoin": "B"}.get(canonical, w.network_icon),
        "ecosystem": canonical, "address": w.address or w.public_key,
        "public_key": w.address or w.public_key, "daily_gas_usd": float(w.daily_gas_usd or 0),
        "recent_airdrop_income_usd": float(w.recent_airdrop_income_usd or 0),
        "last_synced_at": w.last_synced_at}

async def _list_wallets(db: AsyncSession, user: User):
    rows = await db.scalars(select(Wallet).where(Wallet.user_id == user.id).order_by(Wallet.created_at.desc()))
    return [_serialize(w) for w in rows]

@wallets_router.get("")
async def list_wallets(db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    return await _list_wallets(db, user)

@wallets_router.post("", status_code=status.HTTP_201_CREATED)
async def add_wallet(payload: WalletWrite, db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    chain = payload.chain.strip()
    if chain not in NETWORKS: raise HTTPException(422, "暂不支持该生态")
    detected = identify_chain(payload.address)
    expected = {"Ethereum": "EVM", "ERC20": "EVM", "Tron": "TRON", "TRC20": "TRON", "Solana": "SOL"}.get(chain, chain)
    if detected != expected: raise HTTPException(422, "钱包地址格式与所选生态不匹配")
    address = payload.address.strip()
    existing = await db.scalar(select(Wallet).where(Wallet.user_id == user.id, Wallet.address == address))
    db_chain, icon = NETWORKS[chain]
    if existing:
        existing.wallet_name = payload.wallet_name.strip() or "Wallet"
        existing.chain = db_chain
        existing.network_icon = icon
        existing.public_key = address
        await db.commit(); await db.refresh(existing)
        return _serialize(existing)
    wallet = Wallet(user_id=user.id, wallet_name=payload.wallet_name.strip() or "Wallet",
        chain=db_chain, network_icon=icon, address=address, public_key=address,
        daily_gas_usd=Decimal("0"), recent_airdrop_income_usd=Decimal("0"))
    db.add(wallet); await db.commit(); await db.refresh(wallet)
    return _serialize(wallet)

@wallets_router.delete("/{wallet_id}", status_code=204)
async def delete_wallet(wallet_id: UUID, db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    wallet = await db.scalar(select(Wallet).where(Wallet.id == wallet_id, Wallet.user_id == user.id))
    if wallet is None: raise HTTPException(404, "钱包不存在")
    await db.delete(wallet); await db.commit()
    return None

@router.get("/check")
@limiter.limit("10/minute")
async def check_wallet(request: Request, address: str = Query(min_length=21, max_length=120)):
    value = address.strip()
    chain = identify_chain(value)
    if chain is None: return {"valid": False, "message": "地址格式错误或不存在"}
    timeout = httpx.Timeout(15.0, connect=8.0)
    try:
        async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": "AirdropHunter/1.0"}) as client:
            if chain == "EVM":
                response = await client.post("https://cloudflare-eth.com", json={"jsonrpc":"2.0","id":1,"method":"eth_getBalance","params":[value,"latest"]})
                response.raise_for_status(); payload = response.json()
                valid = isinstance(payload.get("result"), str) and not payload.get("error")
            elif chain == "SOL":
                response = await client.post("https://api.mainnet-beta.solana.com", json={"jsonrpc":"2.0","id":1,"method":"getAccountInfo","params":[value,{"encoding":"base64"}]})
                response.raise_for_status(); payload = response.json()
                valid = not payload.get("error") and (payload.get("result") or {}).get("value") is not None
            elif chain == "TRON":
                response = await client.get("https://apilist.tronscanapi.com/api/account", params={"address":value})
                response.raise_for_status(); payload = response.json()
                valid = bool(payload and payload.get("address"))
            else:
                response = await client.get("https://blockstream.info/api/address/" + value)
                if response.status_code in (400,404): valid = False
                else:
                    response.raise_for_status(); payload = response.json()
                    valid = isinstance(payload, dict) and "chain_stats" in payload
    except httpx.HTTPError as exc:
        raise HTTPException(503, "公链网络暂时不可用，请稍后重试") from exc
    return {"valid": bool(valid), "message": "地址已通过公链查询" if valid else "地址格式错误或不存在", "chain": chain}

