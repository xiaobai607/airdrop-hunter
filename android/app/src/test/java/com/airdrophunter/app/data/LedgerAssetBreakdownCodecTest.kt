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

    @Test
    fun periodTotalsKeepNetworksAndCurrenciesSeparateAndRejectPartialUsdTotals() {
        fun interaction(id: String, network: String, assets: List<LedgerAssetAmount>) =
            UserInteractionEntity(
                id = id,
                projectLocalId = "project",
                projectOfficialId = null,
                projectName = "Project",
                txHash = id,
                walletAddress = "wallet",
                network = network,
                gasCostUsd = assets.mapNotNull { it.usd_valuation }.sum(),
                actualRewardUsd = 0.0,
                interactionMethod = "manual confirmation",
                timestamp = 1L,
                assetBreakdownJson = LedgerAssetBreakdownCodec.encode(assets)
            )

        val rows = listOf(
            interaction("eth-1", "ethereum", listOf(
                LedgerAssetAmount("gas", "0.1", "ETH", 18, 200.0),
                LedgerAssetAmount("reward", "10", "TOKEN", 18, null)
            )),
            interaction("eth-2", "ethereum", listOf(
                LedgerAssetAmount("gas", "0.2", "ETH", 18, 400.0)
            )),
            interaction("base-eth", "base", listOf(
                LedgerAssetAmount("gas", "0.3", "ETH", 18, 600.0)
            )),
            interaction("bnb", "bsc", listOf(
                LedgerAssetAmount("gas", "1.5", "BNB", 18, 900.0)
            ))
        )

        val gas = aggregateLedgerAssetPeriodTotals(rows, "gas")
        assertEquals(3, gas.size)
        val ethereumEth = gas.single { it.network == "ethereum" && it.symbol == "ETH" }
        assertEquals("0.3", ethereumEth.amountNative.stripTrailingZeros().toPlainString())
        assertEquals("600.0", ethereumEth.usdValuation?.toPlainString())
        assertEquals("base", gas.single { it.network == "base" && it.symbol == "ETH" }.network)
        assertEquals("1.5", gas.single { it.symbol == "BNB" }.amountNative.stripTrailingZeros().toPlainString())

        val reward = aggregateLedgerAssetPeriodTotals(rows, "reward")
        assertEquals(1, reward.size)
        assertEquals("10", reward.single().amountNative.stripTrailingZeros().toPlainString())
        assertEquals(null, reward.single().usdValuation)
    }

}
