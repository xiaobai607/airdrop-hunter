package com.airdrophunter.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

enum class CacheDisplayStatus { FRESH, REFRESHING, STALE, ERROR, UNKNOWN }

class ProviderHttpException(
    val statusCode: Int,
    val retryAfterHeader: String?,
    message: String
) : IOException(message)

object CacheRefreshPolicy {
    fun walletAssetCacheKey(accountScope: String, walletId: String, chain: String): String =
        "wallet-assets:$accountScope:$walletId:${chain.lowercase()}"

    fun displayStatus(metadata: CacheMetadataEntity?, now: Long): CacheDisplayStatus {
        metadata ?: return CacheDisplayStatus.UNKNOWN
        return when {
            metadata.refreshStatus == "refreshing" -> CacheDisplayStatus.REFRESHING
            metadata.refreshStatus == "error" -> CacheDisplayStatus.ERROR
            isFresh(metadata, now) -> CacheDisplayStatus.FRESH
            else -> CacheDisplayStatus.STALE
        }
    }

    const val DEFAULT_TTL_MILLIS = 15 * 60 * 1000L
    const val MANUAL_REFRESH_THROTTLE_MILLIS = 30 * 1000L
    const val FAILURE_COOLDOWN_MILLIS = 30 * 1000L
    const val MAX_RETRY_ATTEMPTS = 3
    const val MAX_RETRY_DELAY_MILLIS = 8_000L

    fun isFresh(metadata: CacheMetadataEntity?, now: Long): Boolean {
        if (metadata?.refreshStatus != "ready") return false
        val fetchedAt = metadata.fetchedAt ?: return false
        val expiresAt = metadata.expiresAt ?: return false
        return now >= fetchedAt && now < expiresAt
    }

    fun isFailureCooldownActive(metadata: CacheMetadataEntity?, now: Long): Boolean {
        if (metadata?.refreshStatus !in setOf("stale", "error")) return false
        val retryAt = metadata?.expiresAt ?: return false
        return now >= 0L && now < retryAt
    }

    fun isManualRefreshThrottled(metadata: CacheMetadataEntity?, now: Long): Boolean {
        val last = metadata?.lastManualRefreshAt ?: return false
        return now >= last && now - last < MANUAL_REFRESH_THROTTLE_MILLIS
    }

    fun retryDelayMillis(attempt: Int, retryAfterMillis: Long? = null, jitterFactor: Double = 1.0): Long {
        val exponential = (500L shl attempt.coerceIn(0, 4)).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
        val requested = retryAfterMillis?.coerceIn(0L, MAX_RETRY_DELAY_MILLIS) ?: exponential
        return (requested * jitterFactor.coerceIn(0.8, 1.2)).toLong()
            .coerceIn(250L, MAX_RETRY_DELAY_MILLIS)
    }

    fun isRetryable(error: Throwable): Boolean = when (error) {
        is ProviderHttpException -> error.statusCode in setOf(408, 425, 429) || error.statusCode in 500..599
        is IOException -> true
        is HttpException -> error.code() in setOf(408, 425, 429) || error.code() in 500..599
        else -> false
    }

    fun retryAfterMillis(error: Throwable, now: Long): Long? {
        val value = when (error) {
            is ProviderHttpException -> error.retryAfterHeader
            is HttpException -> error.response()?.headers()?.get("Retry-After")
            else -> null
        } ?: return null
        value.trim().toLongOrNull()?.let { return it.coerceIn(0L, 8L) * 1_000L }
        return runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli() - now
        }.getOrNull()?.coerceIn(0L, MAX_RETRY_DELAY_MILLIS)
    }
}

data class CacheRefreshResult<T>(
    val data: T?,
    val stale: Boolean,
    val errorMessage: String? = null,
    val throttled: Boolean = false
)

