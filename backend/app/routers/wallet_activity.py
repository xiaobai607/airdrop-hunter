import os
from decimal import Decimal, InvalidOperation
from typing import Annotated
from uuid import UUID

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query, Request
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import User, Wallet
from app.routers.auth import get_current_user, limiter

router = APIRouter(prefix="/api/wallets", tags=["wallet activity"])
ETHERSCAN_V2 = "https://api.etherscan.io/v2/api"
COINPAPRIKA_ETH = "https://api.coinpaprika.com/v1/tickers/eth-ethereum"
WEI_PER_ETH = Decimal("1000000000000000000")


class EvmTransaction(BaseModel):
    hash: str
    timestamp: int
    from_address: str
    to_address: str
    method_id: str | None
    gas_used: str
    effective_gas_price_wei: str
    gas_cost_eth: str
    gas_price_usd: str | None
    gas_cost_usd: str | None
    status: str
    project_official_id: None = None
    attribution: str = "unassigned"


async def fetch_eth_price(client: httpx.AsyncClient) -> Decimal | None:
    try:
        response = await client.get(COINPAPRIKA_ETH)
        response.raise_for_status()
        value = response.json()["quotes"]["USD"]["price"]
        price = Decimal(str(value))
        return price if price > 0 else None
    except (httpx.HTTPError, KeyError, TypeError, ValueError, InvalidOperation):
        return None


@router.get("/{wallet_id}/evm-transactions", response_model=list[EvmTransaction])
@limiter.limit("10/minute")
async def list_evm_transactions(
    request: Request,
    wallet_id: UUID,
    chain_id: int = Query(default=1, ge=1),
    limit: int = Query(default=50, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    # Start with Ethereum mainnet only. Never silently query a different chain.
    if chain_id != 1:
        raise HTTPException(422, "当前真实交易采集仅支持 Ethereum 主网")
    wallet = await db.scalar(
        select(Wallet).where(Wallet.id == wallet_id, Wallet.user_id == user.id)
    )
    if wallet is None:
        raise HTTPException(404, "钱包不存在")
    address = (wallet.address or wallet.public_key or "").strip()
    if not address.lower().startswith("0x") or len(address) != 42:
        raise HTTPException(422, "该钱包不是有效的 EVM 地址")

    api_key = (os.getenv("ETHERSCAN_API_KEY") or "").strip()
    if not api_key:
        raise HTTPException(503, "链上交易服务尚未配置")
    timeout = httpx.Timeout(20.0, connect=8.0)
    async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": "AirdropHunter/1.0"}) as client:
        try:
            response = await client.get(
                ETHERSCAN_V2,
                params={
                    "chainid": "1",
                    "module": "account",
                    "action": "txlist",
                    "address": address,
                    "startblock": "0",
                    "endblock": "99999999",
                    "page": "1",
                    "offset": str(limit),
                    "sort": "desc",
                    "apikey": api_key,
                },
            )
            response.raise_for_status()
            payload = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise HTTPException(503, "链上交易服务暂时不可用") from exc

        if str(payload.get("status")) != "1":
            message = str(payload.get("message") or "")
            result = str(payload.get("result") or "")
            if "No transactions found" in result:
                rows = []
            else:
                raise HTTPException(503, "暂时无法读取该钱包的链上交易")
        else:
            rows = payload.get("result")
            if not isinstance(rows, list):
                raise HTTPException(502, "链上交易服务返回了无效数据")
        eth_price = await fetch_eth_price(client)

    transactions = []
    for row in rows:
        try:
            tx_hash = str(row["hash"])
            gas_used = int(row["gasUsed"])
            gas_price_wei = int(row["gasPrice"])
            timestamp = int(row["timeStamp"])
            if (
                len(tx_hash) != 66
                or not tx_hash.startswith("0x")
                or gas_used < 0
                or gas_price_wei < 0
                or str(row.get("isError", "0")) != "0"
            ):
                continue
            gas_eth = Decimal(gas_used) * Decimal(gas_price_wei) / WEI_PER_ETH
            gas_usd = gas_eth * eth_price if eth_price is not None else None
            transactions.append(EvmTransaction(
                hash=tx_hash,
                timestamp=timestamp,
                from_address=str(row.get("from") or ""),
                to_address=str(row.get("to") or ""),
                method_id=(str(row.get("methodId") or "") or None),
                gas_used=str(gas_used),
                effective_gas_price_wei=str(gas_price_wei),
                gas_cost_eth=format(gas_eth, "f"),
                gas_price_usd=format(eth_price, "f") if eth_price is not None else None,
                gas_cost_usd=format(gas_usd, "f") if gas_usd is not None else None,
                status="success",
            ))
        except (KeyError, TypeError, ValueError, InvalidOperation):
            continue
    return transactions
