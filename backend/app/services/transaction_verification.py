"""Deterministic EVM transaction and fee verification for anonymous contributions.

Raw wallet addresses and transaction hashes are used only for provider requests and
HMAC calculation. They are never returned or persisted by this service.
"""
from __future__ import annotations

import hashlib
import hmac
import os
import re
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from typing import Any

import httpx

ETHERSCAN_V2 = "https://api.etherscan.io/v2/api"
WEI = Decimal("1000000000000000000")
CHAINLINK_PRICE_SCALE = Decimal("100000000")
TX_HASH = re.compile(r"^0x[a-fA-F0-9]{64}$")
EVM_ADDRESS = re.compile(r"^0x[a-fA-F0-9]{40}$")

# Only networks with a known Chainlink native/USD feed are eligible for exact
# amount verification. L2s remain Tier 2 unless the provider exposes the full L1 fee.
EVM_NETWORKS: dict[str, dict[str, Any]] = {
    "ethereum": {"chain_id": 1, "symbol": "ETH", "feed": "0x5f4eC3Df9cbd43714FE2740f5E3616155c5b8419", "confirmations": 12},
    "bsc": {"chain_id": 56, "symbol": "BNB", "feed": "0x0567F2323251f0Aab15c8dFb1967E4e8A7D42aeE", "confirmations": 15},
    "polygon": {"chain_id": 137, "symbol": "POL", "feed": "0xAB594600376Ec9fD91F8e885dADF0CE036862dE0", "confirmations": 128},
    "arbitrum": {"chain_id": 42161, "symbol": "ETH", "feed": "0x639Fe6ab55C921f74e7fac1ee960C0B6293ba612", "confirmations": 20, "l2": True},
    "optimism": {"chain_id": 10, "symbol": "ETH", "feed": "0x13e3Ee699D1909E989722E753853AE30b17e08c5", "confirmations": 20, "l2": True},
    "base": {"chain_id": 8453, "symbol": "ETH", "feed": "0x71041dddad3595F9CEd3DcCFBe3D1F4b0a16Bb70", "confirmations": 20, "l2": True},
    "avalanche": {"chain_id": 43114, "symbol": "AVAX", "feed": "0x0A77230d17318075983913bC2145DB16C7366156", "confirmations": 12},
}
NETWORK_ALIASES = {
    "eth": "ethereum", "erc20": "ethereum",
    "trc20": "tron", "btc": "bitcoin", "sol": "solana",
}


class VerificationPending(Exception):
    """A retryable provider/configuration/incomplete-data condition."""


class VerificationRejected(Exception):
    """A definitive public-contribution rejection; private ledger data is unaffected."""


@dataclass(frozen=True)
class VerificationResult:
    tier: str
    gas_cost_usd: Decimal
    tx_fingerprint: str
    metadata: dict[str, Any]


def normalize_network(network: str) -> str:
    value = (network or "").strip().lower()
    return NETWORK_ALIASES.get(value, value)


def transaction_fingerprint(network: str, tx_hash: str, secret: str | None = None) -> str:
    key = (secret if secret is not None else os.getenv("COMMUNITY_TX_FINGERPRINT_SECRET", "")).encode()
    if len(key) < 32:
        raise VerificationPending("链上核验服务尚未完成配置")
    normalized = normalize_network(network)
    message = f"{normalized}:{tx_hash.strip().lower()}".encode()
    return hmac.new(key, message, hashlib.sha256).hexdigest()


def _hex_int(value: Any, field: str) -> int:
    if not isinstance(value, str) or not value.startswith("0x"):
        raise VerificationPending(f"链上回执缺少 {field}")
    try:
        result = int(value, 16)
    except ValueError as exc:
        raise VerificationPending(f"链上回执 {field} 格式无效") from exc
    if result < 0:
        raise VerificationPending(f"链上回执 {field} 格式无效")
    return result


