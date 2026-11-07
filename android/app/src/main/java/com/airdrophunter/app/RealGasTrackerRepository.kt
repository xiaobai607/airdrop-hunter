package com.airdrophunter.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

data class RealGasResult(
    val gasCostUsd: Double,
    val interactionMethod: String,
    val txHash: String
)

/**
 * Reads the latest outgoing transaction for a public wallet address.
 * It never requests a signature and never accesses wallet keys.
 */
class RealGasTrackerRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    // Etherscan API key supplied for this integration. Client-side keys are visible in APKs.
    private val etherscanApiKey = "5MVN2SCP1A2HBFX62FNQTD4RRCSU9DUUY5"

    suspend fun fetchLatestGasCost(address: String): RealGasResult? = withContext(Dispatchers.IO) {
        try {
            when {
                address.startsWith("T") -> fetchTronGas(address)
                address.startsWith("0x", ignoreCase = true) && address.length == 42 ->
                    fetchEvmGas(address)
                else -> null
            }
        } catch (e: Exception) {
            Log.e("GasTracker", "抓取失败", e)
            null
        }
    }

    private fun fetchTronGas(address: String): RealGasResult? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("apilist.tronscanapi.com")
            .addPathSegments("api/transaction")
            .addQueryParameter("sort", "-timestamp")
            .addQueryParameter("count", "true")
            .addQueryParameter("limit", "1")
            .addQueryParameter("fromAddress", address)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            val data = json.optJSONArray("data") ?: return null
            if (data.length() == 0) return null

            val tx = data.optJSONObject(0) ?: return null
            val hash = tx.optString("hash").takeIf { it.isNotBlank() } ?: return null
            val cost = tx.optJSONObject("cost")
            val netFee = cost?.optLong("net_fee", 0L) ?: 0L
            val energyFee = cost?.optLong("energy_fee", 0L) ?: 0L
            val totalSun = BigDecimal.valueOf(netFee).add(BigDecimal.valueOf(energyFee))
            val gasTrx = totalSun.divide(BigDecimal("1000000"), 12, RoundingMode.HALF_UP)
            val trxPrice = getOkxPrice("TRX-USDT") ?: return null
            val usd = gasTrx.multiply(BigDecimal.valueOf(trxPrice))
                .setScale(2, RoundingMode.HALF_UP)

            return RealGasResult(
                gasCostUsd = usd.toDouble(),
                interactionMethod = "TRON Contract",
                txHash = hash
            )
        }
    }

    /**
     * Etherscan V2 is queried for Ethereum mainnet (chainid=1).
     * The transaction's actual gasUsed * gasPrice is converted from wei to ETH,
     * then valued using OKX's public ETH-USDT ticker.
     */
    private fun fetchEvmGas(address: String): RealGasResult? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.etherscan.io")
            .addPathSegments("v2/api")
            .addQueryParameter("chainid", "1")
            .addQueryParameter("module", "account")
            .addQueryParameter("action", "txlist")
            .addQueryParameter("address", address)
            .addQueryParameter("page", "1")
            .addQueryParameter("offset", "1")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("apikey", etherscanApiKey)
            .build()

        val request = Request.Builder().url(url).header("User-Agent", "AirdropHunter/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            if (json.optString("status") != "1") {
                Log.w("GasTracker", "Etherscan returned: ${json.optString("message")}")
                return null
            }
            val transactions = json.optJSONArray("result") ?: return null
            if (transactions.length() == 0) return null

            val tx = transactions.optJSONObject(0) ?: return null
            val hash = tx.optString("hash").takeIf { it.isNotBlank() } ?: return null
            val gasUsed = tx.optString("gasUsed").toBigDecimalOrNull() ?: return null
            val gasPriceWei = tx.optString("gasPrice").toBigDecimalOrNull() ?: return null
            val ethSpent = gasUsed.multiply(gasPriceWei)
                .divide(BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP)
            val ethPrice = getOkxPrice("ETH-USDT") ?: return null
            val usd = ethSpent.multiply(BigDecimal.valueOf(ethPrice))
                .setScale(2, RoundingMode.HALF_UP)

            return RealGasResult(
                gasCostUsd = usd.toDouble(),
                interactionMethod = "EVM Transaction",
                txHash = hash
            )
        }
    }

    private fun getOkxPrice(instId: String): Double? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.okx.com")
            .addPathSegments("api/v5/market/ticker")
            .addQueryParameter("instId", instId)
            .build()
        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string() ?: return null)
                val data = json.optJSONArray("data") ?: return null
                data.optJSONObject(0)?.optString("last")?.toDoubleOrNull()
                    ?.takeIf { it > 0.0 }
            }
        } catch (e: Exception) {
            Log.w("GasTracker", "获取币价失败: $instId", e)
            null
        }
    }
}
