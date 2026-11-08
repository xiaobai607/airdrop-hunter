package com.airdrophunter.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

enum class DetectedNetwork(val label: String) {
    EVM("EVM"), TRON("TRON"), SOL("Solana"), BITCOIN("Bitcoin"), UNKNOWN("未知网络")
}

@Deprecated("地址搜索已统一交由 OKLink GlobalSearchScreen 处理。")
object AddressDetector {
    private val evm = Regex("^0x[a-fA-F0-9]{40}$")
    private val tron = Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$")
    private val bitcoinBech32 = Regex("^bc1[ac-hj-np-z02-9]{11,71}$", RegexOption.IGNORE_CASE)
    private val base58 = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")

    private fun isValidBitcoinBase58(value: String): Boolean {
        if (value.length !in 26..35 || value.any { it !in "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz" }) return false
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var number = BigInteger.ZERO
        value.forEach { char -> number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(alphabet.indexOf(char).toLong())) }
        val decoded = number.toByteArray().let { bytes -> if (bytes.firstOrNull() == 0.toByte()) bytes.dropWhile { it == 0.toByte() }.toByteArray() else bytes }
        val leadingZeros = value.takeWhile { it == '1' }.length
        val payload = ByteArray(leadingZeros) + decoded
        if (payload.size != 25 || (payload[0].toInt() and 0xff) !in setOf(0, 5)) return false
        val digest = MessageDigest.getInstance("SHA-256")
        val checksum = digest.digest(digest.digest(payload.copyOfRange(0, 21))).copyOfRange(0, 4)
        return checksum.contentEquals(payload.copyOfRange(21, 25))
    }

    fun detect(address: String): DetectedNetwork {
        val value = address.trim()
        return when {
            evm.matches(value) -> DetectedNetwork.EVM
            tron.matches(value) -> DetectedNetwork.TRON
            bitcoinBech32.matches(value) || isValidBitcoinBase58(value) -> DetectedNetwork.BITCOIN
            base58.matches(value) -> DetectedNetwork.SOL
            else -> DetectedNetwork.UNKNOWN
        }
    }
}

data class UnifiedToken(
    val id: String,
    val name: String,
    val symbol: String,
    val quantity: BigDecimal,
    val usdValue: BigDecimal? = null,
    val priceUsd: BigDecimal? = null,
    val logoUrl: String? = null
)

data class UnifiedAssetData(
    val address: String,
    val ecosystemName: String,
    val totalUsd: BigDecimal?,
    val nativeAssetName: String,
    val nativeSymbol: String,
    val nativeBalance: BigDecimal,
    val tokenList: List<UnifiedToken>,
    val lastUpdatedEpochSeconds: Long? = null
)

interface AssetRepository {
    suspend fun fetchAssets(address: String): Result<UnifiedAssetData>
}

object AssetRepositoryProvider {
    val instance: AssetRepository by lazy { DefaultAssetRepository() }
}

/**
 * One query gateway for temporary checks and saved-wallet details.
 * TRON uses the existing TRONSCAN overview source. EVM and Solana use public
 * read-only RPC endpoints; they do not invent prices or USD valuations.
 */
