import re
import httpx
from decimal import Decimal
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from pydantic import BaseModel, Field, field_validator
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
    "BTC": ("Bitcoin", "B"), "Bitcoin": ("Bitcoin", "B"),
}

class WalletWrite(BaseModel):
    wallet_name: str = Field(min_length=1, max_length=80)
    chain: str = Field(min_length=1, max_length=24)
    address: str = Field(min_length=21, max_length=120)
    tags: list[str] = Field(default_factory=list, max_length=12)
    notes: str = Field(default="", max_length=500)

    @field_validator("wallet_name")
    @classmethod
    def normalize_wallet_name(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("钱包名称不能为空")
        return normalized

    @field_validator("tags")
    @classmethod
    def normalize_tags(cls, values: list[str]) -> list[str]:
        normalized: list[str] = []
        seen: set[str] = set()
        for tag in values:
            value = tag.strip()
            if not value:
                continue
            if len(value) > 24:
                raise ValueError("每个标签不能超过24个字符")
            key = value.casefold()
            if key not in seen:
                normalized.append(value)
                seen.add(key)
        return normalized

    @field_validator("notes")
    @classmethod
    def normalize_notes(cls, value: str) -> str:
        return value.strip()

BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

def _decode_base58(value: str) -> bytes | None:
    if not value or any(char not in BASE58_ALPHABET for char in value):
        return None
    number = 0
    for char in value:
        number = number * 58 + BASE58_ALPHABET.index(char)
    body = number.to_bytes((number.bit_length() + 7) // 8, "big") if number else b""
    return b"\\x00" * (len(value) - len(value.lstrip("1"))) + body

def _is_base58check(value: str, versions: set[int], payload_size: int) -> bool:
    import hashlib
    decoded = _decode_base58(value)
    if decoded is None or len(decoded) != payload_size + 4 or decoded[0] not in versions:
        return False
    checksum = hashlib.sha256(hashlib.sha256(decoded[:payload_size]).digest()).digest()[:4]
    return checksum == decoded[payload_size:]

def _bech32_polymod(values: list[int]) -> int:
    generators = (0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    checksum = 1
    for value in values:
        top = checksum >> 25
        checksum = ((checksum & 0x1ffffff) << 5) ^ value
        for index, generator in enumerate(generators):
            if (top >> index) & 1:
                checksum ^= generator
    return checksum

def _is_bitcoin_segwit(value: str) -> bool:
    charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    if not 14 <= len(value) <= 90 or not (value.islower() or value.isupper()):
        return False
    value = value.lower()
    separator = value.rfind("1")
    if separator < 1 or separator > 83 or separator + 7 >= len(value) or value[:separator] != "bc":
        return False
    try:
        data = [charset.index(char) for char in value[separator + 1:]]
    except ValueError:
        return False
    if len(data) < 7:
        return False
    hrp = value[:separator]
    expanded = [ord(char) >> 5 for char in hrp] + [0] + [ord(char) & 31 for char in hrp]
    checksum = _bech32_polymod(expanded + data)
    if checksum not in (1, 0x2bc830a3):
        return False
    version = data[0]
    if version > 16 or (version == 0 and checksum != 1) or (version != 0 and checksum != 0x2bc830a3):
        return False
    accumulator = bits = 0
    decoded: list[int] = []
    for item in data[1:-6]:
        accumulator = (accumulator << 5) | item
        bits += 5
        while bits >= 8:
            bits -= 8
            decoded.append((accumulator >> bits) & 0xff)
    if bits >= 5 or ((accumulator << (8 - bits)) & 0xff) or not 2 <= len(decoded) <= 40:
        return False
    if version == 0:
        return len(decoded) in (20, 32)
    if version == 1:
        return len(decoded) == 32
    return True

def is_valid_address_for_chain(address: str, chain: str) -> bool:
    value = address.strip()
    canonical = {
        "ethereum": "EVM", "erc20": "EVM", "evm": "EVM",
        "tron": "TRON", "trc20": "TRON",
        "sol": "SOL", "solana": "SOL",
        "btc": "Bitcoin", "bitcoin": "Bitcoin",
    }.get(chain.strip().lower())
    if canonical == "EVM":
        return re.fullmatch(r"0x[a-fA-F0-9]{40}", value) is not None
    if canonical == "TRON":
        return _is_base58check(value, {0x41}, 21)
    if canonical == "SOL":
        decoded = _decode_base58(value)
        return decoded is not None and len(decoded) == 32
    if canonical == "Bitcoin":
        return _is_base58check(value, {0x00, 0x05}, 21) or _is_bitcoin_segwit(value)
    return False

def identify_chain(address: str) -> str | None:
    for chain in ("EVM", "TRON", "Bitcoin", "SOL"):
        if is_valid_address_for_chain(address, chain):
            return chain
    return None

def _serialize(w: Wallet) -> dict:
    stored = {
        "evm": "EVM", "erc20": "EVM", "ethereum": "EVM",
        "tron": "TRON", "trc20": "TRON",
        "sol": "SOL", "solana": "SOL",
        "btc": "Bitcoin", "bitcoin": "Bitcoin",
    }.get((w.chain or "").strip().lower())
    canonical = stored or identify_chain(w.address or w.public_key) or w.chain
    return {"id": str(w.id), "wallet_name": w.wallet_name, "chain": canonical,
        "network_icon": {"EVM": "E", "TRON": "T", "SOL": "S", "Bitcoin": "B"}.get(canonical, w.network_icon),
        "ecosystem": canonical, "address": w.address or w.public_key,
        "public_key": w.address or w.public_key, "daily_gas_usd": float(w.daily_gas_usd or 0),
        "recent_airdrop_income_usd": float(w.recent_airdrop_income_usd or 0),
        "tags": list(w.tags or []), "notes": w.notes or "",
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
    if not is_valid_address_for_chain(payload.address, chain):
        raise HTTPException(422, "钱包地址格式或校验码与所选生态不匹配")
    address = payload.address.strip()
    existing = await db.scalar(select(Wallet).where(Wallet.user_id == user.id, Wallet.address == address))
    db_chain, icon = NETWORKS[chain]
    if existing:
        existing.wallet_name = payload.wallet_name.strip() or "Wallet"
        existing.chain = db_chain
        existing.network_icon = icon
        existing.public_key = address
        existing.tags = payload.tags
        existing.notes = payload.notes
        await db.commit(); await db.refresh(existing)
        return _serialize(existing)
    wallet = Wallet(user_id=user.id, wallet_name=payload.wallet_name.strip() or "Wallet",
        chain=db_chain, network_icon=icon, address=address, public_key=address,
        tags=payload.tags, notes=payload.notes,
        daily_gas_usd=Decimal("0"), recent_airdrop_income_usd=Decimal("0"))
    db.add(wallet); await db.commit(); await db.refresh(wallet)
    return _serialize(wallet)

@wallets_router.put("/{wallet_id}")
async def update_wallet(
    wallet_id: UUID,
    payload: WalletWrite,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    wallet = await db.scalar(select(Wallet).where(Wallet.id == wallet_id, Wallet.user_id == user.id))
    if wallet is None:
        raise HTTPException(404, "钱包不存在")
    chain = payload.chain.strip()
    if chain not in NETWORKS:
        raise HTTPException(422, "暂不支持该生态")
    address = payload.address.strip()
    if not is_valid_address_for_chain(address, chain):
        raise HTTPException(422, "钱包地址格式或校验码与所选生态不匹配")
    duplicate = await db.scalar(select(Wallet).where(
        Wallet.user_id == user.id, Wallet.address == address, Wallet.id != wallet_id
    ))
    if duplicate is not None:
        raise HTTPException(409, "该地址已经添加到钱包列表")
    db_chain, icon = NETWORKS[chain]
    wallet.wallet_name = payload.wallet_name.strip()
    wallet.chain = db_chain
    wallet.network_icon = icon
    wallet.address = address
    wallet.public_key = address
    wallet.tags = payload.tags
    wallet.notes = payload.notes
    await db.commit()
    await db.refresh(wallet)
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
