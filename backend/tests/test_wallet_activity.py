from decimal import Decimal

from app.routers.wallet_activity import (
    build_bitcoin_transactions,
    build_evm_transactions,
    build_solana_transactions,
)


WALLET = "bc1qexamplewallet"


def bitcoin_row(*input_addresses, missing_prevout=False):
    inputs = []
    for index, address in enumerate(input_addresses):
        prevout = {} if missing_prevout and index == len(input_addresses) - 1 else {
            "scriptpubkey_address": address
        }
        inputs.append({"prevout": prevout})
    return {
        "txid": "a" * 64,
        "fee": 1200,
        "status": {"confirmed": True, "block_time": 1_700_000_000},
        "vin": inputs,
        "vout": [{"scriptpubkey_address": "bc1qrecipient"}],
    }


def test_bitcoin_fee_is_attributed_only_when_every_input_is_known_and_owned():
    transactions = build_bitcoin_transactions(
        [bitcoin_row(WALLET, WALLET)], WALLET, Decimal("60000"), 50
    )

    assert len(transactions) == 1
    assert transactions[0].fee_paid_by_wallet is True
    assert transactions[0].fee_type == "bitcoin_fee_sat"


def test_bitcoin_mixed_input_fee_is_not_attributed_to_one_wallet():
    transactions = build_bitcoin_transactions(
        [bitcoin_row(WALLET, "bc1qother")], WALLET, Decimal("60000"), 50
    )

    assert len(transactions) == 1
    assert transactions[0].fee_paid_by_wallet is False
    assert transactions[0].fee_type == "bitcoin_fee_sat_shared_inputs"


def test_bitcoin_missing_prevout_metadata_prevents_fee_attribution():
    transactions = build_bitcoin_transactions(
        [bitcoin_row(WALLET, "bc1qother", missing_prevout=True)],
        WALLET,
        Decimal("60000"),
        50,
    )

    assert len(transactions) == 1
    assert transactions[0].fee_paid_by_wallet is False
    assert transactions[0].fee_type == "bitcoin_fee_sat_shared_inputs"


def evm_row(**overrides):
    row = {
        "hash": "0x" + "b" * 64,
        "gasUsed": "21000",
        "gasPrice": "2000000000",
        "timeStamp": "1700000000",
        "from": "0x" + "1" * 40,
        "to": "0x" + "2" * 40,
        "isError": "0",
    }
    row.update(overrides)
    return row


def test_evm_l1_fee_is_included_when_provider_supplies_it():
    transactions = build_evm_transactions(
        [evm_row(l1Fee="100")],
        "base",
        "0x" + "1" * 40,
        Decimal("2000"),
    )

    assert len(transactions) == 1
    assert transactions[0].total_fee_base_unit == "42000000000100"
    assert transactions[0].fee_type == "evm_gas_fee_with_l1_wei"
    assert transactions[0].fee_paid_by_wallet is True


def test_optimism_or_base_without_l1_fee_is_marked_execution_only():
    transactions = build_evm_transactions(
        [evm_row()],
        "optimism",
        "0x" + "1" * 40,
        Decimal("2000"),
    )

    assert len(transactions) == 1
    assert transactions[0].fee_type == "evm_l2_execution_fee_wei"


def test_failed_evm_transaction_retains_fee_but_is_not_successful():
    transactions = build_evm_transactions(
        [evm_row(isError="1")],
        "ethereum",
        "0x" + "1" * 40,
        Decimal("2000"),
    )

    assert len(transactions) == 1
    assert transactions[0].status == "failed"
    assert transactions[0].total_fee_base_unit == "42000000000000"

def test_solana_fee_is_attributed_to_first_account_key_fee_payer():
    signature = "a" * 64
    transactions = build_solana_transactions(
        [{
            "hash": signature,
            "timestamp": 1_700_000_000,
            "from_address": WALLET,
            "to_address": "solana-recipient",
            "total_fee_base_unit": "5000",
            "status": "success",
        }],
        WALLET,
        Decimal("100"),
        50,
    )
    assert len(transactions) == 1
    assert transactions[0].fee_paid_by_wallet is True
    assert transactions[0].fee_type == "solana_fee_lamports"
    assert transactions[0].gas_cost_native == "0.000005"


def test_solana_fee_is_not_attributed_when_another_address_is_fee_payer():
    transactions = build_solana_transactions(
        [{
            "hash": "b" * 64,
            "timestamp": 1_700_000_000,
            "from_address": "another-wallet",
            "to_address": WALLET,
            "total_fee_base_unit": "5000",
            "status": "success",
        }],
        WALLET,
        Decimal("100"),
        50,
    )
    assert len(transactions) == 1
    assert transactions[0].fee_paid_by_wallet is False


def test_tron_basic_transfer_fee_is_attributed_only_to_matching_owner():
    from app.routers.wallet_activity import tron_fee_is_paid_by_wallet

    wallet_hex = "41" + "11" * 20
    contracts = [{
        "type": "TransferContract",
        "parameter": {"value": {"owner_address": wallet_hex}},
    }]
    assert tron_fee_is_paid_by_wallet(contracts, wallet_hex, 270)


def test_tron_smart_contract_fee_remains_unassigned_even_when_owner_matches():
    from app.routers.wallet_activity import tron_fee_is_paid_by_wallet

    wallet_hex = "41" + "11" * 20
    contracts = [{
        "type": "TriggerSmartContract",
        "parameter": {"value": {"owner_address": wallet_hex}},
    }]
    assert tron_fee_is_paid_by_wallet(contracts, wallet_hex, 270) is False


def test_tron_zero_fee_is_not_prefilled_as_paid_gas():
    from app.routers.wallet_activity import tron_fee_is_paid_by_wallet

    wallet_hex = "41" + "11" * 20
    contracts = [{
        "type": "TransferContract",
        "parameter": {"value": {"owner_address": wallet_hex}},
    }]
    assert tron_fee_is_paid_by_wallet(contracts, wallet_hex, 0) is False


def test_solana_rpc_parser_reads_confirmed_fee_and_first_account_fee_payer():
    import asyncio
    from app.routers.wallet_activity import solana_transactions

    signature = "a" * 64
    other_wallet = "another-solana-wallet"

    class FakeResponse:
        def __init__(self, payload):
            self.payload = payload

        def raise_for_status(self):
            return None

        def json(self):
            return self.payload

    class FakeClient:
        def __init__(self):
            self.calls = 0

        async def post(self, url, json):
            self.calls += 1
            if json["method"] == "getSignaturesForAddress":
                return FakeResponse({"result": [{"signature": signature, "blockTime": 1_700_000_000}]})
            return FakeResponse({
                "result": {
                    "blockTime": 1_700_000_000,
                    "meta": {"fee": 5000, "err": {"InstructionError": [0, "Custom"]}},
                    "transaction": {
                        "message": {
                            "accountKeys": [
                                {"pubkey": other_wallet},
                                {"pubkey": "recipient"},
                            ]
                        }
                    },
                }
            })

    rows = asyncio.run(solana_transactions(FakeClient(), WALLET, 50))
    assert len(rows) == 1
    assert rows[0]["from_address"] == other_wallet
    assert rows[0]["fee_paid_by_wallet"] is False
    assert rows[0]["total_fee_base_unit"] == "5000"
    assert rows[0]["status"] == "failed"
