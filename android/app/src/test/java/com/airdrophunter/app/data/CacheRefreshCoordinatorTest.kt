package com.airdrophunter.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CacheRefreshCoordinatorTest {
    @Test
    fun sameKeyConcurrentRefreshesReuseTheFirstSuccessfulResult() = runBlocking {
        var now = 10_000L
        var fetches = 0
        val metadata = mutableMapOf<String, CacheMetadataEntity>()
        var cached: String? = null
        val engine = CacheRefreshCoordinator(
            clock = { now },
            wait = {},
            jitter = { 1.0 },
            readMetadata = { key, source -> metadata["$source:$key"] },
            writeMetadata = { metadata["${it.source}:${it.cacheKey}"] = it }
        )

        val first = engine.load(
            cacheKey = "public-list", source = "directory", ttlMillis = 60_000L,
            readCache = { cached },
            fetch = { fetches++; "verified-list" },
            save = { cached = it }
        )
        now += 1_000L
        val second = engine.load(
            cacheKey = "public-list", source = "directory", ttlMillis = 60_000L,
            readCache = { cached },
            fetch = { fetches++; "unexpected-second-fetch" },
            save = { cached = it }
        )

        assertEquals("verified-list", first.data)
        assertEquals("verified-list", second.data)
        assertEquals(1, fetches)
        assertFalse(second.stale)
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
}
