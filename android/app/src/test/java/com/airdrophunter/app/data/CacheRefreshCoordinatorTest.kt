package com.airdrophunter.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CacheRefreshCoordinatorTest {
    @Test
    fun sameKeyConcurrentRefreshesReuseTheFirstSuccessfulResult() = runBlocking {
        val metadata = mutableMapOf<String, CacheMetadataEntity>()
        var cached: String? = null
        var fetches = 0
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val engine = CacheRefreshCoordinator(
            clock = { 10_000L },
            wait = {},
            jitter = { 1.0 },
            readMetadata = { key, source -> metadata["$source:$key"] },
            writeMetadata = { metadata["${it.source}:${it.cacheKey}"] = it }
        )

        // Hold the first network request open while the second caller reaches the
        // same source/key. This proves real overlap rather than sequential reuse.
        val first = async {
            engine.load(
                cacheKey = "public-list", source = "directory", ttlMillis = 60_000L,
                readCache = { cached },
                fetch = {
                    fetches++
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    "verified-list"
                },
                save = { cached = it }
            )
        }
        fetchStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            engine.load(
                cacheKey = "public-list", source = "directory", ttlMillis = 60_000L,
                readCache = { cached },
                fetch = { fetches++; "unexpected-second-fetch" },
                save = { cached = it }
            )
        }
        yield()
        releaseFetch.complete(Unit)

        val firstResult = first.await()
        val secondResult = second.await()
        assertEquals("verified-list", firstResult.data)
        assertEquals("verified-list", secondResult.data)
        assertEquals(1, fetches)
        assertFalse(secondResult.stale)
    }

    @Test
    fun failedRefreshKeepsLastKnownGoodPayloadAndMarksItStale() = runBlocking {
        var now = 100_000L
        val metadata = mutableMapOf<String, CacheMetadataEntity>()
        var cached: String? = "last-known-good"
        var fetches = 0
        val engine = CacheRefreshCoordinator(
            clock = { now }, wait = {}, jitter = { 1.0 },
            readMetadata = { key, source -> metadata["$source:$key"] },
            writeMetadata = { metadata["${it.source}:${it.cacheKey}"] = it }
        )

        val result = engine.load(
            cacheKey = "catalog", source = "public-directory", ttlMillis = 1_000L,
            readCache = { cached },
            fetch = { fetches++; throw IOException("network unavailable") },
            save = { cached = it }
        )

        assertEquals(3, fetches)
        assertEquals("last-known-good", result.data)
        assertTrue(result.stale)
        assertEquals("stale", metadata["public-directory:catalog"]?.refreshStatus)
        assertFalse(CacheRefreshPolicy.isFresh(metadata["public-directory:catalog"], now))
    }

    @Test
    fun cancellationRestoresIdleMetadataAndPropagatesCancellation() = runBlocking {
        val metadata = mutableMapOf<String, CacheMetadataEntity>()
        val engine = CacheRefreshCoordinator(
            clock = { 200_000L }, wait = {}, jitter = { 1.0 },
            readMetadata = { key, source -> metadata["$source:$key"] },
            writeMetadata = { metadata["${it.source}:${it.cacheKey}"] = it }
        )

        var cancelled = false
        try {
            engine.load(
                cacheKey = "snapshot", source = "community-cdn",
                readCache = { null },
                fetch = { throw CancellationException("caller left screen") },
                save = {}
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals("idle", metadata["community-cdn:snapshot"]?.refreshStatus)
        assertNull(metadata["community-cdn:snapshot"]?.lastError)
    }

    @Test
    fun sourceAndCacheKeyTogetherDefineIndependentMetadataRecords() = runBlocking {
        val metadata = mutableMapOf<String, CacheMetadataEntity>()
        val engine = CacheRefreshCoordinator(
            clock = { 300_000L }, wait = {}, jitter = { 1.0 },
            readMetadata = { key, source -> metadata["$source:$key"] },
            writeMetadata = { metadata["${it.source}:${it.cacheKey}"] = it }
        )
        suspend fun load(source: String) = engine.load(
            cacheKey = "same-key", source = source,
            readCache = { null }, fetch = { source }, save = {}
        )

        load("provider-a")
        load("provider-b")

        assertEquals("ready", metadata["provider-a:same-key"]?.refreshStatus)
        assertEquals("ready", metadata["provider-b:same-key"]?.refreshStatus)
        assertEquals(2, metadata.size)
    }

    @Test
    fun manualRefreshThrottleBlocksManualButForceRefreshBypassesIt() = runBlocking {
        val now = 10_000L
        val metadata = mutableMapOf("directory:items" to CacheMetadataEntity(
            cacheKey = "items", source = "directory", fetchedAt = 9_000L,
            expiresAt = 20_000L, ttlMillis = 11_000L, refreshStatus = "ready",
            lastError = null, lastManualRefreshAt = 9_500L
        ))
        var cached: String? = "cached"
        var fetches = 0
        val engine = CacheRefreshCoordinator(
            clock = { now }, wait = {}, jitter = { 1.0 },
            readMetadata = { key, source -> metadata["$source:$key"] },
            writeMetadata = { metadata["${it.source}:${it.cacheKey}"] = it }
        )

        val throttled = engine.load(
            cacheKey = "items", source = "directory", manual = true,
            readCache = { cached }, fetch = { fetches++; "manual" }, save = { cached = it }
        )
        assertTrue(throttled.throttled)
        assertEquals("cached", throttled.data)
        assertEquals(0, fetches)

        val forced = engine.load(
            cacheKey = "items", source = "directory", manual = true, force = true,
            readCache = { cached }, fetch = { fetches++; "forced" }, save = { cached = it }
        )
        assertEquals("forced", forced.data)
        assertEquals(1, fetches)
    }

    @Test
    fun failureCooldownSuppressesBackgroundRefreshButForceRefreshBypassesIt() = runBlocking {
        val now = 10_000L
        val metadata = mutableMapOf("directory:items" to CacheMetadataEntity(
            cacheKey = "items", source = "directory", fetchedAt = 5_000L,
            expiresAt = 20_000L, ttlMillis = 1_000L, refreshStatus = "stale",
            lastError = "temporary outage", lastManualRefreshAt = null
        ))
        var cached: String? = "last-good"
        var fetches = 0
        val engine = CacheRefreshCoordinator(
            clock = { now }, wait = {}, jitter = { 1.0 },
            readMetadata = { key, source -> metadata["$source:$key"] },
            writeMetadata = { metadata["${it.source}:${it.cacheKey}"] = it }
        )

        val background = engine.load(
            cacheKey = "items", source = "directory",
            readCache = { cached }, fetch = { fetches++; "background" }, save = { cached = it }
        )
        assertEquals("last-good", background.data)
        assertTrue(background.stale)
        assertEquals(0, fetches)

        val forced = engine.load(
            cacheKey = "items", source = "directory", force = true,
            readCache = { cached }, fetch = { fetches++; "forced" }, save = { cached = it }
        )
        assertEquals("forced", forced.data)
        assertEquals(1, fetches)
    }

    @Test
    fun nonRetryableFailureDoesNotRepeatAndRetryAfterIsCapped() = runBlocking {
        val metadata = mutableMapOf<String, CacheMetadataEntity>()
        var fetches = 0
        val engine = CacheRefreshCoordinator(
            clock = { 50_000L }, wait = {}, jitter = { 1.0 },
            readMetadata = { key, source -> metadata["$source:$key"] },
            writeMetadata = { metadata["${it.source}:${it.cacheKey}"] = it }
        )

        val result = engine.load(
            cacheKey = "items", source = "directory",
            readCache = { null },
            fetch = { fetches++; throw ProviderHttpException(400, null, "bad request") },
            save = {}
        )
        assertNull(result.data)
        assertEquals(1, fetches)
        assertFalse(CacheRefreshPolicy.isRetryable(ProviderHttpException(400, null, "bad request")))
        assertTrue(CacheRefreshPolicy.isRetryable(ProviderHttpException(429, "120", "rate limited")))
        assertEquals(8_000L, CacheRefreshPolicy.retryAfterMillis(
            ProviderHttpException(429, "120", "rate limited"), 50_000L
        ))
    }

}
