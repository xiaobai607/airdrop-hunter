package com.airdrophunter.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

data class TokenMarketInfo(
    val priceUsd: BigDecimal,
    val name: String,
    val symbol: String,
    val logoUrl: String?
)

/**
 * Public, read-only market data. Missing prices stay null; no synthetic quotes
 * are generated. DEX prices are selected from the most liquid matching pool.
 */
class WalletMarketDataRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    fun fetchUsdPrice(instrument: String): BigDecimal? = runCatching {
        val root = getObject("https://www.okx.com/api/v5/market/ticker?instId=" + instrument)
        val ticker = root.optJSONArray("data")?.optJSONObject(0)
            ?: throw IOException("OKX 未返回行情。")
        ticker.optString("last").toBigDecimalOrNull()
            ?: throw IOException("OKX 未返回有效价格。")
    }.getOrNull()

    fun fetch24hChange(instrument: String): BigDecimal? = runCatching {
        val root = getObject("https://www.okx.com/api/v5/market/ticker?instId=" + instrument)
        val ticker = root.optJSONArray("data")?.optJSONObject(0) ?: return@runCatching null
        val last = ticker.optString("last").toBigDecimalOrNull() ?: return@runCatching null
        val open = ticker.optString("open24h").toBigDecimalOrNull() ?: return@runCatching null
        if (open.signum() == 0) null else last.subtract(open)
            .divide(open, 8, java.math.RoundingMode.HALF_UP).multiply(BigDecimal(100))
    }.getOrNull()

    fun fetchBitcoinBalance(address: String): BigDecimal {
        val root = getObject("https://blockstream.info/api/address/" + address)
        val chain = root.optJSONObject("chain_stats") ?: throw IOException("Bitcoin 浏览器未返回链上统计。")
        val mempool = root.optJSONObject("mempool_stats") ?: JSONObject()
        val funded = chain.optLong("funded_txo_sum") + mempool.optLong("funded_txo_sum")
        val spent = chain.optLong("spent_txo_sum") + mempool.optLong("spent_txo_sum")
        return BigDecimal.valueOf((funded - spent).coerceAtLeast(0L)).movePointLeft(8)
    }

    fun fetchSolanaTokenMarkets(mints: List<String>): Map<String, TokenMarketInfo> {
        if (mints.isEmpty()) return emptyMap()
        val output = mutableMapOf<String, Pair<Double, TokenMarketInfo>>()
        mints.distinct().chunked(30).forEach { batch ->
            runCatching {
                val root = getArray("https://api.dexscreener.com/tokens/v1/solana/" + batch.joinToString(","))
                for (index in 0 until root.length()) {
                    val pair = root.optJSONObject(index) ?: continue
                    if (!pair.optString("chainId").equals("solana", true)) continue
                    val base = pair.optJSONObject("baseToken") ?: continue
                    val mint = base.optString("address")
                    if (mint !in batch) continue
                    val price = pair.optString("priceUsd").toBigDecimalOrNull() ?: continue
                    if (price.signum() <= 0) continue
                    val liquidity = pair.optJSONObject("liquidity")?.optDouble("usd") ?: 0.0
                    val info = pair.optJSONObject("info")
                    val token = TokenMarketInfo(
                        priceUsd = price,
                        name = base.optString("name").ifBlank { "SPL Token" },
                        symbol = base.optString("symbol").ifBlank { mint.take(5) + "…" },
                        logoUrl = info?.optString("imageUrl")?.takeIf { it.startsWith("https://") }
                    )
                    val previous = output[mint]
                    if (previous == null || liquidity > previous.first) output[mint] = liquidity to token
                }
            }
        }
        return output.mapValues { it.value.second }
    }

    private fun getObject(url: String): JSONObject {
        val body = request(url)
        return JSONObject(body)
    }

    private fun getArray(url: String): JSONArray = JSONArray(request(url))

    private fun request(url: String): String {
        val request = Request.Builder().url(url).get()
            .header("Accept", "application/json")
            .header("User-Agent", "AirdropHunter-Android/1.0")
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("行情数据请求失败（HTTP " + response.code + "）。")
            if (body.isBlank()) throw IOException("行情数据源返回空响应。")
            body
        }
    }
}
