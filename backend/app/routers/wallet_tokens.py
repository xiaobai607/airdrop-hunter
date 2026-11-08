import asyncio
import os
import re
import time
from decimal import Decimal, InvalidOperation
from typing import Literal

import httpx
from fastapi import APIRouter, HTTPException, Query, Request
from pydantic import BaseModel

from app.routers.auth import limiter
from app.routers.wallet_activity import ETHERSCAN_V2, EVM_NETWORKS

router = APIRouter(prefix="/api/wallet-tokens", tags=["wallet tokens"])
_ETHERSCAN_LOCK = asyncio.Lock()
_LAST_REQUEST_AT = 0.0
_PAGE_SIZE = 100
_MAX_PAGES = 50


class EvmTokenHolding(BaseModel):
    chain: str
    contract_address: str
    name: str
    symbol: str
    quantity: str
    decimals: int


class EvmTokenNetworkResult(BaseModel):
    chain: str
    status: Literal["available", "unavailable", "truncated"]
    detail: str | None = None


class EvmTokenPortfolioResponse(BaseModel):
    holdings: list[EvmTokenHolding]
    networks: list[EvmTokenNetworkResult]


def _parse_holding(row: dict, chain: str) -> EvmTokenHolding | None:
    try:
        address = str(row.get("TokenAddress") or "").strip()
        if not re.fullmatch(r"0x[a-fA-F0-9]{40}", address):
            return None
        decimals = int(row.get("TokenDivisor"))
        if decimals < 0 or decimals > 36:
            return None
        raw_quantity = Decimal(str(row.get("TokenQuantity")))
        if not raw_quantity.is_finite() or raw_quantity <= 0:
            return None
        quantity = raw_quantity.scaleb(-decimals)
        name = str(row.get("TokenName") or "").strip()[:120]
        symbol = str(row.get("TokenSymbol") or "").strip()[:32]
        if not name or not symbol:
            return None
        return EvmTokenHolding(
            chain=chain,
            contract_address=address.lower(),
            name=name,
            symbol=symbol,
            quantity=format(quantity.normalize(), "f"),
            decimals=decimals,
        )
    except (InvalidOperation, TypeError, ValueError):
        return None


async def _request_page(
    client: httpx.AsyncClient,
    chain: str,
    address: str,
    api_key: str,
    page: int,
) -> list[dict] | None:
    global _LAST_REQUEST_AT
    async with _ETHERSCAN_LOCK:
        elapsed = time.monotonic() - _LAST_REQUEST_AT
        if elapsed < 0.36:
            await asyncio.sleep(0.36 - elapsed)
        _LAST_REQUEST_AT = time.monotonic()
        response = await client.get(
            ETHERSCAN_V2,
            params={
                "chainid": str(EVM_NETWORKS[chain]["chain_id"]),
                "module": "account",
                "action": "addresstokenbalance",
                "address": address,
                "page": str(page),
                "offset": str(_PAGE_SIZE),
                "apikey": api_key,
            },
        )
    response.raise_for_status()
    payload = response.json()
    if str(payload.get("status")) == "1":
        result = payload.get("result")
        if not isinstance(result, list):
            raise HTTPException(502, "Etherscan 代币资产响应格式无效")
        return result
    message = str(payload.get("result") or payload.get("message") or "")
    if "no token" in message.lower() or "no records" in message.lower():
        return []
    # Some Etherscan plans/networks do not expose the portfolio endpoint.
    # Treat that as unavailable rather than silently claiming a zero balance.
    return None


@router.get("/evm/{address}", response_model=EvmTokenPortfolioResponse)
@limiter.limit("6/minute")
async def get_evm_token_portfolio(
    request: Request,
    address: str,
):
    if not re.fullmatch(r"0x[a-fA-F0-9]{40}", address):
        raise HTTPException(422, "无效的 EVM 地址")
    api_key = (os.getenv("ETHERSCAN_API_KEY") or "").strip()
    if not api_key:
        raise HTTPException(503, "EVM 代币资产服务尚未配置")
    holdings: list[EvmTokenHolding] = []
    network_results: list[EvmTokenNetworkResult] = []
    timeout = httpx.Timeout(25.0, connect=8.0)
    try:
        async with httpx.AsyncClient(
            timeout=timeout,
            headers={"User-Agent": "AirdropHunter/1.0"},
        ) as client:
            for chain in EVM_NETWORKS:
                chain_holdings: list[EvmTokenHolding] = []
                status: Literal["available", "unavailable", "truncated"] = "available"
                detail = None
                try:
                    for page in range(1, _MAX_PAGES + 1):
                        rows = await _request_page(client, chain, address, api_key, page)
                        if rows is None:
                            status = "unavailable"
                            chain_holdings.clear()
                            detail = "当前 Etherscan API Key 或该网络未开放代币持仓接口"
                            break
                        parsed = [
                            holding for row in rows
                            if isinstance(row, dict)
                            and (holding := _parse_holding(row, chain)) is not None
                        ]
                        chain_holdings.extend(parsed)
                        if len(rows) < _PAGE_SIZE:
                            break
                        if page == _MAX_PAGES:
                            status = "truncated"
                except (httpx.HTTPError, HTTPException):
                    # A single explorer outage or rate limit must not hide the
                    # successfully queried networks in the same wallet response.
                    status = "unavailable"
                    chain_holdings.clear()
                    detail = "该网络的 Etherscan 代币持仓服务暂时不可用"
                holdings.extend(chain_holdings)
                if status == "truncated":
                    detail = "代币数量超过单次读取上限，结果不完整"
                network_results.append(
                    EvmTokenNetworkResult(chain=chain, status=status, detail=detail)
                )
    except httpx.HTTPStatusError as exc:
        raise HTTPException(503, "Etherscan 代币资产服务暂时不可用") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(503, "Etherscan 代币资产服务连接失败") from exc
    return EvmTokenPortfolioResponse(holdings=holdings, networks=network_results)