async def _etherscan_rpc(
    client: httpx.AsyncClient,
    chain_id: int,
    api_key: str,
    action: str,
    **params: str,
) -> Any:
    query = {
        "chainid": str(chain_id),
        "module": "proxy",
        "action": action,
        "apikey": api_key,
        **params,
    }
    try:
        response = await client.get(ETHERSCAN_V2, params=query)
        if response.status_code == 429 or response.status_code >= 500:
            raise VerificationPending("链上服务繁忙，等待自动重试")
        if response.status_code >= 400:
            raise VerificationPending("链上服务暂时不可用，等待自动重试")
        payload = response.json()
    except VerificationPending:
        raise
    except (httpx.HTTPError, ValueError):
        raise VerificationPending("链上服务暂时不可用，等待自动重试")
    if not isinstance(payload, dict):
        raise VerificationPending("链上服务返回数据不完整")
    if payload.get("error"):
        raise VerificationPending("链上服务暂时无法完成核验")
    if str(payload.get("status", "")) == "0":
        raise VerificationPending("链上服务暂时不可用，等待自动重试")
    if "result" not in payload:
        raise VerificationPending("链上服务返回数据不完整")
    result = payload["result"]
    if isinstance(result, str) and not result.startswith("0x"):
        raise VerificationPending("链上服务暂时不可用，等待自动重试")
    return result


