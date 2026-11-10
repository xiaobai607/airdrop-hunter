package com.airdrophunter.app.data

interface WalletAssetsRemoteDataSource {
    suspend fun getAssets(chain: String, address: String): WalletAssetsResponseDto
}

class RetrofitWalletAssetsRemoteDataSource(private val api: ApiService) : WalletAssetsRemoteDataSource {
    override suspend fun getAssets(chain: String, address: String): WalletAssetsResponseDto =
        api.getWalletAssets(chain, address, authMode = "anonymous")
}

data class WalletAssetSnapshot(
    val assets: List<UnifiedAsset>,
    val provider: String,
    val fetchedAt: Long,
    val coverageNote: String? = null,
    val stale: Boolean = false,
    val errorMessage: String? = null
)

class WalletAssetRepository(
    private val remote: WalletAssetsRemoteDataSource,
    private val cacheRefresh: CacheRefreshCoordinator = CacheRefreshCoordinator.shared
) {
    suspend fun readCached(walletId: String, chain: String, accessToken: String?): WalletAssetSnapshot? {
        val cached = readAssets(walletId, chain, accessToken) ?: return null
        val metadata = CacheStore.readCacheMetadata(cacheKey(walletId, chain, accessToken), SOURCE)
        return WalletAssetSnapshot(
            cached,
            cached.firstOrNull()?.source ?: SOURCE,
            cached.maxOfOrNull { it.fetchedAt } ?: metadata?.fetchedAt ?: 0L,
            stale = !CacheRefreshPolicy.isFresh(metadata, System.currentTimeMillis())
        )
    }

    suspend fun refresh(
        walletId: String,
        address: String,
        chain: String,
        accessToken: String?,
        manual: Boolean = false
    ): WalletAssetSnapshot {
        val key = cacheKey(walletId, chain, accessToken)
        val result = cacheRefresh.load(
            cacheKey = key,
            source = SOURCE,
            manual = manual,
            readCache = { readAssets(walletId, chain, accessToken) },
            fetch = {
                val response = remote.getAssets(chain, address)
                response.assets.map { dto ->
                    UnifiedAsset(
                        walletId, chain, dto.asset_id, dto.contract_address, dto.symbol, dto.name,
                        dto.decimals, dto.raw_quantity, dto.formatted_quantity,
                        dto.price_usd?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 },
                        dto.usd_value?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 },
                        dto.is_verified, dto.source, response.fetched_at, response.fetched_at
                    )
                }
            },
            save = { rows ->
                CacheStore.replaceUnifiedAssetsForToken(
                    walletId, chain, rows.map { it.toEntity(accessToken) }, accessToken
                )
            }
        )
        val assets = result.data ?: throw IllegalStateException(result.errorMessage ?: "读取资产失败")
        return WalletAssetSnapshot(
            assets = assets,
            provider = assets.firstOrNull()?.source ?: "public chain providers",
            fetchedAt = assets.maxOfOrNull { it.fetchedAt } ?: System.currentTimeMillis(),
            stale = result.stale,
            errorMessage = result.errorMessage ?: if (result.throttled) "刚刚刷新过，请稍后再试" else null
        )
    }

    private suspend fun readAssets(
        walletId: String,
        chain: String,
        accessToken: String?
    ): List<UnifiedAsset>? = CacheStore.readUnifiedAssetsForToken(walletId, chain, accessToken)
        .takeIf { it.isNotEmpty() }
        ?.map { it.toDomain() }

    private fun cacheKey(walletId: String, chain: String, accessToken: String?): String =
        "wallet-assets:${CacheStore.accountScopeKey(accessToken)}:$walletId:${chain.lowercase()}"

    private fun UnifiedAssetEntity.toDomain() = UnifiedAsset(
        walletId, chain, assetId, contractAddress, symbol, name, decimals,
        rawQuantity, formattedQuantity, priceUsd, usdValue, isVerified, source, fetchedAt, updatedAt
    )

    private fun UnifiedAsset.toEntity(accessToken: String?) = UnifiedAssetEntity(
        accountScope = CacheStore.accountScopeKey(accessToken),
        walletId = walletId, chain = chain.lowercase(), assetId = assetId,
        contractAddress = contractAddress, symbol = symbol, name = name, decimals = decimals,
        rawQuantity = rawQuantity, formattedQuantity = formattedQuantity, priceUsd = priceUsd,
        usdValue = usdValue, isVerified = isVerified, source = source,
        fetchedAt = fetchedAt, updatedAt = updatedAt
    )

    companion object {
        private const val SOURCE = "wallet-assets-api"
    }
}
