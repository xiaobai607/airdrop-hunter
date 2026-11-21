package com.airdrophunter.app.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class OkxTicker(
    val instrumentId: String,
    val lastPrice: Double,
    val change24hPercent: Double
) {

    val symbol: String get() = instrumentId.substringBefore("-")
    val displayName: String get() = when (symbol) {
        "BTC" -> "Bitcoin"
        "ETH" -> "Ethereum"
        "SOL" -> "Solana"
        "BNB" -> "BNB"
        "TRX" -> "TRON"
        "XRP" -> "XRP"
        else -> symbol
    }
}

class OkxMarketRepository(
    private val cacheRefresh: CacheRefreshCoordinator = CacheRefreshCoordinator.shared
) {
    private val gson = Gson()

    suspend fun readCachedSpotTickerDetail(symbol: String): OkxTickerDetail? {
        val key = detailCacheKey(symbol)
        val raw = CacheStore.readRemoteCache(key) ?: return null
        return runCatching { gson.fromJson(raw, OkxTickerDetail::class.java) }.getOrNull()
    }

    suspend fun loadSpotTickerDetail(
        symbol: String,
        manual: Boolean = false
    ): CacheRefreshResult<OkxTickerDetail> {
        val key = detailCacheKey(symbol)
        return cacheRefresh.load(
            cacheKey = key,
            source = "okx-spot-detail",
            manual = manual,
            readCache = { readCachedSpotTickerDetail(symbol) },
            fetch = { fetchSpotTickerDetail(symbol) },
            save = { CacheStore.writeRemoteCache(key, gson.toJson(it)) }
        )
    }

    private fun detailCacheKey(symbol: String) = "market-detail:${symbol.uppercase()}"

    suspend fun fetchSpotTickerDetail(symbol: String): OkxTickerDetail = withContext(Dispatchers.IO) {
        val safeSymbol = symbol.uppercase()
        require(safeSymbol in setOf("BTC", "ETH", "SOL", "BNB", "TRX", "XRP", "POL")) { "暂不支持该币种行情" }
        val instrumentId = "$safeSymbol-USDT"
        val connection = (URL("https://www.okx.com/api/v5/market/ticker?instId=$instrumentId")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AirdropHunter-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw ProviderHttpException(code, connection.getHeaderField("Retry-After"), "行情服务暂不可用（HTTP $code）")
            val root = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            if (root.optString("code") != "0") {
                throw IllegalStateException(root.optString("msg").ifBlank { "行情接口返回异常" })
            }
            val item = root.optJSONArray("data")?.optJSONObject(0)
                ?: throw IllegalStateException("没有找到该币种的行情数据")
            fun number(key: String): Double = item.optString(key).toDoubleOrNull()
                ?.takeIf { it.isFinite() } ?: throw IllegalStateException("行情数据缺少 $key")
            val last = number("last")
            val open = number("open24h")
            require(last > 0.0 && open > 0.0) { "行情价格无效" }
            OkxTickerDetail(
                instrumentId = instrumentId,
                lastPrice = last,
                change24hPercent = (last - open) / open * 100.0,
                high24h = number("high24h"),
                low24h = number("low24h"),
                volume24h = number("vol24h"),
                volumeCurrency24h = number("volCcy24h"),
                timestamp = item.optString("ts").toLongOrNull() ?: System.currentTimeMillis()
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchSpotTickers(): List<OkxTicker> = withContext(Dispatchers.IO) {
        val connection = (URL("https://www.okx.com/api/v5/market/tickers?instType=SPOT")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AirdropHunter-Android")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw ProviderHttpException(code, connection.getHeaderField("Retry-After"), "OKX 行情服务暂不可用（HTTP $code）")
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            if (root.optString("code") != "0") {
                throw IllegalStateException(root.optString("msg").ifBlank { "OKX 行情接口返回异常" })
            }

            val wanted = listOf("BTC-USDT", "ETH-USDT", "SOL-USDT", "BNB-USDT", "TRX-USDT", "XRP-USDT")
            val byInstrument = HashMap<String, OkxTicker>()
            val data = root.optJSONArray("data")
                ?: throw IllegalStateException("OKX 行情接口没有返回数据")

            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val instrumentId = item.optString("instId")
                if (instrumentId !in wanted) continue

                val last = item.optString("last").toDoubleOrNull()
                val open24h = item.optString("open24h").toDoubleOrNull()
                if (last == null || open24h == null || last <= 0.0 || open24h <= 0.0 ||
                    !last.isFinite() || !open24h.isFinite()
                ) continue

                val change = (last - open24h) / open24h * 100.0
                byInstrument[instrumentId] = OkxTicker(instrumentId, last, change)
            }

            val missing = wanted.filterNot { byInstrument.containsKey(it) }
            if (missing.isNotEmpty()) {
                throw IllegalStateException("OKX 行情数据不完整，缺少：" + missing.joinToString("、") + "")
            }

            wanted.map { byInstrument.getValue(it) }
        } finally {
            connection.disconnect()
        }
    }
}


data class OkxTickerDetail(
    val instrumentId: String,
    val lastPrice: Double,
    val change24hPercent: Double,
    val high24h: Double,
    val low24h: Double,
    val volume24h: Double,
    val volumeCurrency24h: Double,
    val timestamp: Long
)

