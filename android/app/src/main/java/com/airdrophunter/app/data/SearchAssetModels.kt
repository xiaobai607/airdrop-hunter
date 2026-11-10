package com.airdrophunter.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "market_coins", indices = [Index(value = ["symbol"]), Index(value = ["name"])])
data class MarketCoinEntity(
    @androidx.room.PrimaryKey val sourceId: String,
    val source: String,
    val symbol: String,
    val name: String,
    val priceUsd: Double?,
    val change24hPercent: Double?,
    val marketCapUsd: Double?,
    val rank: Int?,
    val contractAddress: String?,
    val network: String?,
    val logoUrl: String?,
    val fetchedAt: Long
)

@Entity(tableName = "hot_coin_cache", primaryKeys = ["cacheKey", "symbol"], indices = [Index(value = ["cacheKey", "sortOrder"])])
data class HotCoinCacheEntity(
    val cacheKey: String,
    val symbol: String,
    val name: String,
    val lastPrice: Double,
    val change24hPercent: Double,
    val logoUrl: String?,
    val contractAddress: String?,
    val network: String?,
    val sortOrder: Int,
    val fetchedAt: Long
)

@Entity(tableName = "search_history", primaryKeys = ["accountScope", "category", "normalizedQuery"], indices = [Index(value = ["accountScope", "lastUsedAt"])])
data class SearchHistoryEntity(
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String,
    val category: String,
    val normalizedQuery: String,
    val query: String,
    val lastUsedAt: Long
)

@Entity(tableName = "unified_assets", primaryKeys = ["accountScope", "walletId", "chain", "assetId"], indices = [Index(value = ["accountScope", "walletId", "chain", "usdValue"])])
data class UnifiedAssetEntity(
    @ColumnInfo(defaultValue = "'guest'") val accountScope: String,
    val walletId: String,
    val chain: String,
    val assetId: String,
    val contractAddress: String?,
    val symbol: String,
    val name: String,
    val decimals: Int?,
    val rawQuantity: String,
    val formattedQuantity: String,
    val priceUsd: Double?,
    val usdValue: Double?,
    val isVerified: Boolean,
    val source: String,
    val fetchedAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "cache_metadata", primaryKeys = ["cacheKey", "source"])
data class CacheMetadataEntity(
    val cacheKey: String,
    val source: String,
    val fetchedAt: Long?,
    val expiresAt: Long?,
    val ttlMillis: Long,
    val refreshStatus: String,
    val lastError: String?,
    val lastManualRefreshAt: Long?
)

data class MarketCoin(
    val sourceId: String, val source: String, val symbol: String, val name: String,
    val priceUsd: Double?, val change24hPercent: Double?, val marketCapUsd: Double?,
    val rank: Int?, val contractAddress: String? = null, val network: String? = null,
    val logoUrl: String? = null, val fetchedAt: Long
)

data class HotCoin(
    val symbol: String, val name: String, val lastPrice: Double, val change24hPercent: Double,
    val logoUrl: String? = null, val contractAddress: String? = null,
    val network: String? = null, val fetchedAt: Long
)

data class SearchHistory(
    val category: String, val normalizedQuery: String, val query: String, val lastUsedAt: Long
)

data class UnifiedAsset(
    val walletId: String, val chain: String, val assetId: String, val contractAddress: String?,
    val symbol: String, val name: String, val decimals: Int?, val rawQuantity: String,
    val formattedQuantity: String, val priceUsd: Double?, val usdValue: Double?,
    val isVerified: Boolean, val source: String, val fetchedAt: Long, val updatedAt: Long
)
