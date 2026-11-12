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
        if action == "eth_call" and str(params.get("to", "")).lower() == "0x420000000000000000000000000000000000000f":
            result = self.responses.get("eth_call_operator", "0x" + "0" * 64)
        else:
            result = self.responses[action]
        request = httpx.Request("GET", url)
        return httpx.Response(200, json={"jsonrpc": "2.0", "id": 1, "result": result}, request=request)


def _verification_responses(include_l1_fee=False, status="0x1", latest_block="0x30", tx_hash=None,
                            operator_fee_wei=0, gas_used_for_l1=None, l1_fee_wei=0):
    wallet = "0x1234567890abcdef1234567890abcdef12345678"
    block_timestamp = 1794258505
    receipt = {
        "from": wallet,
        "blockNumber": "0x10",
        "status": status,
        "gasUsed": "0x5208",
        "effectiveGasPrice": "0x3b9aca00",
    }
    if include_l1_fee:
        receipt["l1Fee"] = hex(l1_fee_wei)
    if gas_used_for_l1 is not None:
        receipt["gasUsedForL1"] = hex(gas_used_for_l1)
    return {
        "eth_getTransactionByHash": {
            "from": wallet, "blockNumber": "0x10",
            **({"hash": tx_hash} if tx_hash is not None else {}),
        },
        "eth_getTransactionReceipt": receipt,
        "eth_blockNumber": latest_block,
        "eth_getBlockByNumber": {"timestamp": hex(block_timestamp)},
        "eth_call": _chainlink_result(3000, block_timestamp - 5),
        "eth_call_operator": "0x" + f"{operator_fee_wei:064x}",
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
    assert result.metadata["execution_status"] == "success"
    assert "tx_hash" not in result.metadata
    assert "wallet_address" not in result.metadata


def test_arbitrum_gas_used_total_is_not_added_to_l1_component_twice(monkeypatch):
    import app.services.transaction_verification as verification

    monkeypatch.setenv("ETHERSCAN_API_KEY", "server-only-key")
    monkeypatch.setenv("COMMUNITY_TX_FINGERPRINT_SECRET", "k" * 40)
    monkeypatch.setattr(
        verification.httpx,
        "AsyncClient",
        lambda **kwargs: _FakeEtherscanClient(
            _verification_responses(gas_used_for_l1=1000)
        ),
    )
    result = asyncio.run(verification.verify_evm_contribution(
        "arbitrum",
        "0x1234567890abcdef1234567890abcdef12345678",
        "0x" + "a" * 64,
        Decimal("0.063"),
    ))
    assert result.tier == "amount_verified"
    assert result.gas_cost_usd == Decimal("0.063000")
    assert result.metadata["total_fee_wei"] == str(21000 * 10**9)
    assert result.metadata["l1_fee_wei"] == str(1000 * 10**9)
    assert result.metadata["l2_fee_wei"] == str(20000 * 10**9)


def test_op_stack_adds_l1_and_operator_fees_to_execution_fee(monkeypatch):
    import app.services.transaction_verification as verification

    monkeypatch.setenv("ETHERSCAN_API_KEY", "server-only-key")
    monkeypatch.setenv("COMMUNITY_TX_FINGERPRINT_SECRET", "k" * 40)
    monkeypatch.setattr(
        verification.httpx,
        "AsyncClient",
        lambda **kwargs: _FakeEtherscanClient(
            _verification_responses(
                include_l1_fee=True,
                l1_fee_wei=1_000_000_000_000,
                operator_fee_wei=500_000_000_000,
            )
        ),
    )
    result = asyncio.run(verification.verify_evm_contribution(
        "base",
        "0x1234567890abcdef1234567890abcdef12345678",
        "0x" + "b" * 64,
        Decimal("0.0675"),
    ))
    assert result.tier == "amount_verified"
    assert result.gas_cost_usd == Decimal("0.067500000000")
    assert result.metadata["total_fee_wei"] == str(22_500_000_000_000)
    assert result.metadata["l1_fee_wei"] == str(1_000_000_000_000)
    assert result.metadata["operator_fee_wei"] == str(500_000_000_000)


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



def test_failed_evm_transaction_still_verifies_the_gas_that_was_paid(monkeypatch):
    import app.services.transaction_verification as verification

    monkeypatch.setenv("ETHERSCAN_API_KEY", "server-only-key")
    monkeypatch.setenv("COMMUNITY_TX_FINGERPRINT_SECRET", "k" * 40)
    monkeypatch.setattr(
        verification.httpx,
        "AsyncClient",
        lambda **kwargs: _FakeEtherscanClient(_verification_responses(status="0x0")),
    )
    result = asyncio.run(verification.verify_evm_contribution(
        "ethereum",
        "0x1234567890abcdef1234567890abcdef12345678",
        "0x" + "2" * 64,
        Decimal("0.063"),
    ))
    assert result.tier == "amount_verified"
    assert result.metadata["execution_status"] == "failed"
    assert result.gas_cost_usd == Decimal("0.063000")


def test_insufficient_confirmations_are_pending_and_retryable(monkeypatch):
    import app.services.transaction_verification as verification

    monkeypatch.setenv("ETHERSCAN_API_KEY", "server-only-key")
    monkeypatch.setenv("COMMUNITY_TX_FINGERPRINT_SECRET", "k" * 40)
    monkeypatch.setattr(
        verification.httpx,
        "AsyncClient",
        lambda **kwargs: _FakeEtherscanClient(_verification_responses(latest_block="0x10")),
    )
    with pytest.raises(VerificationPending, match="确认数不足"):
        asyncio.run(verification.verify_evm_contribution(
            "ethereum",
            "0x1234567890abcdef1234567890abcdef12345678",
            "0x" + "3" * 64,
            Decimal("0.063"),
        ))


def test_provider_transaction_hash_mismatch_is_retryable(monkeypatch):
    import app.services.transaction_verification as verification

    monkeypatch.setenv("ETHERSCAN_API_KEY", "server-only-key")
    monkeypatch.setenv("COMMUNITY_TX_FINGERPRINT_SECRET", "k" * 40)
    monkeypatch.setattr(
        verification.httpx,
        "AsyncClient",
        lambda **kwargs: _FakeEtherscanClient(_verification_responses(tx_hash="0x" + "4" * 64)),
    )
    with pytest.raises(VerificationPending, match="交易标识不一致"):
        asyncio.run(verification.verify_evm_contribution(
            "ethereum",
            "0x1234567890abcdef1234567890abcdef12345678",
            "0x" + "5" * 64,
            Decimal("0.063"),
        ))
