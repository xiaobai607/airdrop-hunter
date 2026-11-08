import os
import re
import hashlib
import asyncio
import time
from decimal import Decimal, InvalidOperation
from typing import Literal

import httpx
from fastapi import APIRouter, HTTPException, Query, Request
from pydantic import BaseModel
from app.routers.auth import limiter
from app.routers.wallet import identify_chain

router = APIRouter(prefix="/api/wallet-activity", tags=["wallet activity"])
ETHERSCAN_V2 = "https://api.etherscan.io/v2/api"
TRONGRID = "https://api.trongrid.io"
BLOCKSTREAM = "https://blockstream.info/api"
SATOSHIS_PER_BTC = Decimal("100000000")
COINPAPRIKA_BASE = "https://api.coinpaprika.com/v1/tickers/"
COINPAPRIKA_ETH = COINPAPRIKA_BASE + "eth-ethereum"
COINPAPRIKA_TRX = COINPAPRIKA_BASE + "trx-tron"
EVM_NETWORKS = {
    "ethereum": {"chain_id": 1, "name": "Ethereum", "native_symbol": "ETH", "price_id": "eth-ethereum"},
    "bsc": {"chain_id": 56, "name": "BNB Smart Chain", "native_symbol": "BNB", "price_id": "bnb-binance-coin"},
    "polygon": {"chain_id": 137, "name": "Polygon PoS", "native_symbol": "POL", "price_id": "pol-polygon-ecosystem-token"},
    "arbitrum": {"chain_id": 42161, "name": "Arbitrum One", "native_symbol": "ETH", "price_id": "eth-ethereum"},
    "optimism": {"chain_id": 10, "name": "OP Mainnet", "native_symbol": "ETH", "price_id": "eth-ethereum"},
    "base": {"chain_id": 8453, "name": "Base", "native_symbol": "ETH", "price_id": "eth-ethereum"},
    "avalanche": {"chain_id": 43114, "name": "Avalanche C-Chain", "native_symbol": "AVAX", "price_id": "avax-avalanche"},
}
_ETHERSCAN_REQUEST_LOCK = asyncio.Lock()
_ETHERSCAN_LAST_REQUEST_AT = 0.0
WEI_PER_ETH = Decimal("1000000000000000000")
SUN_PER_TRX = Decimal("1000000")


def decimal_text(value: Decimal) -> str:
    return format(value.normalize(), "f") if value else "0"


