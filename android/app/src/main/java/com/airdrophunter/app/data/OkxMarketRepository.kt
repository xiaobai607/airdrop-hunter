package com.airdrophunter.app.data

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
        "XRP" -> "XRP"
        else -> symbol
    }
}

class OkxMarketRepository {
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
                throw IllegalStateException("OKX 行情服务暂不可用（HTTP $code）")
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            if (root.optString("code") != "0") {
                throw IllegalStateException(root.optString("msg").ifBlank { "OKX 行情接口返回异常" })
            }

            val wanted = listOf("BTC-USDT", "ETH-USDT", "SOL-USDT", "BNB-USDT", "XRP-USDT")
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
