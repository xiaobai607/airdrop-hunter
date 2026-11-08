package com.airdrophunter.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

enum class DetectedNetwork(val label: String) {
    EVM("EVM"), TRON("TRON"), SOL("Solana"), BITCOIN("Bitcoin"), UNKNOWN("未知网络")
}

@Deprecated("地址搜索已统一交由 OKLink GlobalSearchScreen 处理。")
object AddressDetector {
    private val evm = Regex("^0x[a-fA-F0-9]{40}$")
    private val tron = Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$")
    fun detect(address: String): DetectedNetwork {
        val value = address.trim()
        return when {
            evm.matches(value) -> DetectedNetwork.EVM
            tron.matches(value) -> DetectedNetwork.TRON
            Base58Address.isBitcoinSegwitAddress(value) ||
                Base58Address.isBase58Check(value, setOf(0x00, 0x05)) -> DetectedNetwork.BITCOIN
            Base58Address.is32BytePublicKey(value) -> DetectedNetwork.SOL
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


internal data class EvmAssetNetwork(
    val id: String,
    val name: String,
    val symbol: String,
    val rpcUrl: String
)

internal val EVM_ASSET_NETWORKS = listOf(
    EvmAssetNetwork("ethereum", "Ethereum", "ETH", "https://cloudflare-eth.com"),
    EvmAssetNetwork("bsc", "BNB Smart Chain", "BNB", "https://bsc-dataseed.binance.org"),
    EvmAssetNetwork("polygon", "Polygon PoS", "POL", "https://polygon-rpc.com"),
    EvmAssetNetwork("arbitrum", "Arbitrum One", "ETH", "https://arb1.arbitrum.io/rpc"),
    EvmAssetNetwork("optimism", "OP Mainnet", "ETH", "https://mainnet.optimism.io"),
    EvmAssetNetwork("base", "Base", "ETH", "https://mainnet.base.org"),
    EvmAssetNetwork("avalanche", "Avalanche C-Chain", "AVAX", "https://api.avax.network/ext/bc/C/rpc")
)

internal fun parseEvmNativeBalance(hexBalance: String): BigDecimal {
    if (!hexBalance.startsWith("0x")) throw IOException("EVM RPC 未返回有效余额。")
    val digits = hexBalance.removePrefix("0x").ifBlank { "0" }
    val wei = try {
        BigInteger(digits, 16)
    } catch (_: NumberFormatException) {
        throw IOException("EVM RPC 返回了无效的余额格式。")
    }
    if (wei.signum() < 0) throw IOException("EVM RPC 返回了负数余额。")
    return BigDecimal(wei).movePointLeft(18).stripTrailingZeros()
}

internal fun buildEvmAssetData(
    address: String,
    balances: Map<String, BigDecimal>
): UnifiedAssetData {
    val ordered = EVM_ASSET_NETWORKS.mapNotNull { network ->
        balances[network.id]?.let { network to it }
    }
    require(ordered.isNotEmpty()) { "所有 EVM 网络均无法读取余额。" }
    val primary = ordered.firstOrNull { it.first.id == "ethereum" } ?: ordered.first()
    val tokens = ordered.map { (network, balance) ->
        UnifiedToken(
            id = "native-${network.id}",
            name = "${network.name} 主币",
            symbol = network.symbol,
            quantity = balance
        )
    }
    return UnifiedAssetData(
        address = address,
        ecosystemName = "EVM 原生币 · ${ordered.size}/${EVM_ASSET_NETWORKS.size} 网络可用（ERC-20 未读取）",
        totalUsd = null,
        nativeAssetName = primary.first.name,
        nativeSymbol = primary.first.symbol,
        nativeBalance = primary.second,
        tokenList = tokens
    )
}

internal data class EvmTokenHoldingDto(val chain: String, val contract_address: String, val name: String, val symbol: String, val quantity: String, val decimals: Int)
internal data class EvmTokenNetworkResultDto(val chain: String, val status: String, val detail: String? = null)
internal data class EvmTokenPortfolioDto(val holdings: List<EvmTokenHoldingDto> = emptyList(), val networks: List<EvmTokenNetworkResultDto> = emptyList())

internal fun mergeEvmTokenHoldings(base: UnifiedAssetData, holdings: List<EvmTokenHoldingDto>, networks: List<EvmTokenNetworkResultDto>): UnifiedAssetData {
    val tokens = holdings.mapNotNull { holding ->
        val quantity = holding.quantity.toBigDecimalOrNull() ?: return@mapNotNull null
        if (quantity.signum() <= 0 || holding.contract_address.isBlank()) return@mapNotNull null
        UnifiedToken(id = "erc20-${holding.chain}-${holding.contract_address.lowercase()}", name = holding.name.ifBlank { holding.symbol }, symbol = holding.symbol, quantity = quantity)
    }
    val unavailable = networks.filter { it.status != "available" }
    val status = when {
        networks.isEmpty() -> "ERC-20 查询状态未知"
        unavailable.isNotEmpty() -> "ERC-20 部分网络不可用（${unavailable.size}/${networks.size}）"
        else -> "ERC-20 已读取"
    }
    return base.copy(ecosystemName = base.ecosystemName.substringBefore("（ERC-20") + "（$status）", tokenList = base.tokenList + tokens)
}

internal data class SolanaTokenBalance(
    val mint: String,
    val rawAmount: String,
    val decimals: Int,
    val tokenProgramName: String
)

internal fun mergeSolanaTokenBalances(accounts: List<SolanaTokenBalance>): List<UnifiedToken> {
    val totals = linkedMapOf<String, Pair<BigDecimal, String>>()
    for (account in accounts) {
        if (account.mint.isBlank() || account.decimals !in 0..30) continue
        val raw = account.rawAmount.toBigIntegerOrNull() ?: continue
        if (raw.signum() <= 0) continue
        val quantity = BigDecimal(raw).movePointLeft(account.decimals)
        val existing = totals[account.mint]
        totals[account.mint] = if (existing == null) {
            quantity to account.tokenProgramName
        } else {
            (existing.first + quantity) to existing.second
        }
    }
    return totals.map { (mint, data) ->
        UnifiedToken(
            id = mint,
            name = data.second,
            symbol = mint.take(5) + "…",
            quantity = data.first.stripTrailingZeros()
        )
    }
}

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
            DetectedNetwork.BITCOIN -> runCatching { fetchBitcoinAssets(normalized) }
            DetectedNetwork.UNKNOWN -> Result.failure(IllegalArgumentException("地址格式无法识别，请检查后重试。"))
        }
    }