/** Shared cache refresh engine for app data sources: TTL, deduplication, retries and stale-data retention. */
class CacheRefreshCoordinator(
    private val clock: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val jitter: () -> Double = { Random.nextDouble(0.8, 1.2) },
    private val readMetadata: suspend (String, String) -> CacheMetadataEntity? = { key, source ->
        CacheStore.readCacheMetadata(key, source)
    },
    private val writeMetadata: suspend (CacheMetadataEntity) -> Unit = { metadata ->
        CacheStore.writeCacheMetadata(metadata)
    }
) {
    // Fixed stripes bound memory while ensuring same-key work never runs concurrently.
    private val locks = Array(64) { Mutex() }

    suspend fun <T> load(
        cacheKey: String,
        source: String,
        ttlMillis: Long = CacheRefreshPolicy.DEFAULT_TTL_MILLIS,
        manual: Boolean = false,
        force: Boolean = false,
        readCache: suspend () -> T?,
        fetch: suspend () -> T,
        save: suspend (T) -> Unit
    ): CacheRefreshResult<T> {
        val initialCached = readCache()
        var metadata = readMetadata(cacheKey, source)
        val initialNow = clock()
        val initialFresh = CacheRefreshPolicy.isFresh(metadata, initialNow)
        if (!force && !manual && initialCached != null && initialFresh) return CacheRefreshResult(initialCached, false)
        if (!force && !manual && CacheRefreshPolicy.isFailureCooldownActive(metadata, initialNow)) {
            return CacheRefreshResult(initialCached, stale = true, errorMessage = metadata?.lastError)
        }
        if (!force && manual && CacheRefreshPolicy.isManualRefreshThrottled(metadata, initialNow)) {
            return CacheRefreshResult(initialCached, stale = !initialFresh, throttled = true)
        }

        val lock = locks[(("$source:$cacheKey").hashCode() and Int.MAX_VALUE) % locks.size]
        return lock.withLock {
            var cached = readCache()
            metadata = readMetadata(cacheKey, source)
            val lockedNow = clock()
            val fresh = CacheRefreshPolicy.isFresh(metadata, lockedNow)
            if (!force && !manual && cached != null && fresh) return@withLock CacheRefreshResult(cached, false)
            if (!force && !manual && CacheRefreshPolicy.isFailureCooldownActive(metadata, lockedNow)) {
                return@withLock CacheRefreshResult(cached, stale = true, errorMessage = metadata?.lastError)
            }
            if (!force && manual && CacheRefreshPolicy.isManualRefreshThrottled(metadata, lockedNow)) {
                return@withLock CacheRefreshResult(cached, stale = !fresh, throttled = true)
            }

            val lastManual = if (manual) lockedNow else metadata?.lastManualRefreshAt
            writeMetadata(
                CacheMetadataEntity(cacheKey, source, metadata?.fetchedAt, metadata?.expiresAt,
                    ttlMillis, "refreshing", null, lastManual)
            )

            try {
                var result: T? = null
                var failure: Exception? = null
                for (attempt in 0 until CacheRefreshPolicy.MAX_RETRY_ATTEMPTS) {
                    try {
                        result = fetch()
                        failure = null
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failure = error
                        if (!CacheRefreshPolicy.isRetryable(error) ||
                            attempt == CacheRefreshPolicy.MAX_RETRY_ATTEMPTS - 1) break
                        wait(CacheRefreshPolicy.retryDelayMillis(
                            attempt, CacheRefreshPolicy.retryAfterMillis(error, clock()), jitter()
                        ))
                    }
                }
                val value = result ?: throw (failure ?: IllegalStateException("缓存刷新未返回数据"))
                save(value)
                val completedAt = clock()
                val safeTtl = ttlMillis.coerceAtLeast(1L)
                writeMetadata(
                    CacheMetadataEntity(cacheKey, source, completedAt, completedAt + safeTtl,
                        safeTtl, "ready", null, lastManual)
                )
                CacheRefreshResult(value, false)
            } catch (cancelled: CancellationException) {
                writeMetadata(
                    CacheMetadataEntity(cacheKey, source, metadata?.fetchedAt, metadata?.expiresAt,
                        ttlMillis, "idle", null, lastManual)
                )
                throw cancelled
            } catch (error: Exception) {
                cached = readCache()
                val failedAt = clock()
                writeMetadata(
                    CacheMetadataEntity(
                        cacheKey, source, metadata?.fetchedAt,
                        failedAt + CacheRefreshPolicy.FAILURE_COOLDOWN_MILLIS,
                        ttlMillis, if (cached != null) "stale" else "error",
                        (error.message ?: "刷新失败").take(160), lastManual
                    )
                )
                if (cached != null) CacheRefreshResult(cached, true, error.message ?: "刷新失败，暂时显示上次数据")
                else CacheRefreshResult(null, true, error.message ?: "刷新失败")
            }
        }
    }

    companion object {
        val shared = CacheRefreshCoordinator()
    }
}
