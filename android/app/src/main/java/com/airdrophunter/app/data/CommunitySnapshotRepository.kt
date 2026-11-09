package com.airdrophunter.app.data

import android.util.Log
import com.airdrophunter.app.BuildConfig
import com.google.gson.Gson
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
        val cached = windows.mapNotNull { window -> readSnapshot(window)?.let { window to it } }.toMap()
        val base = BuildConfig.COMMUNITY_SNAPSHOT_BASE_URL.trimEnd('/')
        if (base.isBlank()) return@withContext cached

        try {
            val baseUri = URI(base)
            if (!baseUri.scheme.equals("https", true) || baseUri.host.isNullOrBlank() || baseUri.userInfo != null) {
                return@withContext cached
            }
            val manifestRequest = Request.Builder()
                .url("$base/manifest.json")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            val manifestRaw = client.newCall(manifestRequest).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Snapshot manifest HTTP " + response.code)
                response.body?.string() ?: throw IllegalStateException("Empty snapshot manifest")
            }
            val manifest = gson.fromJson(manifestRaw, CommunitySnapshotManifestDto::class.java)
            if (manifest == null || manifest.schema_version != 1) return@withContext cached

            for (window in windows) {
                val reference = manifest.snapshots[window] ?: continue
                if (reference.version.isBlank() || cached[window]?.let {
                    it.generated_at == reference.version || it.update_time == reference.update_time
                } == true) continue

                val snapshotUrl = reference.url?.takeIf { it.isNotBlank() }
                    ?: reference.key.takeIf { it.isNotBlank() }?.let { "$base/$it" }
                    ?: continue
                val uri = runCatching { URI(snapshotUrl) }.getOrNull() ?: continue
                // A public manifest may select paths on its own CDN host, never arbitrary hosts.
                if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
                    !uri.host.equals(baseUri.host, true) || uri.userInfo != null) continue

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
                }.onFailure { error ->
                    Log.w(TAG, "Unable to refresh " + window + " snapshot; retaining cached copy", error)
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Static snapshot revalidation failed; retaining Room cache", error)
        }
        windows.mapNotNull { window -> readSnapshot(window)?.let { window to it } }.toMap()
    }
}
