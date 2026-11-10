package com.airdrophunter.app.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CommunityCatalogCacheEnvelope(
    val schema_version: Int = 1,
    val projects: List<CommunityProjectCatalogDto> = emptyList()
)

/**
 * Room-first cache for the official community directory.
 *
 * The catalog is intentionally independent from CDN metric snapshots. Only an
 * explicit is_active=false from the API marks a known project as delisted.
 * Missing IDs and failed requests never imply deletion or deactivation.
 */
object CommunityCatalogRepository {
    private const val CACHE_KEY = "catalog:directory:v1"
    private val gson = Gson()

    suspend fun readCatalog(): List<CommunityProjectCatalogDto>? = withContext(Dispatchers.IO) {
        val raw = CacheStore.readPublic(CACHE_KEY) ?: return@withContext null
        runCatching {
            gson.fromJson(raw, CommunityCatalogCacheEnvelope::class.java)
                ?.takeIf { it.schema_version == 1 }
                ?.projects
                ?.takeIf { rows -> rows.all { it.project_official_id.isNotBlank() && it.name.isNotBlank() } }
        }.getOrNull()
    }

    suspend fun refreshCatalog(): List<CommunityProjectCatalogDto> = withContext(Dispatchers.IO) {
        val cached = readCatalog().orEmpty()
        val incoming = ApiClient.service.getCommunityCatalog()
        require(incoming.map { it.project_official_id }.distinct().size == incoming.size) {
            "社区目录包含重复的官方项目 ID"
        }
        require(incoming.all { it.project_official_id.isNotBlank() && it.name.isNotBlank() }) {
            "社区目录数据不完整"
        }

        // Do not infer a delisting from an omitted ID. The server currently uses
        // soft-deactivation, so only explicit false values can change local state.
        val merged = mergeCommunityCatalog(cached, incoming)
        val payload = gson.toJson(CommunityCatalogCacheEnvelope(projects = merged))
        CacheStore.writePublic(CACHE_KEY, payload)
        val persisted = readCatalog()
        require(persisted != null && persisted == merged) { "社区目录缓存写入校验失败" }
        merged
    }
}

internal fun mergeCommunityCatalog(
    cached: List<CommunityProjectCatalogDto>,
    incoming: List<CommunityProjectCatalogDto>
): List<CommunityProjectCatalogDto> {
    val merged = LinkedHashMap<String, CommunityProjectCatalogDto>()
    cached.forEach { merged[it.project_official_id] = it }
    incoming.forEach { merged[it.project_official_id] = it }
    return merged.values.sortedWith(compareBy<CommunityProjectCatalogDto> { it.sort_order }.thenBy { it.name.lowercase() })
}
