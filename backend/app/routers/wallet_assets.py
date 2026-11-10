import re
import time
from decimal import Decimal, InvalidOperation
from typing import Literal

import httpx
from fastapi import APIRouter, HTTPException, Query, Request
from pydantic import BaseModel
from app.routers.auth import limiter
from app.routers.wallet import identify_chain

router = APIRouter(prefix="/api/wallet-assets", tags=["wallet assets"])

EVM_RPC = {
    "ethereum": ("https://ethereum-rpc.publicnode.com", "ETH", "eth-ethereum"),
    "bsc": ("https://bsc-dataseed.binance.org", "BNB", "bnb-binance-coin"),
    "polygon": ("https://polygon-bor-rpc.publicnode.com", "POL", "pol-polygon-ecosystem-token"),
    "arbitrum": ("https://arbitrum-one-rpc.publicnode.com", "ETH", "eth-ethereum"),
    "optimism": ("https://optimism-rpc.publicnode.com", "ETH", "eth-ethereum"),
    "base": ("https://base-rpc.publicnode.com", "ETH", "eth-ethereum"),
    "avalanche": ("https://api.avax.network/ext/bc/C/rpc", "AVAX", "avax-avalanche"),
}
BLOCKSCOUT = {
    "ethereum": "https://eth.blockscout.com",
    "arbitrum": "https://arbitrum.blockscout.com",
    "optimism": "https://optimism.blockscout.com",
    "base": "https://base.blockscout.com",
    "polygon": "https://polygon.blockscout.com",
}
SOLANA_RPC = "https://api.mainnet-beta.solana.com"
BITCOIN_API = "https://blockstream.info/api"
COINPAPRIKA = "https://api.coinpaprika.com/v1/tickers/"
SOL_TOKEN_PROGRAMS = (
    "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
    "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",
)
SOL_KNOWN_TOKENS = {
    "EPjFWdd5AufqSSqeM2qXzybapC8G4wEGGkZwyTDt1v": ("USDC", "USD Coin", "usdc-usd-coin"),
    "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB": ("USDT", "Tether", "usdt-tether"),
}
EVM_KNOWN_TOKENS = {
    "ethereum": {
        "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48": "usdc-usd-coin",
        "0xdac17f958d2ee523a2206206994597c13d831ec7": "usdt-tether",
    },
    "base": {"0x833589fcd6edb6e08f4c7c32d4f71b54bda02913": "usdc-usd-coin"},
    "arbitrum": {
        "0xaf88d065e77c8cc2239327c5edb3a432268e5831": "usdc-usd-coin",
        "0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9": "usdt-tether",
    },
    "optimism": {
        "0x0b2c639c533813f4aa9d7837caf62653d097ff85": "usdc-usd-coin",
        "0x94b008aa00579c1307b0ef2c499ad98a8ce58e58": "usdt-tether",
    },
    "polygon": {
        "0x3c499c542cef5e3811e1192ce70d8cc03d5c3359": "usdc-usd-coin",
        "0xc2132d05d31c914a87c6611c10748aeb04b58e8f": "usdt-tether",
    },
}

class WalletAsset(BaseModel):
    asset_id: str
    contract_address: str | None = None
    symbol: str
    name: str
    decimals: int
    raw_quantity: str
    formatted_quantity: str
    price_usd: str | None = None
    usd_value: str | None = None
    is_verified: bool
    source: str

class WalletAssetsResponse(BaseModel):
    chain: str
    network: str
    provider: str
    fetched_at: int
    coverage_note: str | None = None
    assets: list[WalletAsset]

def _decimal(value) -> Decimal:
    try:
        result = Decimal(str(value))
        return result if result.is_finite() else Decimal(0)
    except (InvalidOperation, TypeError, ValueError):
        return Decimal(0)

def _price_value(quantity: Decimal, price: Decimal | None) -> tuple[str | None, str | None]:
    if price is None or price <= 0:
        return None, None
    return format(price, "f"), format(quantity * price, "f")

