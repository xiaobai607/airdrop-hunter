package com.airdrophunter.app.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * Optional market-price source for TRX and USDT.
 *
 * CoinGecko Demo API is free, but current documentation requires a Demo API key
 * for authenticated requests. Keep the key out of source control; pass it from
 * a secure backend or a local developer configuration.
 */
class CoinGeckoPriceRepository(
    private val demoApiKey: String? = null,
    private val client: OkHttpClient = defaultClient()
) {
    suspend fun fetchTronAndTetherPrices(): Map<String, CoinMarketPrice> =
        withContext(Dispatchers.IO) {
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("ids", "tron,tether")
                .addQueryParameter("vs_currencies", "cny,usd")
                .addQueryParameter("include_last_updated_at", "true")
                .build()

            val requestBuilder = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")

            if (!demoApiKey.isNullOrBlank()) {
                requestBuilder.header("x-cg-demo-api-key", demoApiKey)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        401, 403 -> "CoinGecko 拒绝请求，请检查免费 Demo API Key。"
                        429 -> "CoinGecko 请求次数已达限制，请稍后重试。"
                        else -> "CoinGecko 行情请求失败（HTTP ${response.code}）。"
                    }
                    throw IOException(message)
                }
                val root = try {
                    JsonParser.parseString(body).asJsonObject
                } catch (error: Exception) {
                    throw IOException("CoinGecko 返回的数据格式不正确。", error)
                }

                buildMap {
                    listOf("tron", "tether").forEach { coinId ->
                        val item = root.getAsJsonObject(coinId) ?: return@forEach
                        val usd = item.get("usd")?.takeUnless { it.isJsonNull }?.asString
                            ?.toBigDecimalOrNull()
                        val cny = item.get("cny")?.takeUnless { it.isJsonNull }?.asString
                            ?.toBigDecimalOrNull()
                        if (usd != null || cny != null) {
                            put(
                                coinId,
                                CoinMarketPrice(
                                    usd = usd,
                                    cny = cny,
                                    lastUpdatedEpochSeconds = item.get("last_updated_at")
                                        ?.takeUnless { it.isJsonNull }?.asLong
                                )
                            )
                        }
                    }
                }
            }
        }

    companion object {
        private const val BASE_URL = "https://api.coingecko.com/api/v3/simple/price"

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

data class CoinMarketPrice(
    val usd: BigDecimal?,
    val cny: BigDecimal?,
    val lastUpdatedEpochSeconds: Long?
)

object CryptoFiatConverter {
    fun toUsd(amount: BigDecimal, unitPriceUsd: BigDecimal): BigDecimal =
        amount.multiply(unitPriceUsd)

    fun toCny(amount: BigDecimal, unitPriceCny: BigDecimal): BigDecimal =
        amount.multiply(unitPriceCny)
}
