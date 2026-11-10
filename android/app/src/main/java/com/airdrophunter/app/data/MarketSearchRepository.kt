package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MarketSearchRepository(private val okxRepository: OkxMarketRepository) {
    suspend fun searchLocalCoins(query: String): List<MarketCoin> {
        ensureSeedCoins()
        return CacheStore.readMarketCoins().map { it.toDomain() }.filter { it.matchesSearch(query) }
    }

    suspend fun searchRemoteCoins(query: String): List<MarketCoin> {
        val response = ApiClient.service.getMarketPrices()
        val fetchedAt = System.currentTimeMillis()
        val rows = response.prices.filterNot { it.unavailable }.mapNotNull { price ->
            val symbol = price.symbol.trim().uppercase()
            val name = price.name.trim()
            if (symbol !in SUPPORTED_MARKET_SYMBOLS || name.isBlank()) return@mapNotNull null
            MarketCoinEntity(
                sourceId = "coinpaprika:${price.coinpaprika_id}",
                source = response.provider.ifBlank { "coinpaprika" },
                symbol = symbol,
                name = name,
                priceUsd = price.price_usd?.takeIf { it.isFinite() && it >= 0.0 },
                change24hPercent = price.change_24h?.takeIf { it.isFinite() },
                marketCapUsd = price.market_cap_usd?.takeIf { it.isFinite() && it >= 0.0 },
                rank = price.rank?.takeIf { it > 0 },
                contractAddress = null,
                network = null,
                logoUrl = null,
                fetchedAt = fetchedAt
            )
        }
        if (rows.isNotEmpty()) CacheStore.replaceMarketCoins(rows)
        return rows.map { it.toDomain() }.filter { it.matchesSearch(query) }
    }

    suspend fun loadHotCoins(): List<HotCoin> {
        val cached = CacheStore.readHotCoins(HOT_COIN_CACHE_KEY).map { it.toDomain() }
        return cached.ifEmpty { refreshHotCoins() }
    }

    suspend fun refreshHotCoins(): List<HotCoin> {
        val fetchedAt = System.currentTimeMillis()
        val rows = okxRepository.fetchSpotTickers().mapIndexed { index, ticker ->
            HotCoinCacheEntity(
                cacheKey = HOT_COIN_CACHE_KEY,
                symbol = ticker.symbol,
                name = ticker.displayName,
                lastPrice = ticker.lastPrice,
                change24hPercent = ticker.change24hPercent,
                logoUrl = null,
                contractAddress = null,
                network = null,
                sortOrder = index,
                fetchedAt = fetchedAt
            )
        }
        CacheStore.replaceHotCoins(HOT_COIN_CACHE_KEY, rows)
        return rows.map { it.toDomain() }
    }

    suspend fun searchLocalDapps(query: String): List<CommunityProjectCatalogDto> {
        val raw = CacheStore.readPublic(DAPP_CATALOG_CACHE_KEY) ?: return emptyList()
        val type = object : TypeToken<List<CommunityProjectCatalogDto>>() {}.type
        val entries = runCatching { Gson().fromJson<List<CommunityProjectCatalogDto>>(raw, type) }.getOrNull().orEmpty()
        return entries.filter { it.matchesSearch(query) }
    }

    suspend fun searchRemoteDapps(query: String): List<CommunityProjectCatalogDto> {
        val entries = ApiClient.service.getCommunityCatalog()
        CacheStore.writePublic(DAPP_CATALOG_CACHE_KEY, Gson().toJson(entries))
        return entries.filter { it.matchesSearch(query) }
    }

    private suspend fun ensureSeedCoins() {
        if (CacheStore.readMarketCoins().isNotEmpty()) return
        val now = System.currentTimeMillis()
        val seeds = listOf(
            "btc-bitcoin" to ("BTC" to "Bitcoin"),
            "eth-ethereum" to ("ETH" to "Ethereum"),
            "sol-solana" to ("SOL" to "Solana"),
            "bnb-binance-coin" to ("BNB" to "BNB"),
            "xrp-xrp" to ("XRP" to "XRP")
        ).map { (id, identity) ->
            MarketCoinEntity("coinpaprika:$id", "seed", identity.first, identity.second, null, null, null, null, null, null, null, now)
        }
        CacheStore.replaceMarketCoins(seeds)
    }

    private fun MarketCoinEntity.toDomain() = MarketCoin(
        sourceId, source, symbol, name, priceUsd, change24hPercent, marketCapUsd, rank,
        contractAddress, network, logoUrl, fetchedAt
    )

    private fun HotCoinCacheEntity.toDomain() = HotCoin(
        symbol, name, lastPrice, change24hPercent, logoUrl, contractAddress, network, fetchedAt
    )

    companion object {
        private val SUPPORTED_MARKET_SYMBOLS = setOf("BTC", "ETH", "SOL", "BNB", "XRP")
        private const val HOT_COIN_CACHE_KEY = "global-search-hot-coins"
        private const val DAPP_CATALOG_CACHE_KEY = "global-search-dapp-catalog"
    }
}
