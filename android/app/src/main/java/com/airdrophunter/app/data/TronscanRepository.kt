package com.airdrophunter.app.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit

/**
 * Real, read-only TRON mainnet asset source.
 *
 * TRONSCAN's old /api/account response no longer contains the old
 * trc20token_balances field. Token balances and USD valuations are read from
 * /api/account/token_asset_overview instead.
 *
 * No API key is sent by default. TRONSCAN currently rate-limits anonymous
 * mainnet requests heavily and may return 401 for some endpoints.
 */
class TronscanRepository(
    private val client: OkHttpClient = defaultClient(),
    private val apiKey: String? = null
) {
    suspend fun fetchWalletAssets(address: String): TronWalletAssets = withContext(Dispatchers.IO) {
        val normalizedAddress = address.trim()
        require(TRON_ADDRESS.matches(normalizedAddress)) {
            "TRON 地址格式不正确，请检查是否为 T 开头的主网地址。"
        }

        // The overview endpoint contains the token raw balances, decimals,
        // per-token USD values and total USD value.
        val overview = requestJson(
            path = "/api/account/token_asset_overview",
            address = normalizedAddress
        )

        // Keep /api/account as the primary source for the native balance in SUN.
        // Some API responses/anonymous tiers omit it, so use the overview's
        // native TRX token raw balance only as a real-data fallback.
        val account = runCatching {
            requestJson("/api/account", normalizedAddress)
        }.getOrNull()

        val tokens = parseTokens(overview)
        val nativeToken = tokens.firstOrNull {
            it.tokenId == "_" || it.symbol.equals("TRX", ignoreCase = true)
        }

        val rawSun = account?.let { decimalValue(it, "balance") }
            ?: nativeToken?.rawBalance?.toBigDecimalOrNull()
            ?: throw IOException("TRONSCAN 未返回 TRX 原始余额，无法计算真实 TRX 余额。")

        val trxBalance = rawSun.movePointLeft(TRX_DECIMALS).toPlainString().toBigDecimal()

        val totalUsd = decimalValue(overview, "totalAssetInUsd")
            ?: throw IOException(
                "TRONSCAN 未返回 totalAssetInUsd，无法可靠计算总资产美元估值；未使用估算值替代。"
            )

        TronWalletAssets(
            address = normalizedAddress,
            trxBalance = trxBalance,
            totalUsd = totalUsd,
            tokenList = tokens,
            lastUpdatedEpochSeconds = overview.getAsJsonObject("refreshTimeInfo")
                ?.let { decimalValue(it, "lastUpdateTime")?.toLong() }
        )
    }

    private fun requestJson(path: String, address: String): JsonObject {
        val url = (BASE_URL + path).toHttpUrl().newBuilder()
            .addQueryParameter("address", address)
            .apply {
                if (path.endsWith("token_asset_overview")) {
                    addQueryParameter("sort", "true")
                }
            }
            .build()

        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "AirdropHunter-Android/0.11")

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("TRON-PRO-API-KEY", apiKey)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = when (response.code) {
                    401, 403 -> "TRONSCAN 拒绝了匿名请求（HTTP ${response.code}）。当前主网接口可能要求 API Key。"
                    429 -> "TRONSCAN 请求频率受限（HTTP 429），请稍后再试。"
                    else -> "TRONSCAN 请求失败（HTTP ${response.code}）。"
                }
                throw IOException(detail)
            }
            if (bodyText.isBlank()) throw IOException("TRONSCAN 返回了空响应。")
            val parsed = try {
                JsonParser.parseString(bodyText)
            } catch (error: Exception) {
                throw IOException("TRONSCAN 返回的数据不是有效 JSON。", error)
            }
            if (!parsed.isJsonObject) throw IOException("TRONSCAN 返回的数据格式不正确。")
            return parsed.asJsonObject
        }
    }

    private fun parseTokens(overview: JsonObject): List<TronTokenBalance> {
        val data = overview.get("data")
        if (data == null || !data.isJsonArray) {
            throw IOException("TRONSCAN 资产接口缺少 data 数组，无法读取代币列表。")
        }

        return data.asJsonArray.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val token = element.asJsonObject

            val rawBalance = stringValue(token, "balance") ?: return@mapNotNull null
            val decimals = intValue(token, "tokenDecimal") ?: 0
            if (decimals !in 0..36) return@mapNotNull null

            val raw = rawBalance.toBigDecimalOrNull() ?: return@mapNotNull null
            val quantity = raw.movePointLeft(decimals)
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) return@mapNotNull null

            val symbol = stringValue(token, "tokenAbbr")
                ?: stringValue(token, "tokenName")
                ?: "UNKNOWN"
            val priceUsd = decimalValue(token, "tokenPriceInUsd")
                ?: decimalValue(token, "token_price_in_usd")
            val usdValue = decimalValue(token, "assetInUsd")
                ?: decimalValue(token, "token_value_in_usd")
                ?: decimalValue(token, "amountInUsd")
                ?: decimalValue(token, "value_in_usd")
                ?: priceUsd?.multiply(quantity)

            TronTokenBalance(
                tokenId = stringValue(token, "tokenId").orEmpty(),
                name = stringValue(token, "tokenName") ?: symbol,
                symbol = symbol,
                decimals = decimals,
                rawBalance = rawBalance,
                quantity = quantity,
                priceUsd = priceUsd,
                usdValue = usdValue,
                tokenType = stringValue(token, "tokenType").orEmpty(),
                logoUrl = stringValue(token, "tokenLogo")
            )
        }.sortedByDescending { it.usdValue ?: BigDecimal.ZERO }
    }

    private fun stringValue(json: JsonObject, key: String): String? {
        val value = json.get(key) ?: return null
        if (value.isJsonNull || value.isJsonObject || value.isJsonArray) return null
        return value.asString.takeIf { it.isNotBlank() }
    }

    private fun intValue(json: JsonObject, key: String): Int? =
        stringValue(json, key)?.toIntOrNull()

    private fun decimalValue(json: JsonObject, key: String): BigDecimal? =
        stringValue(json, key)?.toBigDecimalOrNull()

    companion object {
        private const val BASE_URL = "https://apilist.tronscanapi.com"
        private const val TRX_DECIMALS = 6
        private val TRON_ADDRESS = Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$")

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    }
}

data class TronWalletAssets(
    val address: String,
    val trxBalance: BigDecimal,
    val totalUsd: BigDecimal,
    val tokenList: List<TronTokenBalance>,
    val lastUpdatedEpochSeconds: Long? = null
)

data class TronTokenBalance(
    val tokenId: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val rawBalance: String,
    val quantity: BigDecimal,
    val priceUsd: BigDecimal?,
    val usdValue: BigDecimal?,
    val tokenType: String,
    val logoUrl: String?
)
