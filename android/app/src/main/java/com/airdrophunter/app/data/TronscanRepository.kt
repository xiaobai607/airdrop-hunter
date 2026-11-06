package com.airdrophunter.app.data

import com.google.gson.JsonObject
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
 * Read-only TRONSCAN asset repository.
 * The token asset overview endpoint is the single source of truth.
 * No synthetic balances, prices, or totals are generated.
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

        val overview = requestOverview(normalizedAddress)
        val tokens = parseTokens(overview)
        val nativeRawBalance = tokens.firstOrNull {
            it.tokenId == "_" || it.symbol.equals("TRX", ignoreCase = true)
        }?.rawBalance ?: findNativeTokenRawBalance(overview)
            ?: throw IOException("TRONSCAN 未返回 TRX 原始余额，无法计算真实 TRX 余额。")

        val totalUsd = decimalValue(overview, "totalAssetInUsd")
            ?: throw IOException("TRONSCAN 未返回真实总资产估值。")

        TronWalletAssets(
            address = normalizedAddress,
            trxBalance = nativeRawBalance.toBigDecimal().movePointLeft(TRX_DECIMALS),
            totalUsd = totalUsd,
            tokenList = tokens,
            lastUpdatedEpochSeconds = overview.getAsJsonObject("refreshTimeInfo")
                ?.let { decimalValue(it, "lastUpdateTime")?.toLong() }
        )
    }

    private fun requestOverview(address: String): JsonObject {
        val url = (BASE_URL + "/api/account/token_asset_overview").toHttpUrl()
            .newBuilder()
            .addQueryParameter("address", address)
            .addQueryParameter("sort", "true")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "AirdropHunter-Android/0.12")
            .apply {
                if (!apiKey.isNullOrBlank()) header("TRON-PRO-API-KEY", apiKey)
            }
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401, 403 -> "TRONSCAN 当前要求 API Key，暂时无法匿名查询。"
                    429 -> "TRONSCAN 请求频率受限，请稍后再试。"
                    else -> "TRONSCAN 请求失败（HTTP ${response.code}）。"
                }
                throw IOException(message)
            }
            if (body.isBlank()) throw IOException("TRONSCAN 返回了空响应。")
            val parsed = try {
                JsonParser.parseString(body)
            } catch (error: Exception) {
                throw IOException("TRONSCAN 返回的数据不是有效 JSON。", error)
            }
            if (!parsed.isJsonObject) throw IOException("TRONSCAN 返回的数据格式不正确。")
            parsed.asJsonObject
        }
    }

    private fun parseTokens(overview: JsonObject): List<TronTokenBalance> {
        val data = overview.get("data")
        if (data == null || !data.isJsonArray) {
            throw IOException("TRONSCAN 资产接口缺少代币数据。")
        }

        return data.asJsonArray.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val token = element.asJsonObject
            val rawBalance = stringValue(token, "balance") ?: return@mapNotNull null
            val decimals = intValue(token, "tokenDecimal") ?: return@mapNotNull null
            if (decimals !in 0..36) return@mapNotNull null

            val raw = rawBalance.toBigDecimalOrNull() ?: return@mapNotNull null
            val quantity = raw.movePointLeft(decimals)
            if (quantity.signum() <= 0) return@mapNotNull null

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

    private fun findNativeTokenRawBalance(overview: JsonObject): String? {
        val data = overview.get("data")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        return data.firstNotNullOfOrNull { element ->
            if (!element.isJsonObject) return@firstNotNullOfOrNull null
            val token = element.asJsonObject
            val id = stringValue(token, "tokenId")
            val symbol = stringValue(token, "tokenAbbr")
            if (id != "_" && !symbol.equals("TRX", ignoreCase = true)) {
                return@firstNotNullOfOrNull null
            }
            stringValue(token, "balance")
        }
    }

    private fun stringValue(json: JsonObject, key: String): String? {
        val value = json.get(key) ?: return null
        if (value.isJsonNull || value.isJsonObject || value.isJsonArray) return null
        return runCatching { value.asString }.getOrNull()?.takeIf { it.isNotBlank() }
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
