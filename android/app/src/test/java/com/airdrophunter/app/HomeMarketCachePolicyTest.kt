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

    @Test fun manualCooldownUsesExplicitThirtySecondWarning() {
        assertEquals(
            "刚刚刷新过，请30秒后再试",
            homeMarketRefreshWarning("刚刚刷新过，请稍后再试", hasCachedRows = true)
        )
    }

    @Test fun providerFailureKeepsGenericStaleWarning() {
        assertEquals(
            "行情暂时不可用，正在显示上次缓存",
            homeMarketRefreshWarning("HTTP 503", hasCachedRows = true)
        )
    }
}
