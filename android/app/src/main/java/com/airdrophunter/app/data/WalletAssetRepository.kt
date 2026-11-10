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

class WalletAssetRepository(private val remote: WalletAssetsRemoteDataSource) {
    suspend fun readCached(walletId: String, chain: String, accessToken: String?): WalletAssetSnapshot? {
        val cached = CacheStore.readUnifiedAssetsForToken(walletId, chain, accessToken).map { it.toDomain() }
        if (cached.isEmpty()) return null
        return WalletAssetSnapshot(
            cached, cached.first().source, cached.maxOfOrNull { it.fetchedAt } ?: 0L, stale = true
        )
    }

    suspend fun refresh(walletId: String, address: String, chain: String, accessToken: String?): WalletAssetSnapshot {
        val cached = CacheStore.readUnifiedAssetsForToken(walletId, chain, accessToken).map { it.toDomain() }
        return try {
            val response = remote.getAssets(chain, address)
            val rows = response.assets.map { dto ->
                UnifiedAsset(
                    walletId, chain, dto.asset_id, dto.contract_address, dto.symbol, dto.name,
                    dto.decimals, dto.raw_quantity, dto.formatted_quantity,
                    dto.price_usd?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 },
                    dto.usd_value?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 },
                    dto.is_verified, dto.source, response.fetched_at, response.fetched_at
                )
            }
            CacheStore.replaceUnifiedAssetsForToken(
                walletId, chain, rows.map { it.toEntity(accessToken) }, accessToken
            )
            WalletAssetSnapshot(rows, response.provider, response.fetched_at, response.coverage_note)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (cached.isNotEmpty()) {
                WalletAssetSnapshot(
                    cached, cached.first().source, cached.maxOfOrNull { it.fetchedAt } ?: 0L,
                    stale = true, errorMessage = error.message ?: "刷新失败，暂时显示上次数据"
                )
            } else throw error
        }
    }

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
}
