package com.airdrophunter.app.data

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room-backed public campaign cache. Public discovery data is shared between
 * guest and local-only sessions and contains no personal wallet or ledger data.
 */
class AirdropCampaignRepository(
    private val api: ApiService,
    private val gson: Gson = Gson()
) {
    suspend fun readCached(filters: AirdropCampaignFilters = AirdropCampaignFilters()): List<AirdropCampaignDto>? =
        withContext(Dispatchers.IO) {
            val raw = CacheStore.readPublic(filters.cacheKey()) ?: return@withContext null
            runCatching {
                gson.fromJson(raw, AirdropCampaignCacheEnvelope::class.java)
                    ?.takeIf { it.schema_version == 1 }
                    ?.campaigns
                    ?.takeIf { rows -> rows.all { it.id.isNotBlank() && it.name.isNotBlank() } }
            }.getOrNull()
        }

    suspend fun load(filters: AirdropCampaignFilters = AirdropCampaignFilters()): List<AirdropCampaignDto> =
        withContext(Dispatchers.IO) {
            val result = CacheRefreshCoordinator.shared.load(
                cacheKey = filters.cacheKey(),
                source = "public-airdrop-campaigns",
                ttlMillis = CAMPAIGN_TTL_MILLIS,
                readCache = { readCached(filters) },
                fetch = {
                    api.getAirdropCampaigns(
                        tier = filters.tier,
                        chain = filters.chain,
                        status = filters.status,
                        campaignType = filters.campaignType,
                        kyc = filters.kyc,
                        query = filters.query,
                        limit = 100
                    ).also { rows ->
                        require(rows.map { it.id }.distinct().size == rows.size) {
                            "空投活动目录包含重复 ID"
                        }
                        require(rows.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
                            "空投活动目录数据不完整"
                        }
                        require(rows.all { it.tier == "featured" || it.tier == "ordinary" }) {
                            "公开目录返回了未发布的观察或归档记录"
                        }
                    }
                },
                save = { rows ->
                    CacheStore.writePublic(
                        filters.cacheKey(),
                        gson.toJson(AirdropCampaignCacheEnvelope(campaigns = rows))
                    )
                    require(readCached(filters) == rows) { "空投活动缓存写入校验失败" }
                }
            )
            result.data ?: throw IllegalStateException(result.errorMessage ?: "空投活动暂不可用")
        }

    suspend fun refresh(filters: AirdropCampaignFilters = AirdropCampaignFilters()): List<AirdropCampaignDto> =
        withContext(Dispatchers.IO) {
            // Manual refresh still uses the shared bounded retry/cache coordinator.
            val result = CacheRefreshCoordinator.shared.load(
                cacheKey = filters.cacheKey(),
                source = "public-airdrop-campaigns",
                ttlMillis = CAMPAIGN_TTL_MILLIS,
                manual = true,
                readCache = { readCached(filters) },
                fetch = {
                    api.getAirdropCampaigns(
                        tier = filters.tier,
                        chain = filters.chain,
                        status = filters.status,
                        campaignType = filters.campaignType,
                        kyc = filters.kyc,
                        query = filters.query,
                        limit = 100
                    )
                },
                save = { rows ->
                    CacheStore.writePublic(
                        filters.cacheKey(),
                        gson.toJson(AirdropCampaignCacheEnvelope(campaigns = rows))
                    )
                }
            )
            result.data ?: throw IllegalStateException(result.errorMessage ?: "空投活动暂不可用")
        }

    companion object {
        private const val CAMPAIGN_TTL_MILLIS = 6 * 60 * 60 * 1000L
    }
}