def normalize_evm_assets(chain: str, native_wei: str, token_rows: list,
                         native_price: Decimal | None, token_prices: dict[str, Decimal]) -> list[WalletAsset]:
    _, native_symbol, _ = EVM_RPC[chain]
    native_raw = str(int(native_wei, 16))
    native_quantity = Decimal(native_raw) / Decimal(10**18)
    price_text, usd_value = _price_value(native_quantity, native_price)
    assets = [WalletAsset(
        asset_id=f"{chain}:native", symbol=native_symbol, name=native_symbol, decimals=18,
        raw_quantity=native_raw, formatted_quantity=format(native_quantity, "f"),
        price_usd=price_text, usd_value=usd_value, is_verified=True, source="public-rpc",
    )]
    for row in token_rows:
        if not isinstance(row, dict) or not isinstance(row.get("token"), dict):
            continue
        token = row["token"]
        contract = str(token.get("address_hash") or "").lower()
        symbol, name = str(token.get("symbol") or "").strip(), str(token.get("name") or "").strip()
        try:
            decimals, raw = int(token.get("decimals")), str(int(row.get("value") or "0"))
        except (TypeError, ValueError):
            continue
        if not re.fullmatch(r"0x[0-9a-f]{40}", contract) or not symbol or not name or not 0 <= decimals <= 36 or int(raw) <= 0:
            continue
        verified = token.get("is_verified") is True
        quantity = Decimal(raw) / (Decimal(10) ** decimals)
        price_text, usd_value = _price_value(quantity, token_prices.get(contract) if verified else None)
        assets.append(WalletAsset(
            asset_id=f"{chain}:{contract}", contract_address=contract,
            symbol=symbol[:32], name=name[:120], decimals=decimals,
            raw_quantity=raw, formatted_quantity=format(quantity, "f"),
            price_usd=price_text, usd_value=usd_value, is_verified=verified, source="blockscout-v2",
        ))
    return assets

def normalize_bitcoin_asset(payload: dict, price: Decimal | None) -> list[WalletAsset]:
    chain_stats, mempool_stats = payload.get("chain_stats") or {}, payload.get("mempool_stats") or {}
    sats = (
        int(chain_stats.get("funded_txo_sum") or 0) - int(chain_stats.get("spent_txo_sum") or 0)
        + int(mempool_stats.get("funded_txo_sum") or 0) - int(mempool_stats.get("spent_txo_sum") or 0)
    )
    if sats < 0:
        raise HTTPException(502, "Bitcoin 余额服务返回无效数据")
    quantity = Decimal(sats) / Decimal(100_000_000)
    price_text, usd_value = _price_value(quantity, price)
    return [WalletAsset(
        asset_id="bitcoin:native", symbol="BTC", name="Bitcoin", decimals=8,
        raw_quantity=str(sats), formatted_quantity=format(quantity, "f"),
        price_usd=price_text, usd_value=usd_value, is_verified=True, source="blockstream-esplora",
    )]

def normalize_solana_assets(balance_lamports: str, token_payloads: list,
                            price: Decimal | None, token_prices: dict[str, Decimal]) -> list[WalletAsset]:
    lamports = int(balance_lamports)
    if lamports < 0:
        raise HTTPException(502, "Solana 余额服务返回无效数据")
    quantity = Decimal(lamports) / Decimal(1_000_000_000)
    price_text, usd_value = _price_value(quantity, price)
    assets = [WalletAsset(
        asset_id="solana:native", symbol="SOL", name="Solana", decimals=9,
        raw_quantity=str(lamports), formatted_quantity=format(quantity, "f"),
        price_usd=price_text, usd_value=usd_value, is_verified=True, source="solana-rpc",
    )]
    for payload in token_payloads:
        rows = (payload.get("result") or {}).get("value") if isinstance(payload, dict) else None
        if not isinstance(rows, list):
            continue
        for row in rows:
            info = (((row.get("account") or {}).get("data") or {}).get("parsed") or {}).get("info") or {}
            amount = info.get("tokenAmount") or {}
            mint = str(info.get("mint") or "")
            try:
                raw, decimals = str(int(amount.get("amount") or "0")), int(amount.get("decimals"))
            except (TypeError, ValueError):
                continue
            if not mint or int(raw) <= 0 or not 0 <= decimals <= 18:
                continue
            known = SOL_KNOWN_TOKENS.get(mint)
            symbol, name, _ = known if known else (mint[:6], "未识别代币", "")
            quantity = Decimal(raw) / (Decimal(10) ** decimals)
            price_text, usd_value = _price_value(quantity, token_prices.get(mint) if known else None)
            assets.append(WalletAsset(
                asset_id=f"solana:{mint}", contract_address=mint, symbol=symbol, name=name,
                decimals=decimals, raw_quantity=raw, formatted_quantity=format(quantity, "f"),
                price_usd=price_text, usd_value=usd_value, is_verified=known is not None,
                source="solana-rpc-spl",
            ))
    return assets

async def fetch_price(client: httpx.AsyncClient, price_id: str) -> Decimal | None:
    try:
        response = await client.get(COINPAPRIKA + price_id)
        response.raise_for_status()
        price = _decimal(response.json()["quotes"]["USD"]["price"])
        return price if price > 0 else None
    except (httpx.HTTPError, KeyError, TypeError, ValueError):
        return None