class DefaultAssetRepository(
    private val tronRepository: TronscanRepository = TronscanRepository(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()
) : AssetRepository {

    override suspend fun fetchAssets(address: String): Result<UnifiedAssetData> = withContext(Dispatchers.IO) {
        val normalized = address.trim()
        when (AddressDetector.detect(normalized)) {
            DetectedNetwork.TRON -> runCatching {
                val tron = tronRepository.fetchWalletAssets(normalized)
                UnifiedAssetData(
                    address = tron.address,
                    ecosystemName = "TRON",
                    totalUsd = tron.totalUsd,
                    nativeAssetName = "TRON",
                    nativeSymbol = "TRX",
                    nativeBalance = tron.trxBalance,
                    tokenList = tron.tokenList.map {
                        UnifiedToken(
                            id = it.tokenId,
                            name = it.name,
                            symbol = it.symbol.uppercase(),
                            quantity = it.quantity,
                            usdValue = it.usdValue,
                            priceUsd = it.priceUsd,
                            logoUrl = it.logoUrl
                        )
                    },
                    lastUpdatedEpochSeconds = tron.lastUpdatedEpochSeconds
                )
            }
            DetectedNetwork.EVM -> runCatching { fetchEvmAssets(normalized) }
            DetectedNetwork.SOL -> runCatching { fetchSolanaAssets(normalized) }
            DetectedNetwork.BITCOIN -> Result.failure(IllegalArgumentException("已识别为 Bitcoin 地址，当前版本暂未接入 BTC 资产查询。"))
            DetectedNetwork.UNKNOWN -> Result.failure(IllegalArgumentException("地址格式无法识别，请检查后重试。"))
        }
    }

    private fun fetchEvmAssets(address: String): UnifiedAssetData {
        // EVM addresses are shared across Ethereum-compatible networks. Without
        // an explicit chain selector, the gateway queries Ethereum mainnet.
        val response = rpc("https://cloudflare-eth.com", "eth_getBalance", JSONArray().put(address).put("latest"))
        val hexBalance = response.optString("result")
        if (!hexBalance.startsWith("0x")) throw IOException("Ethereum RPC 未返回有效余额。")
        val wei = BigInteger(hexBalance.removePrefix("0x").ifBlank { "0" }, 16)
        val balance = BigDecimal(wei).movePointLeft(18)
        return UnifiedAssetData(
            address = address,
            ecosystemName = "EVM · Ethereum",
            totalUsd = null,
            nativeAssetName = "Ethereum",
            nativeSymbol = "ETH",
            nativeBalance = balance,
            tokenList = listOf(UnifiedToken("native-eth", "Ethereum", "ETH", balance))
        )
    }

    private fun fetchSolanaAssets(address: String): UnifiedAssetData {
        val nativeResponse = rpc(
            "https://api.mainnet-beta.solana.com",
            "getBalance",
            JSONArray().put(address).put(JSONObject().put("commitment", "confirmed"))
        )
        val lamports = nativeResponse.optJSONObject("result")?.optLong("value")
            ?: throw IOException("Solana RPC 未返回 SOL 余额。")
        val solBalance = BigDecimal.valueOf(lamports).movePointLeft(9)

        val tokenResponse = rpc(
            "https://api.mainnet-beta.solana.com",
            "getTokenAccountsByOwner",
            JSONArray().put(address)
                .put(JSONObject().put("programId", "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"))
                .put(JSONObject().put("encoding", "jsonParsed").put("commitment", "confirmed"))
        )
        val values = tokenResponse.optJSONObject("result")?.optJSONArray("value") ?: JSONArray()
        val tokens = mutableListOf(
            UnifiedToken("native-sol", "Solana", "SOL", solBalance)
        )
        for (i in 0 until values.length()) {
            val account = values.optJSONObject(i)?.optJSONObject("account")
            val info = account?.optJSONObject("data")?.optJSONObject("parsed")
                ?.optJSONObject("info") ?: continue
            val mint = info.optString("mint")
            val amount = info.optJSONObject("tokenAmount") ?: continue
            val quantity = amount.optString("uiAmountString").toBigDecimalOrNull() ?: continue
            if (quantity.signum() <= 0) continue
            tokens += UnifiedToken(
                id = mint,
                name = "SPL Token",
                symbol = mint.take(5) + "…",
                quantity = quantity
            )
        }
        return UnifiedAssetData(
            address = address,
            ecosystemName = "Solana",
            totalUsd = null,
            nativeAssetName = "Solana",
            nativeSymbol = "SOL",
            nativeBalance = solBalance,
            tokenList = tokens
        )
    }

    private fun rpc(endpoint: String, method: String, params: JSONArray): JSONObject {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", method)
            .put("params", params)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(endpoint).post(body).build()
        return httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("链上 RPC 请求失败（HTTP ${response.code}）。")
            val json = JSONObject(text)
            if (json.has("error")) {
                val message = json.optJSONObject("error")?.optString("message")
                throw IOException(message?.takeIf { it.isNotBlank() } ?: "链上 RPC 返回错误。")
            }
            json
        }
    }
}
