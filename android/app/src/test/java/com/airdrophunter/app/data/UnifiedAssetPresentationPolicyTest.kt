package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedAssetPresentationPolicyTest {
    private fun asset(id: String, value: Double?, verified: Boolean = true, chain: String = "ethereum", price: Double? = 1.0) =
        UnifiedAsset("wallet-1", chain, id, null, id.uppercase(), id, 18, "1", "1", price, value, verified, "test-provider", 1L, 1L)

    @Test fun normalListHidesDustUnpricedAndUnverifiedAssets() {
        val rows = listOf(asset("large", 25.0), asset("dust", 0.099), asset("unpriced", null), asset("unverified", 50.0, verified = false))
        assertEquals(listOf("large"), visibleUnifiedAssets(rows).map { it.assetId })
        assertEquals(setOf("dust", "unpriced", "unverified"), hiddenUnifiedAssets(rows).map { it.assetId }.toSet())
    }

    @Test fun distributionUsesTopFiveVerifiedAssetsAndAggregatesOthers() {
        val rows = (1..7).map { asset("token-$it", (80 - it).toDouble()) }
        val slices = unifiedAssetDistribution(rows, "ethereum")
        assertEquals(6, slices.size)
        assertEquals(listOf("token-1", "token-2", "token-3", "token-4", "token-5", "others"), slices.map { it.assetId })
        assertEquals(147.0, slices.last().usdValue, 0.000001)
    }

    @Test fun bitcoinNeverShowsAssetDistributionChart() {
        val rows = listOf(asset("btc", 100.0, chain = "bitcoin"))
        assertTrue(unifiedAssetDistribution(rows, "bitcoin").isEmpty())
        assertTrue(unifiedAssetDistribution(rows, "BTC").isEmpty())
    }
}
