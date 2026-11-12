package com.airdrophunter.app

import com.airdrophunter.app.data.OkxTicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMarketCachePolicyTest {
    @Test fun cachedRowsSkipInitialBlockingLoadingState() {
        val cached = listOf(OkxTicker("BTC-USDT", 65000.0, 1.2))
        assertFalse(homeMarketShouldShowInitialLoading(cached))
    }

    @Test fun emptyCacheKeepsInitialLoadingState() {
        assertTrue(homeMarketShouldShowInitialLoading(emptyList()))
    }

    @Test fun failedRefreshRetainsCachedRows() {
        val cached = listOf(
            OkxTicker("BTC-USDT", 65000.0, 1.2),
            OkxTicker("ETH-USDT", 3000.0, -0.4)
        )
        assertEquals(cached, resolveHomeMarketRows(cached, refreshedRows = null))
    }

    @Test fun successfulRefreshReplacesCachedRows() {
        val cached = listOf(OkxTicker("BTC-USDT", 65000.0, 1.2))
        val refreshed = listOf(OkxTicker("BTC-USDT", 66000.0, 1.8))
        assertEquals(refreshed, resolveHomeMarketRows(cached, refreshed))
    }
}
