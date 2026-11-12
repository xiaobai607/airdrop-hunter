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

    suspend fun isExplicitlyDelisted(projectOfficialId: String?): Boolean = withContext(Dispatchers.IO) {
        isCommunityProjectDelisted(projectOfficialId, readCatalog().orEmpty())
    }

    suspend fun refreshCatalog(): List<CommunityProjectCatalogDto> = refreshCatalogWithStatus().data
        ?: throw IllegalStateException("社区目录暂不可用")

    suspend fun refreshCatalogWithStatus(): CacheRefreshResult<List<CommunityProjectCatalogDto>> = withContext(Dispatchers.IO) {
        val result = CacheRefreshCoordinator.shared.load(
            cacheKey = CACHE_KEY,
            source = "official-community-directory",
            ttlMillis = DIRECTORY_TTL_MILLIS,
            readCache = { readCatalog() },
            fetch = {
                val incoming = ApiClient.service.getCommunityCatalog()
                require(incoming.map { it.project_official_id }.distinct().size == incoming.size) {
                    "社区目录包含重复的官方项目 ID"
                }
                require(incoming.all { it.project_official_id.isNotBlank() && it.name.isNotBlank() }) {
                    "社区目录数据不完整"
                }
                mergeCommunityCatalog(readCatalog().orEmpty(), incoming)
            },
            save = { merged ->
                CacheStore.writePublic(CACHE_KEY, gson.toJson(CommunityCatalogCacheEnvelope(projects = merged)))
                require(readCatalog() == merged) { "社区目录缓存写入校验失败" }
            }
        )
        result
    }

    private const val DIRECTORY_TTL_MILLIS = 6 * 60 * 60 * 1000L
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

internal fun isCommunityProjectDelisted(projectOfficialId: String?, directory: List<CommunityProjectCatalogDto>): Boolean {
    if (projectOfficialId.isNullOrBlank()) return false
    return directory.firstOrNull { it.project_official_id == projectOfficialId }?.is_active == false
}
