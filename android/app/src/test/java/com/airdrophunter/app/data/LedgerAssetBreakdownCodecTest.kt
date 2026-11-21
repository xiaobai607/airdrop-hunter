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
        assertEquals("Gas 总计：0.125 ETH（USD估值 $250.00） · Gas 总计：1.5 BNB（USD估值 $900.00）",
            formatLedgerAssetBreakdown(encoded, "gas"))
        assertTrue(LedgerAssetBreakdownCodec.decode(null).isEmpty())
    }

    @Test
    fun formatterLabelsFeeComponentsAndDoesNotRepeatExecutionTotal() {
        val encoded = LedgerAssetBreakdownCodec.encode(listOf(
            LedgerAssetAmount("gas", "0.003", "ETH", 18, 9.0),
            LedgerAssetAmount("execution_fee", "0.003", "ETH", 18, 9.0)
        ))
        assertEquals("Gas 总计：0.003 ETH（USD估值 $9.00）", formatLedgerAssetBreakdown(encoded))

        val rollup = LedgerAssetBreakdownCodec.encode(listOf(
            LedgerAssetAmount("gas", "0.006", "ETH", 18, 18.0),
            LedgerAssetAmount("l2_fee", "0.003", "ETH", 18, 9.0),
            LedgerAssetAmount("l1_fee", "0.002", "ETH", 18, 6.0),
            LedgerAssetAmount("operator_fee", "0.001", "ETH", 18, 3.0)
        ))
        assertEquals(
            "Gas 总计：0.006 ETH（USD估值 $18.00） · L2 执行费：0.003 ETH（USD估值 $9.00） · L1 数据费：0.002 ETH（USD估值 $6.00） · Operator Fee：0.001 ETH（USD估值 $3.00）",
            formatLedgerAssetBreakdown(rollup)
        )
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

    @Test
    fun estimateTotalsStaySeparateAndUnknownValuesAreNotTreatedAsZero() {
        fun interaction(id: String, expectedGas: Double?, expectedReward: Double?) =
            UserInteractionEntity(
                id = id, projectLocalId = "project", projectOfficialId = null, projectName = "Project",
                txHash = id, walletAddress = "wallet", network = "ethereum",
                gasCostUsd = 1.5, actualRewardUsd = 4.0, interactionMethod = "manual confirmation", timestamp = 1L,
                expectedGasCostUsd = expectedGas, expectedRewardUsd = expectedReward
            )
        val complete = aggregateLedgerEstimatePeriodTotals(
            listOf(interaction("one", 1.25, 8.0), interaction("two", 2.75, 12.0))
        )
        assertEquals("4", complete.expectedGasCostUsd?.stripTrailingZeros()?.toPlainString())
        assertEquals("20", complete.expectedRewardUsd?.stripTrailingZeros()?.toPlainString())
        val incomplete = aggregateLedgerEstimatePeriodTotals(
            listOf(interaction("one", 1.25, null), interaction("two", null, 12.0))
        )
        assertEquals(null, incomplete.expectedGasCostUsd)
        assertEquals(null, incomplete.expectedRewardUsd)
        assertEquals(null, aggregateLedgerEstimatePeriodTotals(emptyList()).expectedGasCostUsd)
    }
}