    private suspend fun fetchEvmAssets(address: String): UnifiedAssetData {
        // An EVM address is shared across chains. Query each supported mainnet
        // independently so one unavailable public RPC does not hide other balances.
        val balances = coroutineScope {
            EVM_ASSET_NETWORKS.map { network ->
                async(Dispatchers.IO) {
                    network.id to runCatching {
                        val response = rpc(
                            network.rpcUrl,
                            "eth_getBalance",
                            JSONArray().put(address).put("latest")
                        )
                        parseEvmNativeBalance(response.optString("result"))
                    }.getOrNull()
                }
            }.awaitAll()
        }.mapNotNull { (networkId, balance) -> balance?.let { networkId to it } }.toMap()
        val nativeAssets = buildEvmAssetData(address, balances)
        val tokenPortfolio = runCatching { ApiClient.service.getEvmTokenPortfolio(address) }.getOrNull()
        return if (tokenPortfolio == null) {
            nativeAssets.copy(ecosystemName = nativeAssets.ecosystemName.replace("（ERC-20 未读取）", "（ERC-20 查询暂不可用）"))
        } else {
            mergeEvmTokenHoldings(nativeAssets, tokenPortfolio.holdings, tokenPortfolio.networks)
        }
    }

    private fun fetchBitcoinAssets(address: String): UnifiedAssetData {
        val request = Request.Builder()
            .url("https://blockstream.info/api/address/$address")
            .get()
            .build()
        val payload = httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Bitcoin 数据服务请求失败（HTTP ${response.code}）。")
            JSONObject(body)
        }
        val confirmed = payload.optJSONObject("chain_stats") ?: JSONObject()
        val mempool = payload.optJSONObject("mempool_stats") ?: JSONObject()
        val satoshis =
            confirmed.optLong("funded_txo_sum") - confirmed.optLong("spent_txo_sum") +
            mempool.optLong("funded_txo_sum") - mempool.optLong("spent_txo_sum")
        val balance = BigDecimal.valueOf(satoshis).movePointLeft(8)
        return UnifiedAssetData(
            address = address,
            ecosystemName = "Bitcoin",
            totalUsd = null,
            nativeAssetName = "Bitcoin",
            nativeSymbol = "BTC",
            nativeBalance = balance,
            tokenList = listOf(UnifiedToken("native-btc", "Bitcoin", "BTC", balance))
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

        val tokenProgramIds = listOf(
            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA" to "SPL Token",
            "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb" to "SPL Token-2022"
        )
        val tokenBalances = mutableListOf<SolanaTokenBalance>()
        for ((programId, programName) in tokenProgramIds) {
            val tokenResponse = rpc(
                "https://api.mainnet-beta.solana.com",
                "getTokenAccountsByOwner",
                JSONArray().put(address)
                    .put(JSONObject().put("programId", programId))
                    .put(JSONObject().put("encoding", "jsonParsed").put("commitment", "confirmed"))
            )
            val values = tokenResponse.optJSONObject("result")?.optJSONArray("value")
                ?: throw IOException("$programName 账户列表响应无效。")
            for (i in 0 until values.length()) {
                val account = values.optJSONObject(i)?.optJSONObject("account")
                val info = account?.optJSONObject("data")?.optJSONObject("parsed")
                    ?.optJSONObject("info") ?: continue
                val mint = info.optString("mint").takeIf { it.isNotBlank() } ?: continue
                val amount = info.optJSONObject("tokenAmount") ?: continue
                val rawAmount = amount.optString("amount")
                val decimals = amount.optInt("decimals", -1)
                tokenBalances += SolanaTokenBalance(
                    mint = mint,
                    rawAmount = rawAmount,
                    decimals = decimals,
                    tokenProgramName = programName
                )
            }
        }
        val tokens = mutableListOf(
            UnifiedToken("native-sol", "Solana", "SOL", solBalance)
        )
        tokens += mergeSolanaTokenBalances(tokenBalances)
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