async def verify_evm_contribution(
    network: str,
    wallet_address: str,
    tx_hash: str,
    expected_gas_usd: Decimal,
) -> VerificationResult:
    normalized = normalize_network(network)
    config = EVM_NETWORKS.get(normalized)
    if config is None:
        raise VerificationRejected("当前网络暂不支持链上核验")
    if not EVM_ADDRESS.fullmatch((wallet_address or "").strip()):
        raise VerificationRejected("钱包地址格式无效")
    if not TX_HASH.fullmatch((tx_hash or "").strip()):
        raise VerificationRejected("Tx Hash 格式无效")
    try:
        expected = Decimal(str(expected_gas_usd))
    except (InvalidOperation, TypeError, ValueError):
        raise VerificationRejected("Gas 金额无效")
    if not expected.is_finite() or expected < 0:
        raise VerificationRejected("Gas 金额无效")

    api_key = (os.getenv("ETHERSCAN_API_KEY") or "").strip()
    if not api_key:
        raise VerificationPending("链上核验服务尚未完成配置")

    fingerprint = transaction_fingerprint(normalized, tx_hash)
    timeout = httpx.Timeout(8.0, connect=4.0)
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=False) as client:
        tx, receipt = await _fetch_transaction(client, config, api_key, tx_hash)
        if not isinstance(tx, dict):
            raise VerificationRejected("未找到该笔链上交易")
        if not isinstance(receipt, dict):
            raise VerificationPending("交易回执尚不可用，等待自动重试")

        sender = str(tx.get("from") or "").lower()
        receipt_sender = str(receipt.get("from") or sender).lower()
        if sender != wallet_address.lower() or receipt_sender != wallet_address.lower():
            raise VerificationRejected("交易付款钱包与所选钱包不匹配")

        tx_block = _hex_int(tx.get("blockNumber"), "交易区块")
        receipt_block = _hex_int(receipt.get("blockNumber"), "回执区块")
        if tx_block != receipt_block:
            raise VerificationPending("交易区块信息尚未一致，等待自动重试")
        status = str(receipt.get("status") or "").lower()
        if status == "0x0":
            raise VerificationRejected("链上交易执行失败")
        if status != "0x1":
            raise VerificationPending("交易执行状态尚未确认")

        latest_hex = await _etherscan_rpc(client, config["chain_id"], api_key, "eth_blockNumber")
        latest_block = _hex_int(latest_hex, "最新区块")
        confirmations = latest_block - receipt_block + 1
        if confirmations < int(config["confirmations"]):
            raise VerificationRejected("交易确认数不足")

        block = await _etherscan_rpc(
            client, config["chain_id"], api_key, "eth_getBlockByNumber",
            tag=hex(receipt_block), boolean="false",
        )
        if not isinstance(block, dict):
            raise VerificationPending("交易区块时间暂不可用")
        block_timestamp = _hex_int(block.get("timestamp"), "区块时间")

        gas_used = _hex_int(receipt.get("gasUsed"), "Gas 用量")
        effective_gas_price = _hex_int(receipt.get("effectiveGasPrice"), "实际 Gas 单价")
        if gas_used <= 0 or effective_gas_price <= 0:
            raise VerificationPending("交易费用字段不完整")

        l1_fee_raw = receipt.get("l1Fee")
        l1_fee = _hex_int(l1_fee_raw, "L1 数据费用") if l1_fee_raw is not None else None
        fee_components_complete = not bool(config.get("l2")) or l1_fee is not None
        base_fee_wei = gas_used * effective_gas_price
        total_fee_wei = base_fee_wei + (l1_fee or 0)

        common_metadata = {
            "network": normalized,
            "chain_id": int(config["chain_id"]),
            "block_number": receipt_block,
            "block_timestamp": block_timestamp,
            "confirmations": confirmations,
            "native_symbol": config["symbol"],
            "gas_used": str(gas_used),
            "effective_gas_price_wei": str(effective_gas_price),
            "fee_components_complete": fee_components_complete,
            "provider": "etherscan_v2",
        }
        # Optimistic rollups may charge an additional L1 data fee. If the explorer
        # does not expose it, only transaction linkage is verified (Tier 2).
        if not fee_components_complete:
            return VerificationResult(
                tier="transaction_linked",
                gas_cost_usd=expected,
                tx_fingerprint=fingerprint,
                metadata={**common_metadata, "verification_note": "l1_fee_not_exposed"},
            )

        price_result = await _etherscan_rpc(
            client, config["chain_id"], api_key, "eth_call",
            to=str(config["feed"]), data="0xfeaf968c", tag=hex(receipt_block),
        )
        if not isinstance(price_result, str) or not re.fullmatch(r"0x[0-9a-fA-F]{320}", price_result):
            raise VerificationPending("交易时点的链上历史价格暂不可用")
        words = [price_result[2 + index * 64:2 + (index + 1) * 64] for index in range(5)]
        answer = int(words[1], 16)
        updated_at = int(words[3], 16)
        if answer <= 0 or updated_at <= 0 or updated_at > block_timestamp or block_timestamp - updated_at > 86400:
            raise VerificationPending("交易时点的历史价格不完整，等待自动重试")
        native_price_usd = Decimal(answer) / CHAINLINK_PRICE_SCALE
        native_fee = Decimal(total_fee_wei) / WEI
        calculated_usd = native_fee * native_price_usd
        if expected == 0:
            within_tolerance = calculated_usd == 0
        else:
            within_tolerance = abs(calculated_usd - expected) / expected <= Decimal("0.20")
        if not within_tolerance:
            raise VerificationRejected("金额与链上不符")

        return VerificationResult(
            tier="amount_verified",
            gas_cost_usd=calculated_usd,
            tx_fingerprint=fingerprint,
            metadata={
                **common_metadata,
                "total_fee_wei": str(total_fee_wei),
                "native_fee": format(native_fee, "f"),
                "native_price_usd": format(native_price_usd, "f"),
                "price_source": f"chainlink:{normalized}",
                "price_updated_at": updated_at,
                "fee_components_complete": True,
            },
        )


async def _fetch_transaction(
    client: httpx.AsyncClient,
    config: dict[str, Any],
    api_key: str,
    tx_hash: str,
) -> tuple[Any, Any]:
    tx = await _etherscan_rpc(
        client, config["chain_id"], api_key, "eth_getTransactionByHash", txhash=tx_hash
    )
    if tx is None:
        return None, None
    receipt = await _etherscan_rpc(
        client, config["chain_id"], api_key, "eth_getTransactionReceipt", txhash=tx_hash
    )
    return tx, receipt


async def verify_community_transaction(
    network: str,
    wallet_address: str,
    tx_hash: str,
    expected_gas_usd: Decimal,
) -> VerificationResult:
    """Public entry point; deliberately EVM-only until other chain adapters exist."""
    return await verify_evm_contribution(network, wallet_address, tx_hash, expected_gas_usd)
