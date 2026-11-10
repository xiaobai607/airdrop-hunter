package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AirdropSite(
    val id: String,
    val name: String,
    val url: String,
    val icon: String,
    val clickCount: Int = 0
) {
    val domain: String
        get() = runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
}

object AirdropSiteStore {
    private val gson = Gson()
    private val listType = object : TypeToken<List<AirdropSite>>() {}.type

    val defaults = listOf(
        AirdropSite("airdrops-io", "Airdrops.io", "https://airdrops.io/", "airdrops.io"),
        AirdropSite("defillama", "DeFiLlama", "https://defillama.com/airdrops", "defillama.com"),
        AirdropSite("dropstab", "DropsTab", "https://dropstab.com/", "dropstab.com"),
        AirdropSite("coinmarketcap", "CoinMarketCap", "https://coinmarketcap.com/airdrop/", "coinmarketcap.com"),
        AirdropSite("rootdata", "RootData", "https://www.rootdata.com/", "rootdata.com")
    )

    suspend fun load(): List<AirdropSite> {
        val saved = com.airdrophunter.app.data.CacheStore.readLocal("airdrop_sites")
            ?.let { runCatching { gson.fromJson<List<AirdropSite>>(it, listType) }.getOrNull() }
        val counts = saved.orEmpty().associate { it.id to it.clickCount }
        return defaults.map { it.copy(clickCount = counts[it.id] ?: 0) }
    }

    suspend fun recordClick(site: AirdropSite): List<AirdropSite> {
        val updated = load().map { if (it.id == site.id) it.copy(clickCount = it.clickCount + 1) else it }
        CacheStore.writeLocal("airdrop_sites", gson.toJson(updated))
        return updated
    }
}
