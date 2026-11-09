package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerAssetBreakdownCodecTest {
    @Test
    fun roundTripsMultipleTokenAssetsWithoutCombiningCurrencies() {
        val assets = listOf(
            LedgerAssetAmount(
                kind = "gas", amount_native = "0.125", symbol = "ETH", decimals = 18,
                usd_valuation = 250.0, valuation_timestamp = "2026-11-09T08:00:00Z",
                valuation_source = "coinpaprika"
            ),
            LedgerAssetAmount(
                kind = "gas", amount_native = "1.5", symbol = "BNB", decimals = 18,
                usd_valuation = 900.0, valuation_timestamp = "2026-11-09T08:01:00Z",
                valuation_source = "coinpaprika"
            ),
            LedgerAssetAmount(
                kind = "reward", amount_native = "100", symbol = "LINEA", decimals = 18,
                usd_valuation = null, valuation_timestamp = null, valuation_source = null
            )
        )
        val encoded = LedgerAssetBreakdownCodec.encode(assets)
        val decoded = LedgerAssetBreakdownCodec.decode(encoded)

        assertEquals(assets, decoded)
        assertEquals("0.125 ETH（USD估值 $250.00） · 1.5 BNB（USD估值 $900.00）",
            formatLedgerAssetBreakdown(encoded, "gas"))
        assertTrue(LedgerAssetBreakdownCodec.decode(null).isEmpty())
    }
}
