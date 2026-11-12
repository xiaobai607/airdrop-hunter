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
    val errorMessage: String? = null,
    val throttled: Boolean = false
)

class WalletAssetRepository(
    private val remote: WalletAssetsRemoteDataSource,
    private val cacheRefresh: CacheRefreshCoordinator = CacheRefreshCoordinator.shared
) {
    suspend fun readCached(walletId: String, chain: String, accessToken: String?): WalletAssetSnapshot? {
        val cached = readAssets(walletId, chain, accessToken) ?: return null
        return WalletAssetSnapshot(
            cached,
            cached.firstOrNull()?.source ?: SOURCE,
            cached.maxOfOrNull { it.fetchedAt } ?: 0L,
            stale = true
        )
    }

    suspend fun refresh(
        walletId: String, address: String, chain: String, accessToken: String?,
        manual: Boolean = false
    ): WalletAssetSnapshot {
        val scopeKey = "${CacheStore.accountScopeKey(accessToken)}:$walletId:${chain.lowercase()}"
        val result = cacheRefresh.load(
            cacheKey = scopeKey, source = WALLET_ASSET_SOURCE, ttlMillis = WALLET_ASSET_TTL_MILLIS,
            manual = manual, readCache = { readCached(walletId, chain, accessToken) },
            fetch = {
                val response = remote.getAssets(chain, address)
                val assets = response.assets.map { dto ->
                    UnifiedAsset(walletId, chain, dto.asset_id, dto.contract_address, dto.symbol, dto.name,
                        dto.decimals, dto.raw_quantity, dto.formatted_quantity,
                        dto.price_usd?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 },
                        dto.usd_value?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 },
                        dto.is_verified, dto.source, response.fetched_at, response.fetched_at)
                }
                WalletAssetSnapshot(assets, assets.firstOrNull()?.source ?: "public chain providers",
                    assets.maxOfOrNull { it.fetchedAt } ?: response.fetched_at, stale = false)
            },
            save = { snapshot -> CacheStore.replaceUnifiedAssetsForToken(
                walletId, chain, snapshot.assets.map { it.toEntity(accessToken) }, accessToken
            ) }
        )
        return (result.data ?: throw IllegalStateException(result.errorMessage ?: "余额暂不可用"))
            .copy(stale = result.stale, errorMessage = result.errorMessage, throttled = result.throttled)
    }

    private suspend fun readAssets(
        walletId: String,
        chain: String,
        accessToken: String?
    ): List<UnifiedAsset>? = CacheStore.readUnifiedAssetsForToken(walletId, chain, accessToken)
        .takeIf { it.isNotEmpty() }
        ?.map { it.toDomain() }


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
        private const val WALLET_ASSET_SOURCE = "private-wallet-assets"
        private const val WALLET_ASSET_TTL_MILLIS = 8 * 60 * 60 * 1000L
    }
}
