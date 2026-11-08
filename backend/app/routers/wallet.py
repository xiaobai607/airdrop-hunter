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
    "Bitcoin": ("Bitcoin", "B"), "BITCOIN": ("Bitcoin", "B"),
}

BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
BECH32_GENERATORS = (0x3B6A57B2, 0x26508E6D, 0x1EA119FA, 0x3D4233DD, 0x2A1462B3)


def _decode_base58(value: str) -> bytes | None:
    if not value or any(char not in BASE58_ALPHABET for char in value):
        return None
    number = 0
    for char in value:
        number = number * 58 + BASE58_ALPHABET.index(char)
    body = number.to_bytes((number.bit_length() + 7) // 8, "big") if number else b""
    return bytes(len(value) - len(value.lstrip("1"))) + body


def _is_bitcoin_base58check(value: str) -> bool:
    decoded = _decode_base58(value)
    if decoded is None or len(decoded) != 25 or decoded[0] not in (0x00, 0x05):
        return False
    payload, checksum = decoded[:21], decoded[21:]
    expected = hashlib.sha256(hashlib.sha256(payload).digest()).digest()[:4]
    return checksum == expected


def _bech32_polymod(values: list[int]) -> int:
    checksum = 1
    for value in values:
        top = checksum >> 25
        checksum = ((checksum & 0x1FFFFFF) << 5) ^ value
        for index, generator in enumerate(BECH32_GENERATORS):
            if (top >> index) & 1:
                checksum ^= generator
    return checksum


def _convert_five_to_eight(values: list[int]) -> bytes | None:
    accumulator = 0
    bit_count = 0
    output = bytearray()
    for value in values:
        if value < 0 or value > 31:
            return None
        accumulator = ((accumulator << 5) | value) & 0xFFF
        bit_count += 5
        while bit_count >= 8:
            bit_count -= 8
            output.append((accumulator >> bit_count) & 0xFF)
    if bit_count >= 5 or ((accumulator << (8 - bit_count)) & 0xFF):
        return None
    return bytes(output)


def _is_bitcoin_segwit(value: str) -> bool:
    if not 8 <= len(value) <= 90 or any(ord(char) < 33 or ord(char) > 126 for char in value):
        return False
    if value.lower() != value and value.upper() != value:
        return False
    normalized = value.lower()
    separator = normalized.rfind("1")
    if separator != 2 or normalized[:separator] != "bc" or separator + 7 > len(normalized):
        return False
    data = []
    for char in normalized[separator + 1:]:
        index = BECH32_CHARSET.find(char)
        if index < 0:
            return False
        data.append(index)
    if len(data) < 7 or not 0 <= data[0] <= 16:
        return False
    expanded_hrp = [ord(char) >> 5 for char in "bc"] + [0] + [ord(char) & 31 for char in "bc"]
    expected_checksum = 1 if data[0] == 0 else 0x2BC830A3
    if _bech32_polymod(expanded_hrp + data) != expected_checksum:
        return False
    witness_program = _convert_five_to_eight(data[1:-6])
    if witness_program is None or not 2 <= len(witness_program) <= 40:
        return False
    if data[0] == 0 and len(witness_program) not in (20, 32):
        return False
    if data[0] == 1 and len(witness_program) != 32:
        return False
    return True


class WalletWrite(BaseModel):
    wallet_name: str = Field(min_length=1, max_length=80)
    chain: str = Field(min_length=1, max_length=24)
    address: str = Field(min_length=21, max_length=120)

def identify_chain(address: str) -> str | None:
    value = address.strip()
    if re.fullmatch(r"0x[a-fA-F0-9]{40}", value): return "EVM"
    if re.fullmatch(r"T[1-9A-HJ-NP-Za-km-z]{33}", value): return "TRON"
    if _is_bitcoin_segwit(value) or _is_bitcoin_base58check(value): return "Bitcoin"
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
    expected = {"Ethereum": "EVM", "ERC20": "EVM", "Tron": "TRON", "TRC20": "TRON", "Solana": "SOL", "BITCOIN": "Bitcoin"}.get(chain, chain)
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

@router.get("", include_in_schema=False)
async def legacy_list_wallets(db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    return await _list_wallets(db, user)

@router.get("/sync", include_in_schema=False)
async def legacy_save_wallet(public_key: str = Query(min_length=21, max_length=120), db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    chain = identify_chain(public_key)
    if chain is None: raise HTTPException(422, "公开钱包地址格式无效")
    label, icon = NETWORKS[chain]
    wallet = await db.scalar(select(Wallet).where(Wallet.user_id == user.id, Wallet.address == public_key))
    if wallet is None:
        wallet = Wallet(user_id=user.id, wallet_name="Wallet", chain=label, network_icon=icon, address=public_key, public_key=public_key)
        db.add(wallet)
    await db.commit(); await db.refresh(wallet)
    return {**_serialize(wallet), "status":"saved", "notice":"仅保存公开地址，不涉及私钥。"}
