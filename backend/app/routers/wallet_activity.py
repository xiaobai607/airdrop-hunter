import os
import re
from decimal import Decimal, InvalidOperation
from typing import Literal

import httpx
from fastapi import APIRouter, HTTPException, Query, Request
from pydantic import BaseModel
from slowapi import Limiter
from slowapi.util import get_remote_address

router = APIRouter(prefix="/api/wallet-activity", tags=["wallet activity"])
limiter = Limiter(key_func=get_remote_address)
ETHERSCAN_V2 = "https://api.etherscan.io/v2/api"
TRONGRID = "https://api.trongrid.io"
COINPAPRIKA_ETH = "https://api.coinpaprika.com/v1/tickers/eth-ethereum"
COINPAPRIKA_TRX = "https://api.coinpaprika.com/v1/tickers/trx-tron"
WEI_PER_ETH = Decimal("1000000000000000000")
SUN_PER_TRX = Decimal("1000000")


class WalletTransaction(BaseModel):
    chain: str
    hash: str
    timestamp: int
    from_address: str
    to_address: str
    method_id: str | None = None
    gas_used: str
    gas_price_base_unit: str
    gas_cost_native: str
    native_symbol: str
    gas_price_usd: str | None = None
    gas_cost_usd: str | None = None
    status: str
    project_official_id: None = None
    attribution: str = "unassigned"


async def fetch_price(client: httpx.AsyncClient, chain: str) -> Decimal | None:
    url = COINPAPRIKA_ETH if chain == "ethereum" else COINPAPRIKA_TRX
    try:
        response = await client.get(url)
        response.raise_for_status()
        price = Decimal(str(response.json()["quotes"]["USD"]["price"]))
        return price if price > 0 else None
    except (httpx.HTTPError, KeyError, TypeError, ValueError, InvalidOperation):
        return None


async def evm_transactions(client: httpx.AsyncClient, address: str, limit: int, api_key: str):
    response = await client.get(ETHERSCAN_V2, params={
        "chainid": "1", "module": "account", "action": "txlist",
        "address": address, "startblock": "0", "endblock": "99999999",
        "page": "1", "offset": str(limit), "sort": "desc", "apikey": api_key,
    })
    response.raise_for_status()
    payload = response.json()
    if str(payload.get("status")) != "1":
        if "No transactions found" in str(payload.get("result") or ""):
            return []
        raise HTTPException(503, "暂时无法读取 Ethereum 链上交易")
    if not isinstance(payload.get("result"), list):
        raise HTTPException(502, "Ethereum 交易服务返回无效数据")
    return payload["result"]


async def tron_transactions(client: httpx.AsyncClient, address: str, limit: int):
    headers = {}
    key = (os.getenv("TRONGRID_API_KEY") or "").strip()
    if key:
        headers["TRON-PRO-API-KEY"] = key
    response = await client.get(
        f"{TRONGRID}/v1/accounts/{address}/transactions",
        params={"only_confirmed": "true", "limit": str(limit), "order_by": "block_timestamp,desc"},
        headers=headers,
    )
    response.raise_for_status()
    payload = response.json()
    rows = payload.get("data")
    if not isinstance(rows, list):
        raise HTTPException(502, "TRON 交易服务返回无效数据")
    return rows


@router.get("/{chain}/{address}/transactions", response_model=list[WalletTransaction])
@limiter.limit("12/minute")
async def list_wallet_transactions(
    request: Request,
    chain: Literal["ethereum", "tron"],
    address: str,
    limit: int = Query(default=50, ge=1, le=100),
):
    if chain == "ethereum" and not re.fullmatch(r"0x[a-fA-F0-9]{40}", address):
        raise HTTPException(422, "无效的 Ethereum 地址")
    if chain == "tron" and not re.fullmatch(r"T[1-9A-HJ-NP-Za-km-z]{33}", address):
        raise HTTPException(422, "无效的 TRON 地址")

    api_key = (os.getenv("ETHERSCAN_API_KEY") or "").strip()
    if chain == "ethereum" and not api_key:
        raise HTTPException(503, "Ethereum 交易服务尚未配置")
    timeout = httpx.Timeout(25.0, connect=8.0)
    try:
        async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": "AirdropHunter/1.0"}) as client:
            rows = await (evm_transactions(client, address, limit, api_key) if chain == "ethereum"
                          else tron_transactions(client, address, limit))
            price = await fetch_price(client, chain)
            results = []
            if chain == "ethereum":
                for row in rows:
                    try:
                        tx_hash = str(row["hash"])
                        gas_used, gas_price = int(row["gasUsed"]), int(row["gasPrice"])
                        timestamp = int(row["timeStamp"])
                        if len(tx_hash) != 66 or not tx_hash.startswith("0x") or gas_used < 0 or gas_price < 0:
                            continue
                        if str(row.get("isError", "0")) != "0":
                            continue
                        native_cost = Decimal(gas_used) * Decimal(gas_price) / WEI_PER_ETH
                        results.append(WalletTransaction(
                            chain="ethereum", hash=tx_hash, timestamp=timestamp,
                            from_address=str(row.get("from") or ""), to_address=str(row.get("to") or ""),
                            method_id=str(row.get("methodId") or "") or None,
                            gas_used=str(gas_used), gas_price_base_unit=str(gas_price),
                            gas_cost_native=format(native_cost, "f"), native_symbol="ETH",
                            gas_price_usd=format(price, "f") if price is not None else None,
                            gas_cost_usd=format(native_cost * price, "f") if price is not None else None,
                            status="success",
                        ))
                    except (KeyError, TypeError, ValueError, InvalidOperation):
                        continue
            else:
                for row in rows:
                    try:
                        tx_hash = str(row["txID"])
                        timestamp = int(row["block_timestamp"]) // 1000
                        if len(tx_hash) != 64 or not re.fullmatch(r"[0-9a-fA-F]{64}", tx_hash):
                            continue
                        info_response = await client.post(f"{TRONGRID}/wallet/gettransactioninfobyid", json={"value": tx_hash})
                        info_response.raise_for_status()
                        info = info_response.json()
                        if not info or info.get("id", "").lower() != tx_hash.lower():
                            continue
                        fee_sun = int(info.get("fee") or 0)
                        if fee_sun < 0:
                            continue
                        native_cost = Decimal(fee_sun) / SUN_PER_TRX
                        raw_contracts = (row.get("raw_data") or {}).get("contract") or []
                        value = (((raw_contracts[0] if raw_contracts else {}).get("parameter") or {}).get("value") or {})
                        results.append(WalletTransaction(
                            chain="tron", hash=tx_hash, timestamp=timestamp,
                            from_address=str(value.get("owner_address") or address),
                            to_address=str(value.get("to_address") or ""),
                            method_id=None, gas_used=str(info.get("receipt", {}).get("energy_usage_total") or 0),
                            gas_price_base_unit=str(info.get("receipt", {}).get("energy_fee") or 0),
                            gas_cost_native=format(native_cost, "f"), native_symbol="TRX",
                            gas_price_usd=format(price, "f") if price is not None else None,
                            gas_cost_usd=format(native_cost * price, "f") if price is not None else None,
                            status="success" if info.get("receipt", {}).get("result", "SUCCESS") == "SUCCESS" else "failed",
                        ))
                    except (httpx.HTTPError, KeyError, TypeError, ValueError, InvalidOperation):
                        continue
            return results
    except httpx.HTTPStatusError as exc:
        raise HTTPException(503, "链上数据服务暂时不可用") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(503, "链上数据服务连接失败") from exc
