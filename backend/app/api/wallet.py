import hashlib
import re
from fastapi import APIRouter, HTTPException, Query

router=APIRouter(prefix="/api/wallet",tags=["wallet"])
@router.get("/check")
async def check_wallet(address: str = Query(min_length=20,max_length=120)):
    value=address.strip()
    valid_evm=bool(re.fullmatch(r"0x[a-fA-F0-9]{40}",value))
    valid_base58=bool(re.fullmatch(r"[1-9A-HJ-NP-Za-km-z]{20,120}",value))
    if not (valid_evm or valid_base58):
        raise HTTPException(422,"请输入有效的公开钱包地址")
    # Deterministic simulation only; no blockchain or private-key access.
    digest=hashlib.sha256(value.encode()).digest()
    eligible=digest[0]%2==0
    reward=round(10+(int.from_bytes(digest[1:3],"big")%9000)/100,2) if eligible else 0.0
    return {"address":value,"eligible":eligible,"estimated_reward_usd":reward,
        "status":"simulated","notice":"模拟结果，仅用于界面联调；不代表真实链上资格或官方奖励。"}
