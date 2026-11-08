from decimal import Decimal

from app.routers.wallet_activity import (
    build_bitcoin_transactions,
    build_evm_transactions,
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
