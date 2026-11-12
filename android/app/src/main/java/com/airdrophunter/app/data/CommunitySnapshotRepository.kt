package com.airdrophunter.app.data

import android.util.Log
import com.airdrophunter.app.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Read-through cache for immutable public snapshots.
 * Room is always read first; CDN failures never delete or overwrite cached data.
 */
object CommunitySnapshotRepository {
    private const val TAG = "CommunitySnapshots"
    private val windows = listOf("overall", "7d", "3d", "1h")
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun readSnapshot(window: String): CommunitySnapshotDto? = withContext(Dispatchers.IO) {
        if (window !in windows) return@withContext null
        val raw = CacheStore.readPublic("snapshot:$window") ?: return@withContext null
        runCatching { gson.fromJson(raw, CommunitySnapshotDto::class.java) }
            .getOrNull()
            ?.takeIf { it.schema_version == 1 && it.window == window }
    }

    suspend fun refreshSnapshots(): Map<String, CommunitySnapshotDto> = withContext(Dispatchers.IO) {
        val base = BuildConfig.COMMUNITY_SNAPSHOT_BASE_URL.trimEnd('/')
        if (base.isBlank()) return@withContext readCachedSnapshots()

        val result = CacheRefreshCoordinator.shared.load(
            cacheKey = "community-snapshot:all",
            source = "community-snapshots",
            ttlMillis = SNAPSHOT_CACHE_TTL_MILLIS,
            readCache = { readCachedSnapshots().takeIf { it.isNotEmpty() } },
            fetch = { refreshSnapshotsFromNetwork(readCachedSnapshots()) },
            // The network method persists each independently verified snapshot and
            // commits manifest validators only after the complete set is available.
            save = { }
        )
        result.data.orEmpty()
    }

    private suspend fun readCachedSnapshots(): Map<String, CommunitySnapshotDto> =
        windows.mapNotNull { window -> readSnapshot(window)?.let { window to it } }.toMap()

    private suspend fun refreshSnapshotsFromNetwork(
        cached: Map<String, CommunitySnapshotDto>
    ): Map<String, CommunitySnapshotDto> = withContext(Dispatchers.IO) {
        val base = BuildConfig.COMMUNITY_SNAPSHOT_BASE_URL.trimEnd('/')
        if (base.isBlank()) return@withContext cached

        try {
            val baseUri = URI(base)
            if (!baseUri.scheme.equals("https", true) || baseUri.host.isNullOrBlank() || baseUri.userInfo != null) {
                return@withContext cached
            }
            val manifestRequestBuilder = Request.Builder()
                .url("$base/manifest.json")
                .header("Cache-Control", "no-cache")
                .get()
            CacheStore.readPublic("snapshot:manifest:etag")
                ?.takeIf { it.isNotBlank() }
                ?.let { manifestRequestBuilder.header("If-None-Match", it) }
            CacheStore.readPublic("snapshot:manifest:last-modified")
                ?.takeIf { it.isNotBlank() }
                ?.let { manifestRequestBuilder.header("If-Modified-Since", it) }

            var manifestNotModified = false
            var manifestEtag: String? = null
            var manifestLastModified: String? = null
            val manifestRaw = client.newCall(manifestRequestBuilder.build()).execute().use { response ->
                if (response.code == 304) {
                    manifestNotModified = true
                    null
                } else {
                    if (!response.isSuccessful) throw IllegalStateException("Snapshot manifest HTTP " + response.code)
                    manifestEtag = response.header("ETag")
                    manifestLastModified = response.header("Last-Modified")
                    response.body?.string() ?: throw IllegalStateException("Empty snapshot manifest")
                }
            }
            if (manifestNotModified || manifestRaw == null) return@withContext cached
            val manifest = gson.fromJson(manifestRaw, CommunitySnapshotManifestDto::class.java)
            if (manifest == null || manifest.schema_version != 1) throw IllegalStateException("Invalid snapshot manifest")

            // Save validators only after every object referenced by this manifest is
            // already cached or has been fetched and verified. Otherwise the next run
            // must retry the incomplete snapshot set instead of accepting a 304.
            var allReferencedSnapshotsAvailable = true
            for (window in windows) {
                val reference = manifest.snapshots[window] ?: continue
                if (reference.version.isBlank()) {
                    allReferencedSnapshotsAvailable = false
                    continue
                }
                if (cached[window]?.update_time == reference.update_time) continue

                val snapshotUrl = reference.url?.takeIf { it.isNotBlank() }
                    ?: reference.key.takeIf { it.isNotBlank() }?.let { "$base/$it" }
                if (snapshotUrl == null) {
                    allReferencedSnapshotsAvailable = false
                    continue
                }
                val uri = runCatching { URI(snapshotUrl) }.getOrNull()
                // A public manifest may select paths on its own CDN host, never arbitrary hosts.
                if (uri == null || !uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
                    !uri.host.equals(baseUri.host, true) || uri.userInfo != null) {
                    allReferencedSnapshotsAvailable = false
                    continue
                }

                var snapshotAvailable = false
                runCatching {
                    val request = Request.Builder().url(snapshotUrl).get().build()
                    val raw = client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IllegalStateException("Snapshot HTTP " + response.code)
                        response.body?.string() ?: throw IllegalStateException("Empty snapshot body")
                    }
                    val snapshot = gson.fromJson(raw, CommunitySnapshotDto::class.java)
                    require(snapshot.schema_version == 1 && snapshot.window == window)
                    val current = readSnapshot(window)
                    val incomingTime = runCatching { Instant.parse(snapshot.generated_at) }.getOrNull()
                    val currentTime = current?.let { runCatching { Instant.parse(it.generated_at) }.getOrNull() }
                    if (currentTime == null || (incomingTime != null && incomingTime.isAfter(currentTime))) {
                        CacheStore.writePublic("snapshot:$window", raw)
                    }
                    val persisted = readSnapshot(window)
                    val persistedTime = persisted?.let { runCatching { Instant.parse(it.generated_at) }.getOrNull() }
                    snapshotAvailable = persisted != null && when {
                        incomingTime != null && persistedTime != null -> !persistedTime.isBefore(incomingTime)
                        else -> persisted.update_time == snapshot.update_time
                    }
                    require(snapshotAvailable) { "Snapshot cache write could not be verified" }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    allReferencedSnapshotsAvailable = false
                    Log.w(TAG, "Unable to refresh " + window + " snapshot; retaining cached copy", error)
                }
                if (!snapshotAvailable) allReferencedSnapshotsAvailable = false
            }

            if (!allReferencedSnapshotsAvailable) {
                throw IllegalStateException("Snapshot manifest is incomplete; cached snapshots retained")
            }
            CacheStore.writePublic("snapshot:manifest:etag", manifestEtag.orEmpty())
            CacheStore.writePublic("snapshot:manifest:last-modified", manifestLastModified.orEmpty())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Static snapshot revalidation failed; retaining Room cache", error)
            throw error
        }
        readCachedSnapshots()
    }

    private const val SNAPSHOT_CACHE_TTL_MILLIS = 15 * 60 * 1000L
}
