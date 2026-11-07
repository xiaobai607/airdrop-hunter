import os
import re
from typing import Annotated

import httpx
from fastapi import APIRouter, HTTPException, Query, Request
from slowapi import Limiter
from app.routers.auth import limiter

router = APIRouter(prefix="/api/gas", tags=["gas"])

EVM_ADDRESS_PATTERN = re.compile(r"^0x[a-fA-F0-9]{40}$")
TX_HASH_PATTERN = re.compile(r"^0x[a-fA-F0-9]{64}$")
UINT_PATTERN = re.compile(r"^[0-9]+$")


@router.get("/evm/latest")
@limiter.limit("30/minute")
async def latest_evm_transaction(
    request: Request,
    address: Annotated[str, Query(min_length=42, max_length=42, pattern=r"^0x[a-fA-F0-9]{40}$")],
):
    if not EVM_ADDRESS_PATTERN.fullmatch(address):
        raise HTTPException(status_code=422, detail="Invalid EVM address")

    api_key = os.getenv("ETHERSCAN_API_KEY", "").strip()
    if not api_key:
        raise HTTPException(status_code=503, detail="Gas data service is not configured")

    params = {
        "chainid": "1",
        "module": "account",
        "action": "txlist",
        "address": address,
        "page": "1",
        "offset": "1",
        "sort": "desc",
        "apikey": api_key,
    }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get("https://api.etherscan.io/v2/api", params=params)
            response.raise_for_status()
            payload = response.json()
    except (httpx.HTTPError, ValueError):
        raise HTTPException(status_code=502, detail="Gas data provider is temporarily unavailable")

    transactions = payload.get("result")
    if payload.get("status") != "1" or not isinstance(transactions, list):
        if isinstance(transactions, str) and "no transactions found" in transactions.lower():
            raise HTTPException(status_code=404, detail="No EVM transaction found")
        raise HTTPException(status_code=502, detail="Gas data provider returned an invalid response")

    if not transactions:
        raise HTTPException(status_code=404, detail="No EVM transaction found")

    transaction = transactions[0]
    tx_hash = str(transaction.get("hash", ""))
    gas_used = str(transaction.get("gasUsed", ""))
    gas_price = str(transaction.get("gasPrice", ""))
    if (
        not TX_HASH_PATTERN.fullmatch(tx_hash)
        or not UINT_PATTERN.fullmatch(gas_used)
        or not UINT_PATTERN.fullmatch(gas_price)
    ):
        raise HTTPException(status_code=502, detail="Gas data provider returned invalid transaction data")

    return {"hash": tx_hash, "gasUsed": gas_used, "gasPrice": gas_price}
