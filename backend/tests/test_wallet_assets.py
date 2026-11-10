from decimal import Decimal

from app.routers.wallet_assets import (
    normalize_bitcoin_asset,
    normalize_evm_assets,
    normalize_solana_assets,
)


def test_evm_normalization_keeps_unverified_and_unpriced_assets_out_of_verified_bucket():
    rows = [
        {"token": {"address_hash": "0x" + "a" * 40, "symbol": "SCAM", "name": "Unknown", "decimals": "18", "is_verified": False}, "value": "1000000000000000000"},
        {"token": {"address_hash": "0x" + "b" * 40, "symbol": "USDC", "name": "USD Coin", "decimals": "6", "is_verified": True}, "value": "2500000"},
    ]
    assets = normalize_evm_assets("ethereum", "0x" + "1" * 40, "0x0de0b6b3a7640000", rows, Decimal("2000"), {})
    assert assets[0].formatted_quantity == "1"
    assert assets[0].is_verified is True
    assert assets[1].is_verified is False
    assert assets[1].price_usd is None
    assert assets[2].is_verified is True
    assert assets[2].price_usd is None


def test_bitcoin_balance_includes_confirmed_and_mempool_net_satoshis():
    assets = normalize_bitcoin_asset({
        "chain_stats": {"funded_txo_sum": 150000000, "spent_txo_sum": 50000000},
        "mempool_stats": {"funded_txo_sum": 20000000, "spent_txo_sum": 10000000},
    }, Decimal("60000"))
    assert len(assets) == 1
    assert assets[0].raw_quantity == "110000000"
    assert assets[0].formatted_quantity == "1.1"
    assert assets[0].usd_value == "66000.0"
    assert assets[0].is_verified is True


def test_solana_only_known_mints_receive_verified_metadata_and_prices():
    mint = "EPjFWdd5AufqSSqeM2qXzybapC8G4wEGGkZwyTDt1v"
    unknown = "UnknownMint1111111111111111111111111111111"
    payload = {"result": {"value": [
        {"account": {"data": {"parsed": {"info": {"mint": mint, "tokenAmount": {"amount": "1250000", "decimals": 6}}}}}},
        {"account": {"data": {"parsed": {"info": {"mint": unknown, "tokenAmount": {"amount": "100", "decimals": 2}}}}}},
    ]}}
    assets = normalize_solana_assets("wallet", "1000000000", [payload], Decimal("100"), {mint: Decimal("1")})
    assert assets[0].symbol == "SOL"
    assert assets[1].symbol == "USDC"
    assert assets[1].is_verified is True
    assert assets[1].usd_value == "1.25"
    assert assets[2].is_verified is False
    assert assets[2].price_usd is None
