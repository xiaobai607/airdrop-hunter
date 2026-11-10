package com.airdrophunter.app.data

data class UnifiedAssetDistributionSlice(val assetId: String, val label: String, val usdValue: Double)

private fun UnifiedAsset.isDisplayable(): Boolean =
    isVerified && priceUsd?.let { it.isFinite() && it > 0.0 } == true &&
        usdValue?.let { it.isFinite() && it >= 0.10 } == true

fun visibleUnifiedAssets(assets: List<UnifiedAsset>): List<UnifiedAsset> =
    assets.filter { it.isDisplayable() }.sortedByDescending { it.usdValue ?: 0.0 }

fun hiddenUnifiedAssets(assets: List<UnifiedAsset>): List<UnifiedAsset> =
    assets.filterNot { it.isDisplayable() }

fun unifiedAssetDistribution(assets: List<UnifiedAsset>, chain: String): List<UnifiedAssetDistributionSlice> {
    if (chain.trim().lowercase() in setOf("bitcoin", "btc")) return emptyList()
    val priced = assets.filter { asset ->
        asset.isVerified && asset.priceUsd?.let { it.isFinite() && it > 0.0 } == true &&
            asset.usdValue?.let { it.isFinite() && it > 0.0 } == true
    }.sortedByDescending { it.usdValue ?: 0.0 }
    if (priced.isEmpty()) return emptyList()
    val top = priced.take(5).map {
        UnifiedAssetDistributionSlice(it.assetId, it.symbol.ifBlank { it.name }, it.usdValue!!)
    }
    val others = priced.drop(5).sumOf { it.usdValue ?: 0.0 }
    return if (others > 0.0 && others.isFinite()) top + UnifiedAssetDistributionSlice("others", "Others", others) else top
}
