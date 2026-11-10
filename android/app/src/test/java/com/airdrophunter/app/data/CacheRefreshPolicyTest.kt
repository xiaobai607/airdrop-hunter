package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CacheRefreshPolicyTest {
    private fun metadata(
        fetchedAt: Long? = 1_000L,
        expiresAt: Long? = 901_000L,
        status: String = "ready",
        lastManual: Long? = null
    ) = CacheMetadataEntity(
        cacheKey = "market", source = "provider", fetchedAt = fetchedAt,
        expiresAt = expiresAt, ttlMillis = 900_000L, refreshStatus = status,
        lastError = null, lastManualRefreshAt = lastManual
    )

    @Test
    fun cacheIsFreshOnlyWithinSuccessfulTtlWindow() {
        assertTrue(CacheRefreshPolicy.isFresh(metadata(), 500_000L))
        assertFalse(CacheRefreshPolicy.isFresh(metadata(), 901_000L))
        assertFalse(CacheRefreshPolicy.isFresh(metadata(status = "stale"), 500_000L))
        assertFalse(CacheRefreshPolicy.isFresh(metadata(fetchedAt = null), 500_000L))
    }

    @Test
    fun manualRefreshThrottleHasExactThirtySecondBoundary() {
        val row = metadata(lastManual = 10_000L)
        assertTrue(CacheRefreshPolicy.isManualRefreshThrottled(row, 39_999L))
        assertFalse(CacheRefreshPolicy.isManualRefreshThrottled(row, 40_000L))
        assertFalse(CacheRefreshPolicy.isManualRefreshThrottled(metadata(), 39_999L))
    }

    @Test
    fun failedRefreshUsesShortCooldownWithoutMarkingOldPayloadFresh() {
        val row = metadata(fetchedAt = 1_000L, expiresAt = 40_000L, status = "stale")
        assertTrue(CacheRefreshPolicy.isFailureCooldownActive(row, 20_000L))
        assertFalse(CacheRefreshPolicy.isFresh(row, 20_000L))
        assertFalse(CacheRefreshPolicy.isFailureCooldownActive(row, 40_000L))
    }

    @Test
    fun retryBackoffIsExponentialBoundedAndHonorsRetryAfter() {
        assertEquals(500L, CacheRefreshPolicy.retryDelayMillis(0))
        assertEquals(1_000L, CacheRefreshPolicy.retryDelayMillis(1))
        assertEquals(2_000L, CacheRefreshPolicy.retryDelayMillis(2))
        assertEquals(8_000L, CacheRefreshPolicy.retryDelayMillis(9))
        assertEquals(8_000L, CacheRefreshPolicy.retryDelayMillis(0, 30_000L))
        assertEquals(600L, CacheRefreshPolicy.retryDelayMillis(0, jitterFactor = 1.2))
    }

    @Test
    fun onlyTransientTransportFailuresAreRetried() {
        assertTrue(CacheRefreshPolicy.isRetryable(IOException("offline")))
        assertFalse(CacheRefreshPolicy.isRetryable(IllegalArgumentException("bad request")))
    }
}
