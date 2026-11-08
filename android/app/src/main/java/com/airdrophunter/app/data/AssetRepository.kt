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

object AddressDetector {
    private val evm = Regex("^0x[a-fA-F0-9]{40}$")
    private val tron = Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$")
    private val base58 = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")

    private fun isValidBitcoinBase58(value: String): Boolean = BitcoinAddressValidator.isValid(value)


    fun detect(address: String): DetectedNetwork {
        val value = address.trim()
        return when {
            evm.matches(value) -> DetectedNetwork.EVM
            tron.matches(value) -> DetectedNetwork.TRON
            isValidBitcoinBase58(value) -> DetectedNetwork.BITCOIN
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
    val lastUpdatedEpochSeconds: Long? = null,
    val valuationComplete: Boolean = true,
    val unpricedTokenCount: Int = 0
)

interface AssetRepository {
    suspend fun fetchAssets(address: String, chain: String = ""): Result<UnifiedAssetData>
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

    override suspend fun fetchAssets(address: String, chain: String): Result<UnifiedAssetData> = withContext(Dispatchers.IO) {
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
            DetectedNetwork.EVM -> runCatching { fetchEvmAssets(normalized, chain) }
            DetectedNetwork.SOL -> runCatching { fetchSolanaAssets(normalized) }
            DetectedNetwork.BITCOIN -> runCatching { fetchBitcoinAssets(normalized) }
            DetectedNetwork.UNKNOWN -> Result.failure(IllegalArgumentException("地址格式无法识别，请检查后重试。"))
        }
    }

    private val marketRepository = WalletMarketDataRepository()

    private fun fetchBitcoinAssets(address: String): UnifiedAssetData {
        val balance = marketRepository.fetchBitcoinBalance(address)
        val price = marketRepository.fetchUsdPrice("BTC-USDT")
        val value = price?.multiply(balance)
        return UnifiedAssetData(
            address = address,
            ecosystemName = "Bitcoin",
            totalUsd = value,
            nativeAssetName = "Bitcoin",
            nativeSymbol = "BTC",
            nativeBalance = balance,
            tokenList = listOf(UnifiedToken("native-btc", "Bitcoin", "BTC", balance, value, price)),
            valuationComplete = value != null || balance.signum() == 0,
            unpricedTokenCount = if (value == null && balance.signum() > 0) 1 else 0
        )
    }

    private fun fetchEvmAssets(address: String, selectedChain: String): UnifiedAssetData {
        val network = EvmNetwork.from(selectedChain)
        // EVM addresses are shared across Ethereum-compatible networks. Without
        // an explicit chain selector, the gateway queries Ethereum mainnet.
        val response = rpc(network.rpcUrl, "eth_getBalance", JSONArray().put(address).put("latest"))
        val hexBalance = response.optString("result")
        if (!hexBalance.startsWith("0x")) throw IOException("Ethereum RPC 未返回有效余额。")
        val wei = BigInteger(hexBalance.removePrefix("0x").ifBlank { "0" }, 16)
        val balance = BigDecimal(wei).movePointLeft(18)
        val price = marketRepository.fetchUsdPrice(network.priceInstrument)
        val value = price?.multiply(balance)
        return UnifiedAssetData(
            address = address,
            ecosystemName = "EVM · ${network.label}",
            totalUsd = value,
            nativeAssetName = network.nativeName,
            nativeSymbol = network.nativeSymbol,
            nativeBalance = balance,
            tokenList = listOf(UnifiedToken("native-${network.id}", network.nativeName, network.nativeSymbol, balance, value, price)),
            valuationComplete = value != null || balance.signum() == 0,
            unpricedTokenCount = if (value == null && balance.signum() > 0) 1 else 0
        )
    }

    private data class EvmNetwork(
        val id: String, val label: String, val rpcUrl: String,
        val nativeName: String, val nativeSymbol: String, val priceInstrument: String
    ) {
        companion object {
            fun from(value: String): EvmNetwork = when (value.trim().lowercase()) {
                "bnb chain", "bnb", "bsc" -> EvmNetwork("bsc", "BNB Chain", "https://bsc-rpc.publicnode.com", "BNB", "BNB", "BNB-USDT")
                "polygon", "polygon pos" -> EvmNetwork("polygon", "Polygon", "https://polygon-bor-rpc.publicnode.com", "POL", "POL", "POL-USDT")
                "arbitrum", "arbitrum one" -> EvmNetwork("arbitrum", "Arbitrum", "https://arbitrum-one-rpc.publicnode.com", "Ether", "ETH", "ETH-USDT")
                "optimism" -> EvmNetwork("optimism", "Optimism", "https://optimism-rpc.publicnode.com", "Ether", "ETH", "ETH-USDT")
                "base" -> EvmNetwork("base", "Base", "https://base-rpc.publicnode.com", "Ether", "ETH", "ETH-USDT")
                "avalanche", "avalanche c-chain" -> EvmNetwork("avalanche", "Avalanche", "https://avalanche-c-chain-rpc.publicnode.com", "Avalanche", "AVAX", "AVAX-USDT")
                else -> EvmNetwork("ethereum", "Ethereum", "https://cloudflare-eth.com", "Ether", "ETH", "ETH-USDT")
            }
        }
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
        data class Holding(val mint: String, val quantity: BigDecimal)
        val holdings = mutableListOf<Holding>()
        for (i in 0 until values.length()) {
            val info = values.optJSONObject(i)?.optJSONObject("account")
                ?.optJSONObject("data")?.optJSONObject("parsed")?.optJSONObject("info") ?: continue
            val mint = info.optString("mint")
            val amount = info.optJSONObject("tokenAmount") ?: continue
            val quantity = amount.optString("uiAmountString").toBigDecimalOrNull()
                ?: amount.optDouble("uiAmount").takeIf { it.isFinite() }?.toBigDecimal()
                ?: continue
            if (quantity.signum() > 0 && mint.isNotBlank()) holdings += Holding(mint, quantity)
        }
        val markets = marketRepository.fetchSolanaTokenMarkets(holdings.map { it.mint })
        val solPrice = marketRepository.fetchUsdPrice("SOL-USDT")
        val solValue = solPrice?.multiply(solBalance)
        val tokens = mutableListOf(UnifiedToken("native-sol", "Solana", "SOL", solBalance, solValue, solPrice))
        holdings.forEach { holding ->
            val market = markets[holding.mint]
            val price = market?.priceUsd
            tokens += UnifiedToken(
                id = holding.mint,
                name = market?.name ?: "SPL Token",
                symbol = market?.symbol ?: holding.mint.take(5) + "…",
                quantity = holding.quantity,
                usdValue = price?.multiply(holding.quantity),
                priceUsd = price,
                logoUrl = market?.logoUrl
            )
        }
        val unpriced = tokens.count { it.quantity.signum() > 0 && it.usdValue == null }
        val knownValue = tokens.mapNotNull { it.usdValue }.fold(BigDecimal.ZERO, BigDecimal::add)
        return UnifiedAssetData(
            address = address,
            ecosystemName = "Solana",
            totalUsd = if (tokens.any { it.usdValue != null }) knownValue else null,
            nativeAssetName = "Solana",
            nativeSymbol = "SOL",
            nativeBalance = solBalance,
            tokenList = tokens,
            valuationComplete = unpriced == 0,
            unpricedTokenCount = unpriced
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