def tron_address_to_hex(address: str) -> str | None:
    alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    number = 0
    try:
        for character in address:
            number = number * 58 + alphabet.index(character)
    except ValueError:
        return None
    decoded = number.to_bytes((number.bit_length() + 7) // 8, "big") if number else b""
    decoded = bytes(len(address) - len(address.lstrip("1"))) + decoded
    if len(decoded) != 25:
        return None
    payload, checksum = decoded[:-4], decoded[-4:]
    if payload[0] != 0x41:
        return None
    expected = hashlib.sha256(hashlib.sha256(payload).digest()).digest()[:4]
    if checksum != expected:
        return None
    return payload.hex()


class WalletTransaction(BaseModel):
    chain: str
    hash: str
    timestamp: int
    from_address: str
    to_address: str
    method_id: str | None = None
    gas_used: str | None = None
    gas_price_base_unit: str | None = None
    total_fee_base_unit: str
    fee_type: str
    energy_used: str | None = None
    bandwidth_used: str | None = None
    energy_fee_base_unit: str | None = None
    bandwidth_fee_base_unit: str | None = None
    gas_cost_native: str
    native_symbol: str
    native_price_usd: str | None = None
    gas_cost_usd: str | None = None
    fee_paid_by_wallet: bool
    status: str
    project_official_id: None = None
    attribution: str = "unassigned"


class WalletActivityBatchResponse(BaseModel):
    transactions: list[WalletTransaction]
    networks_checked: list[str]
    networks_unavailable: list[str]


async def fetch_price(client: httpx.AsyncClient, price_id: str) -> Decimal | None:
    try:
        response = await client.get(COINPAPRIKA_BASE + price_id)
        response.raise_for_status()
        price = Decimal(str(response.json()["quotes"]["USD"]["price"]))
        return price if price > 0 else None
    except (httpx.HTTPError, KeyError, TypeError, ValueError, InvalidOperation):
        return None


async def evm_transactions(
    client: httpx.AsyncClient,
    address: str,
    limit: int,
    api_key: str,
    chain: str,
):
    global _ETHERSCAN_LAST_REQUEST_AT
    network = EVM_NETWORKS[chain]
    async with _ETHERSCAN_REQUEST_LOCK:
        elapsed = time.monotonic() - _ETHERSCAN_LAST_REQUEST_AT
        if elapsed < 0.36:
            await asyncio.sleep(0.36 - elapsed)
        _ETHERSCAN_LAST_REQUEST_AT = time.monotonic()
        response = await client.get(ETHERSCAN_V2, params={
            "chainid": str(network["chain_id"]), "module": "account", "action": "txlist",
            "address": address, "startblock": "0", "endblock": "99999999",
            "page": "1", "offset": str(limit), "sort": "desc", "apikey": api_key,
        })
    response.raise_for_status()
    payload = response.json()
    if str(payload.get("status")) != "1":
        if "No transactions found" in str(payload.get("result") or ""):
            return []
        raise HTTPException(503, f"暂时无法读取 {network['name']} 链上交易")
    if not isinstance(payload.get("result"), list):
        raise HTTPException(502, f"{network['name']} 交易服务返回无效数据")
    return payload["result"]


def build_evm_transactions(rows, chain: str, address: str, price: Decimal | None):
    network = EVM_NETWORKS[chain]
    results = []
    for row in rows:
        try:
            tx_hash = str(row["hash"])
            gas_used, gas_price = int(row["gasUsed"]), int(row["gasPrice"])
            timestamp = int(row["timeStamp"])
            l1_fee_wei = int(row.get("l1Fee") or 0)
            if (
                len(tx_hash) != 66
                or not tx_hash.startswith("0x")
                or not re.fullmatch(r"0x[0-9a-fA-F]{64}", tx_hash)
                or gas_used < 0
                or gas_price < 0
                or l1_fee_wei < 0
            ):
                continue
            total_fee_wei = gas_used * gas_price + l1_fee_wei
            native_cost = Decimal(total_fee_wei) / WEI_PER_ETH
            results.append(WalletTransaction(
                chain=chain, hash=tx_hash, timestamp=timestamp,
                from_address=str(row.get("from") or ""), to_address=str(row.get("to") or ""),
                method_id=str(row.get("methodId") or "") or None,
                gas_used=str(gas_used), gas_price_base_unit=str(gas_price),
                total_fee_base_unit=str(total_fee_wei),
                fee_type=(
                    "evm_gas_fee_with_l1_wei" if l1_fee_wei
                    else "evm_l2_execution_fee_wei" if chain in {"optimism", "base"}
                    else "evm_gas_fee_wei"
                ),
                gas_cost_native=decimal_text(native_cost), native_symbol=network["native_symbol"],
                native_price_usd=decimal_text(price) if price is not None else None,
                gas_cost_usd=decimal_text(native_cost * price) if price is not None else None,
                fee_paid_by_wallet=str(row.get("from") or "").lower() == address.lower(),
                status="success" if str(row.get("isError", "0")) == "0" else "failed",
            ))
        except (KeyError, TypeError, ValueError, InvalidOperation):
            continue
    return results


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


async def bitcoin_transactions(client: httpx.AsyncClient, address: str, limit: int):
    response = await client.get(f"{BLOCKSTREAM}/address/{address}/txs")
    response.raise_for_status()
    first_page = response.json()
    if not isinstance(first_page, list):
        raise HTTPException(502, "Bitcoin 交易服务返回无效数据")

    mempool_rows = [
        row for row in first_page if not (row.get("status") or {}).get("confirmed")
    ][:min(limit, 50)]
    confirmed_rows = [
        row for row in first_page if (row.get("status") or {}).get("confirmed")
    ]
    confirmed_needed = max(limit - len(mempool_rows), 0)
    while len(confirmed_rows) < confirmed_needed and len(confirmed_rows) >= 25:
        last_seen = str(confirmed_rows[-1].get("txid") or "")
        if not re.fullmatch(r"[0-9a-fA-F]{64}", last_seen):
            break
        page_response = await client.get(
            f"{BLOCKSTREAM}/address/{address}/txs/chain/{last_seen}"
        )
        page_response.raise_for_status()
        page = page_response.json()
        if not isinstance(page, list) or not page:
            break
        confirmed_rows.extend(
            row for row in page if (row.get("status") or {}).get("confirmed")
        )
        if len(page) < 25:
            break
    return mempool_rows + confirmed_rows[:confirmed_needed]


def build_bitcoin_transactions(rows, address: str, price: Decimal | None, limit: int):
    results = []
    for row in rows:
        try:
            tx_hash = str(row["txid"])
            fee_sats = int(row.get("fee") or 0)
            status_data = row.get("status") or {}
            confirmed = bool(status_data.get("confirmed"))
            timestamp = int(status_data.get("block_time") or 0)
            if (
                not re.fullmatch(r"[0-9a-fA-F]{64}", tx_hash)
                or fee_sats < 0
                or (confirmed and timestamp <= 0)
            ):
                continue
            inputs = row.get("vin") or []
            input_addresses = [
                str((item.get("prevout") or {}).get("scriptpubkey_address") or "")
                for item in inputs if isinstance(item, dict)
            ]
            # Attribute the whole transaction fee only when every input belongs
            # to this exact address; mixed-address inputs remain unassigned.
            fee_paid_by_wallet = bool(input_addresses) and all(
                value == address for value in input_addresses
            )
            shared_input_fee = address in input_addresses and not fee_paid_by_wallet
            outputs = row.get("vout") or []
            first_input = next((value for value in input_addresses if value), "")
            first_output = next((
                str(item.get("scriptpubkey_address") or "")
                for item in outputs if isinstance(item, dict) and item.get("scriptpubkey_address")
            ), "")
            native_cost = Decimal(fee_sats) / SATOSHIS_PER_BTC
            results.append(WalletTransaction(
                chain="bitcoin", hash=tx_hash, timestamp=timestamp,
                from_address=first_input, to_address=first_output,
                method_id=None, gas_used=None, gas_price_base_unit=None,
                total_fee_base_unit=str(fee_sats),
                fee_type="bitcoin_fee_sat_shared_inputs" if shared_input_fee else "bitcoin_fee_sat",
                gas_cost_native=decimal_text(native_cost), native_symbol="BTC",
                native_price_usd=decimal_text(price) if price is not None else None,
                gas_cost_usd=decimal_text(native_cost * price) if price is not None else None,
                fee_paid_by_wallet=fee_paid_by_wallet,
                status="success" if confirmed else "pending",
            ))
        except (KeyError, TypeError, ValueError, InvalidOperation):
            continue
    results.sort(key=lambda tx: (tx.status == "pending", tx.timestamp), reverse=True)
    deduped, seen = [], set()
    for tx in results:
        key = tx.hash.lower()
        if key in seen:
            continue
        seen.add(key)
        deduped.append(tx)
    return deduped[:limit]


@router.get("/evm/{address}/transactions", response_model=WalletActivityBatchResponse)
@limiter.limit("12/minute")
async def list_multi_evm_transactions(
    request: Request,
    address: str,
    limit: int = Query(default=50, ge=1, le=100),
):
    if not re.fullmatch(r"0x[a-fA-F0-9]{40}", address):
        raise HTTPException(422, "无效的 EVM 地址")
    api_key = (os.getenv("ETHERSCAN_API_KEY") or "").strip()
    if not api_key:
        raise HTTPException(503, "EVM 交易服务尚未配置")
    timeout = httpx.Timeout(25.0, connect=8.0)
    try:
        async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": "AirdropHunter/1.0"}) as client:
            rows_by_network = {}
            checked, unavailable = [], []
            for chain in EVM_NETWORKS:
                try:
                    rows_by_network[chain] = await evm_transactions(client, address, limit, api_key, chain)
                    checked.append(chain)
                except (HTTPException, httpx.HTTPError):
                    unavailable.append(chain)
            if not checked:
                raise HTTPException(503, "当前无法读取任何 EVM 网络，请检查 Etherscan API Key 或稍后重试")
            price_ids = {
                EVM_NETWORKS[chain]["price_id"]
                for chain, rows in rows_by_network.items() if rows
            }
            prices = {}
            for price_id in price_ids:
                prices[price_id] = await fetch_price(client, price_id)
            transactions = []
            for chain, rows in rows_by_network.items():
                price = prices.get(EVM_NETWORKS[chain]["price_id"])
                transactions.extend(build_evm_transactions(rows, chain, address, price))
            transactions.sort(key=lambda tx: tx.timestamp, reverse=True)
            deduped, seen = [], set()
            for tx in transactions:
                key = (tx.chain, tx.hash.lower())
                if key in seen:
                    continue
                seen.add(key)
                deduped.append(tx)
                if len(deduped) >= limit:
                    break
            return WalletActivityBatchResponse(
                transactions=deduped,
                networks_checked=checked,
                networks_unavailable=unavailable,
            )
    except httpx.HTTPStatusError as exc:
        raise HTTPException(503, "EVM 链上数据服务暂时不可用") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(503, "EVM 链上数据服务连接失败") from exc


@router.get("/{chain}/{address}/transactions", response_model=list[WalletTransaction])
@limiter.limit("12/minute")
async def list_wallet_transactions(
    request: Request,
    chain: Literal["ethereum", "bsc", "polygon", "arbitrum", "optimism", "base", "avalanche", "tron", "bitcoin"],
    address: str,
    limit: int = Query(default=50, ge=1, le=100),
):
    if chain == "tron":
        if not re.fullmatch(r"T[1-9A-HJ-NP-Za-km-z]{33}", address):
            raise HTTPException(422, "无效的 TRON 地址")
    elif chain == "bitcoin":
        if identify_chain(address) != "Bitcoin":
            raise HTTPException(422, "无效的 Bitcoin 地址")
    elif not re.fullmatch(r"0x[a-fA-F0-9]{40}", address):
        raise HTTPException(422, "无效的 EVM 地址")

    api_key = (os.getenv("ETHERSCAN_API_KEY") or "").strip()
    if chain not in {"tron", "bitcoin"} and not api_key:
        raise HTTPException(503, "EVM 交易服务尚未配置")
    timeout = httpx.Timeout(25.0, connect=8.0)
    try:
        async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": "AirdropHunter/1.0"}) as client:
            if chain == "bitcoin":
                rows = await bitcoin_transactions(client, address, limit)
                price = await fetch_price(client, "btc-bitcoin")
                return build_bitcoin_transactions(rows, address, price, limit)
            if chain != "tron":
                rows = await evm_transactions(client, address, limit, api_key, chain)
                price = await fetch_price(client, EVM_NETWORKS[chain]["price_id"])
                return build_evm_transactions(rows, chain, address, price)

            rows = await tron_transactions(client, address, limit)
            price = await fetch_price(client, "trx-tron")
            results = []
            for row in rows:
                try:
                    tx_hash = str(row["txID"])
                    timestamp = int(row["block_timestamp"]) // 1000
                    if len(tx_hash) != 64 or not re.fullmatch(r"[0-9a-fA-F]{64}", tx_hash):
                        continue
                    tron_headers = {}
                    tron_key = (os.getenv("TRONGRID_API_KEY") or "").strip()
                    if tron_key:
                        tron_headers["TRON-PRO-API-KEY"] = tron_key
                    info_response = await client.post(
                        f"{TRONGRID}/wallet/gettransactioninfobyid",
                        json={"value": tx_hash},
                        headers=tron_headers,
                    )
                    info_response.raise_for_status()
                    info = info_response.json()
                    if not info or info.get("id", "").lower() != tx_hash.lower():
                        continue
                    fee_sun = int(info.get("fee") or 0)
                    if fee_sun < 0:
                        continue
                    receipt = info.get("receipt") or {}
                    energy_fee_sun = int(receipt.get("energy_fee") or 0)
                    net_fee_sun = int(receipt.get("net_fee") or 0)
                    energy_used = int(receipt.get("energy_usage_total") or 0)
                    bandwidth_used = int(receipt.get("net_usage") or 0)
                    native_cost = Decimal(fee_sun) / SUN_PER_TRX
                    raw_contracts = (row.get("raw_data") or {}).get("contract") or []
                    value = (((raw_contracts[0] if raw_contracts else {}).get("parameter") or {}).get("value") or {})
                    wallet_hex = tron_address_to_hex(address)
                    wallet_hex_lower = wallet_hex.lower() if wallet_hex else None

                    def contract_owner_matches(contract):
                        contract_value = ((contract.get("parameter") or {}).get("value") or {})
                        owner = str(contract_value.get("owner_address") or "")
                        if not owner or wallet_hex_lower is None:
                            return False
                        owner_hex = tron_address_to_hex(owner) if owner.startswith("T") else owner
                        return owner_hex is not None and owner_hex.lower() == wallet_hex_lower

                    contract_owners = [
                        contract for contract in raw_contracts
                        if isinstance(contract, dict)
                    ]
                    # TRON receipts expose aggregate fees, not a definitive payer split.
                    # Smart-contract calls can consume energy sponsored by the contract
                    # origin, so matching owner_address alone is insufficient evidence.
                    # Attribute fees only for basic transfer operations where the
                    # transaction owner is the fee payer and no contract energy sponsor
                    # can be involved. All other cases remain unassigned for manual review.
                    fee_paid_by_wallet = (
                        fee_sun > 0
                        and bool(contract_owners)
                        and all(
                            contract.get("type") in {"TransferContract", "TransferAssetContract"}
                            and contract_owner_matches(contract)
                            for contract in contract_owners
                        )
                    )
                    results.append(WalletTransaction(
                        chain="tron", hash=tx_hash, timestamp=timestamp,
                        from_address=str(value.get("owner_address") or address),
                        to_address=str(value.get("to_address") or ""),
                        method_id=None, gas_used=None, gas_price_base_unit=None,
                        total_fee_base_unit=str(fee_sun), fee_type="tron_total_fee_sun",
                        energy_used=str(energy_used), bandwidth_used=str(bandwidth_used),
                        energy_fee_base_unit=str(energy_fee_sun), bandwidth_fee_base_unit=str(net_fee_sun),
                        gas_cost_native=decimal_text(native_cost), native_symbol="TRX",
                        native_price_usd=decimal_text(price) if price is not None else None,
                        gas_cost_usd=decimal_text(native_cost * price) if price is not None else None,
                        fee_paid_by_wallet=fee_paid_by_wallet,
                        status="success" if receipt.get("result", "SUCCESS") == "SUCCESS" else "failed",
                    ))
                except (httpx.HTTPError, KeyError, TypeError, ValueError, InvalidOperation):
                    continue
            return results
    except httpx.HTTPStatusError as exc:
        raise HTTPException(503, "链上数据服务暂时不可用") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(503, "链上数据服务连接失败") from exc
