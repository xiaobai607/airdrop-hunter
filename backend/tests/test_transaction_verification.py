import asyncio
import hashlib
from decimal import Decimal

import httpx
import pytest

from app.services.transaction_verification import (
    VerificationPending,
    VerificationRejected,
    _hex_int,
    normalize_network,
    transaction_fingerprint,
    verify_evm_contribution,
)


def test_network_aliases_are_normalized():
    assert normalize_network("ERC20") == "ethereum"
    assert normalize_network("BSC") == "bsc"
    assert normalize_network("TRC20") == "tron"
    # "EVM" is an ecosystem label, not a chain ID; never guess Ethereum for it.
    assert normalize_network("EVM") == "evm"


def test_fingerprint_is_server_keyed_and_normalized():
    secret = "s" * 32
    first = transaction_fingerprint("Ethereum", "0x" + "A" * 64, secret)
    second = transaction_fingerprint("ethereum", "0x" + "a" * 64, secret)
    other = transaction_fingerprint("bsc", "0x" + "a" * 64, secret)
    assert first == second
    assert first != other
    assert first == hmac_sha256(secret, "ethereum:" + "0x" + "a" * 64)


def hmac_sha256(secret: str, message: str) -> str:
    import hmac
    return hmac.new(secret.encode(), message.encode(), hashlib.sha256).hexdigest()


def test_fingerprint_refuses_missing_or_weak_server_secret():
    with pytest.raises(VerificationPending):
        transaction_fingerprint("ethereum", "0x" + "a" * 64, "short")


def test_hex_rpc_values_are_validated():
    assert _hex_int("0x10", "block") == 16
    with pytest.raises(VerificationPending):
        _hex_int("16", "block")
    with pytest.raises(VerificationPending):
        _hex_int("0xnope", "block")


def test_unsupported_network_is_rejected_without_calling_provider():
    with pytest.raises(VerificationRejected, match="暂不支持"):
        asyncio.run(verify_evm_contribution(
            "tron", "T" + "1" * 33, "a" * 64, Decimal("1.00")
        ))


def test_invalid_evm_hash_is_rejected_without_provider_configuration(monkeypatch):
    monkeypatch.delenv("ETHERSCAN_API_KEY", raising=False)
    with pytest.raises(VerificationRejected, match="Tx Hash"):
        asyncio.run(verify_evm_contribution(
            "ethereum",
            "0x1234567890abcdef1234567890abcdef12345678",
            "not-a-hash",
            Decimal("1.00"),
        ))


def test_provider_rate_limit_is_retryable():
    async def run():
        transport = httpx.MockTransport(lambda request: httpx.Response(429, json={"message": "rate limited"}))
        async with httpx.AsyncClient(transport=transport) as client:
            from app.services.transaction_verification import _etherscan_rpc
            with pytest.raises(VerificationPending):
                await _etherscan_rpc(client, 1, "test-key", "eth_blockNumber")
    asyncio.run(run())



def _chainlink_result(price_usd: int, updated_at: int) -> str:
    values = [1, price_usd * 100000000, updated_at - 1, updated_at, 1]
    return "0x" + "".join(f"{value:064x}" for value in values)


class _FakeEtherscanClient:
    def __init__(self, responses):
        self.responses = responses

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return None

    async def get(self, url, params):
        action = params["action"]
        result = self.responses[action]
        request = httpx.Request("GET", url)
        return httpx.Response(200, json={"jsonrpc": "2.0", "id": 1, "result": result}, request=request)


def _verification_responses(include_l1_fee=False):
    wallet = "0x1234567890abcdef1234567890abcdef12345678"
    block_timestamp = 1794258505
    receipt = {
        "from": wallet,
        "blockNumber": "0x10",
        "status": "0x1",
        "gasUsed": "0x5208",
        "effectiveGasPrice": "0x3b9aca00",
    }
    if include_l1_fee:
        receipt["l1Fee"] = "0x0"
    return {
        "eth_getTransactionByHash": {"from": wallet, "blockNumber": "0x10"},
        "eth_getTransactionReceipt": receipt,
        "eth_blockNumber": "0x30",
        "eth_getBlockByNumber": {"timestamp": hex(block_timestamp)},
        "eth_call": _chainlink_result(3000, block_timestamp - 5),
    }


def test_evm_receipt_uses_historical_chainlink_price_and_backend_fee(monkeypatch):
    import app.services.transaction_verification as verification

    monkeypatch.setenv("ETHERSCAN_API_KEY", "server-only-key")
    monkeypatch.setenv("COMMUNITY_TX_FINGERPRINT_SECRET", "k" * 40)
    monkeypatch.setattr(
        verification.httpx,
        "AsyncClient",
        lambda **kwargs: _FakeEtherscanClient(_verification_responses()),
    )
    result = asyncio.run(verification.verify_evm_contribution(
        "ethereum",
        "0x1234567890abcdef1234567890abcdef12345678",
        "0x" + "e" * 64,
        Decimal("0.063"),
    ))
    assert result.tier == "amount_verified"
    assert result.gas_cost_usd == Decimal("0.063000")
    assert result.metadata["native_symbol"] == "ETH"
    assert result.metadata["price_source"] == "chainlink:ethereum"
    assert "tx_hash" not in result.metadata
    assert "wallet_address" not in result.metadata


def test_evm_fee_mismatch_is_rejected_without_changing_private_data(monkeypatch):
    import app.services.transaction_verification as verification

    monkeypatch.setenv("ETHERSCAN_API_KEY", "server-only-key")
    monkeypatch.setenv("COMMUNITY_TX_FINGERPRINT_SECRET", "k" * 40)
    monkeypatch.setattr(
        verification.httpx,
        "AsyncClient",
        lambda **kwargs: _FakeEtherscanClient(_verification_responses()),
    )
    with pytest.raises(VerificationRejected, match="金额与链上不符"):
        asyncio.run(verification.verify_evm_contribution(
            "ethereum",
            "0x1234567890abcdef1234567890abcdef12345678",
            "0x" + "f" * 64,
            Decimal("1.00"),
        ))


def test_l2_without_complete_l1_fee_stays_transaction_linked(monkeypatch):
    import app.services.transaction_verification as verification

    monkeypatch.setenv("ETHERSCAN_API_KEY", "server-only-key")
    monkeypatch.setenv("COMMUNITY_TX_FINGERPRINT_SECRET", "k" * 40)
    monkeypatch.setattr(
        verification.httpx,
        "AsyncClient",
        lambda **kwargs: _FakeEtherscanClient(_verification_responses(include_l1_fee=False)),
    )
    result = asyncio.run(verification.verify_evm_contribution(
        "base",
        "0x1234567890abcdef1234567890abcdef12345678",
        "0x" + "1" * 64,
        Decimal("0.20"),
    ))
    assert result.tier == "transaction_linked"
    assert result.gas_cost_usd == Decimal("0.20")
    assert result.metadata["fee_components_complete"] is False