@router.get("/{chain}/{address}", response_model=WalletAssetsResponse)
@limiter.limit("12/minute")
async def get_wallet_assets(
    request: Request,
    chain: Literal["ethereum", "bsc", "polygon", "arbitrum", "optimism", "base", "avalanche", "bitcoin", "solana"],
    address: str,
):
    expected = "Bitcoin" if chain == "bitcoin" else "SOL" if chain == "solana" else "EVM"
    if identify_chain(address) != expected:
        raise HTTPException(422, "钱包地址格式与所选网络不匹配")
    fetched_at = int(time.time() * 1000)
    timeout = httpx.Timeout(20.0, connect=8.0)
    try:
        async with httpx.AsyncClient(timeout=timeout, headers={"User-Agent": "AirdropHunter/1.0"}) as client:
            if chain == "bitcoin":
                response = await client.get(f"{BITCOIN_API}/address/{address}")
                response.raise_for_status()
                assets = normalize_bitcoin_asset(response.json(), await fetch_price(client, "btc-bitcoin"))
                return WalletAssetsResponse(chain=chain, network="Bitcoin", provider="Blockstream Esplora + CoinPaprika",
                                             fetched_at=fetched_at, assets=assets)

            if chain == "solana":
                native = await client.post(SOLANA_RPC, json={
                    "jsonrpc": "2.0", "id": 1, "method": "getBalance",
                    "params": [address, {"commitment": "confirmed"}],
                })
                native.raise_for_status()
                native_payload = native.json()
                if not isinstance(native_payload, dict) or native_payload.get("error") or not isinstance(native_payload.get("result"), dict):
                    raise HTTPException(503, "Solana 原生余额暂时不可用")
                lamports = str(native_payload["result"].get("value") or 0)
                token_payloads, token_unavailable = [], False
                for index, program in enumerate(SOL_TOKEN_PROGRAMS):
                    try:
                        response = await client.post(SOLANA_RPC, json={
                            "jsonrpc": "2.0", "id": index + 2, "method": "getTokenAccountsByOwner",
                            "params": [address, {"programId": program}, {"encoding": "jsonParsed", "commitment": "confirmed"}],
                        })
                        response.raise_for_status()
                        payload = response.json()
                        if isinstance(payload, dict) and not payload.get("error"):
                            token_payloads.append(payload)
                        else:
                            token_unavailable = True
                    except httpx.HTTPError:
                        token_unavailable = True
                prices = {}
                for mint, (_, _, price_id) in SOL_KNOWN_TOKENS.items():
                    price = await fetch_price(client, price_id)
                    if price is not None:
                        prices[mint] = price
                assets = normalize_solana_assets(lamports, token_payloads, await fetch_price(client, "sol-solana"), prices)
                note = "部分 SPL 代币列表服务暂时不可用" if token_unavailable else None
                return WalletAssetsResponse(chain=chain, network="Solana", provider="Solana RPC + CoinPaprika",
                                             fetched_at=fetched_at, coverage_note=note, assets=assets)

            rpc_url, _, native_price_id = EVM_RPC[chain]
            rpc_response = await client.post(rpc_url, json={
                "jsonrpc": "2.0", "id": 1, "method": "eth_getBalance", "params": [address, "latest"],
            })
            rpc_response.raise_for_status()
            rpc_payload = rpc_response.json()
            native_wei = rpc_payload.get("result") if isinstance(rpc_payload, dict) and not rpc_payload.get("error") else None
            if not isinstance(native_wei, str) or not re.fullmatch(r"0x[0-9a-fA-F]+", native_wei):
                raise HTTPException(503, "EVM 原生币余额暂时不可用")

            token_rows, token_unavailable = [], False
            blockscout = BLOCKSCOUT.get(chain)
            if blockscout:
                try:
                    response = await client.get(f"{blockscout}/api/v2/addresses/{address}/token-balances")
                    response.raise_for_status()
                    payload = response.json()
                    if isinstance(payload, list):
                        token_rows = payload
                    else:
                        token_unavailable = True
                except httpx.HTTPError:
                    token_unavailable = True
            else:
                token_unavailable = True

            native_price = await fetch_price(client, native_price_id)
            price_ids = set(EVM_KNOWN_TOKENS.get(chain, {}).values())
            prices = {price_id: await fetch_price(client, price_id) for price_id in price_ids}
            token_prices = {
                contract: prices[price_id]
                for contract, price_id in EVM_KNOWN_TOKENS.get(chain, {}).items()
                if prices.get(price_id) is not None
            }
            assets = normalize_evm_assets(chain, native_wei, token_rows, native_price, token_prices)
            note = "当前免费服务仅提供该网络原生币余额，代币列表暂不可用" if token_unavailable else None
            return WalletAssetsResponse(
                chain=chain, network=chain.title(), provider="Public EVM RPC + Blockscout + CoinPaprika",
                fetched_at=fetched_at, coverage_note=note, assets=assets,
            )
    except HTTPException:
        raise
    except (httpx.HTTPError, ValueError, TypeError, InvalidOperation) as exc:
        raise HTTPException(503, "链上资产服务暂时不可用，请稍后重试") from exc
