package com.airdrophunter.app

import android.content.Context
import android.net.Uri
import com.airdrophunter.app.data.CacheRefreshCoordinator
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class CuratedWalletDefinition(
    val key: String,
    val displayName: String,
    val officialHost: String,
    val aliases: Set<String> = emptySet()
)

internal data class ReownWalletListing(
    val id: String,
    val name: String,
    val homepage: String?,
    val imageId: String?,
    val priority: Int
)

internal interface ReownExplorerApi {
    @GET("v3/wallets")
    suspend fun searchWallets(
        @Query("projectId") projectId: String,
        @Query("search") search: String,
        @Query("platforms") platforms: String = "android",
        @Query("chains") chains: String = "eip155:1",
        @Query("sdks") sdks: String = "sign_v2"
    ): ResponseBody
}

private object ReownExplorerClient {
    val api: ReownExplorerApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://explorer-api.walletconnect.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ReownExplorerApi::class.java)
    }
}

/**
 * APP-owned shortlist. Explorer IDs are resolved by exact official name and
 * verified homepage host; no directory IDs are guessed or inferred from brands.
 *
 * AppKit in this release is configured with EVM chains, so the directory query
 * deliberately requires eip155 support. Solana connection support is a separate
 * adapter/namespace task and is not advertised by this EVM-only picker.
 */
internal object CuratedWalletCatalog {
    const val CACHE_KEY = "reown_curated_wallets_v1"
    const val CACHE_SOURCE = "reown_wallet_explorer"
    const val CACHE_TTL_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    const val MINIMUM_VERIFIED_WALLETS = 8

    val definitions = listOf(
        CuratedWalletDefinition("metamask", "MetaMask", "metamask.io"),
        CuratedWalletDefinition("okx", "OKX Wallet", "okx.com"),
        CuratedWalletDefinition("trust", "Trust Wallet", "trustwallet.com"),
        CuratedWalletDefinition("rabby", "Rabby", "rabby.io"),
        CuratedWalletDefinition("phantom", "Phantom", "phantom.app"),
        CuratedWalletDefinition("bitget", "Bitget Wallet", "bitget.com"),
        CuratedWalletDefinition("binance", "Binance Wallet", "binance.com", setOf("Binance Web3 Wallet")),
        CuratedWalletDefinition("safepal", "SafePal", "safepal.com"),
        CuratedWalletDefinition("tokenpocket", "TokenPocket", "tokenpocket.pro"),
        CuratedWalletDefinition("imtoken", "imToken", "token.im"),
        CuratedWalletDefinition("coinbase", "Coinbase Wallet", "coinbase.com"),
        CuratedWalletDefinition("backpack", "Backpack", "backpack.app"),
        CuratedWalletDefinition("zerion", "Zerion", "zerion.io"),
        CuratedWalletDefinition("rainbow", "Rainbow", "rainbow.me", setOf("Rainbow Wallet")),
        CuratedWalletDefinition("uniswap", "Uniswap Wallet", "uniswap.org")
    )

    fun orderBySuccessfulConnections(
        wallets: List<ReownWalletListing>,
        usage: List<WalletUsageRecord>
    ): List<ReownWalletListing> {
        val recordsByIdentity = usage.associateBy { it.identity }
        return wallets.sortedWith(
            compareByDescending<ReownWalletListing> { wallet ->
                usageFor(wallet, recordsByIdentity)?.successfulConnections ?: 0L
            }.thenByDescending { wallet ->
                usageFor(wallet, recordsByIdentity)?.lastConnectedAt ?: 0L
            }.thenBy { it.priority }
        )
    }

    private fun usageFor(
        wallet: ReownWalletListing,
        recordsByIdentity: Map<String, WalletUsageRecord>
    ): WalletUsageRecord? {
        val byWebsite = wallet.homepage?.let {
            recordsByIdentity[canonicalWalletIdentity(wallet.name, it)]
        }
        if (byWebsite != null) return byWebsite

        val definition = definitions.getOrNull(wallet.priority) ?: return null
        val acceptedNames = (definition.aliases + definition.displayName + wallet.name)
            .map(::normalizedName)
            .toSet()
        return recordsByIdentity.values.firstOrNull {
            normalizedName(it.displayName) in acceptedNames
        }
    }

    internal fun normalizedName(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")
}

internal class ReownWalletCatalogRepository(private val context: Context) {
    private val gson = Gson()
    private val coordinator = CacheRefreshCoordinator()

    suspend fun loadCuratedWallets(): List<ReownWalletListing> {
        val result = coordinator.load(
            cacheKey = CuratedWalletCatalog.CACHE_KEY,
            source = CuratedWalletCatalog.CACHE_SOURCE,
            ttlMillis = CuratedWalletCatalog.CACHE_TTL_MILLIS,
            readCache = {
                CacheStore.readPublic(CuratedWalletCatalog.CACHE_KEY)?.let(::decode)
            },
            fetch = { fetchCuratedWallets() },
            save = { wallets ->
                CacheStore.writePublic(CuratedWalletCatalog.CACHE_KEY, gson.toJson(wallets))
            }
        )
        val wallets = result.data.orEmpty()
        if (wallets.size < CuratedWalletCatalog.MINIMUM_VERIFIED_WALLETS) {
            val detail = result.errorMessage?.let { ": $it" }.orEmpty()
            throw IOException("精选钱包目录暂不可用，请检查网络后重试$detail")
        }
        return wallets
    }

    private suspend fun fetchCuratedWallets(): List<ReownWalletListing> = coroutineScope {
        val semaphore = Semaphore(5)
        val resolved = CuratedWalletCatalog.definitions.mapIndexed { index, definition ->
            async {
                semaphore.withPermit {
                    runCatching { findExactListing(definition, index) }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull()

        val duplicateId = resolved.groupBy { it.id }.values.any { it.size > 1 }
        val duplicateName = resolved.groupBy {
            CuratedWalletCatalog.normalizedName(it.name)
        }.values.any { it.size > 1 }
        if (duplicateId || duplicateName) {
            throw IOException("精选钱包配置出现重复官方目录项，已阻止初始化")
        }
        val ordered = resolved.sortedBy { it.priority }
        if (ordered.size < CuratedWalletCatalog.MINIMUM_VERIFIED_WALLETS) {
            throw IOException(
                "Reown 官方目录仅核验到 ${ordered.size}/${CuratedWalletCatalog.definitions.size} 个精选钱包"
            )
        }
        ordered
    }

    private suspend fun findExactListing(
        definition: CuratedWalletDefinition,
        priority: Int
    ): ReownWalletListing? {
        val body = ReownExplorerClient.api.searchWallets(
            projectId = BuildConfig.REOWN_PROJECT_ID,
            search = definition.displayName
        ).string()
        val candidates = parseListings(body).filter { listing ->
            val names = (definition.aliases + definition.displayName)
                .map(CuratedWalletCatalog::normalizedName)
                .toSet()
            CuratedWalletCatalog.normalizedName(listing.name) in names
        }
        if (candidates.isEmpty()) return null

        val hostMatches = candidates.filter { listing ->
            val host = listing.homepage?.let(::hostOf) ?: return@filter false
            host == definition.officialHost ||
                host.endsWith(".${definition.officialHost}")
        }
        val selected = hostMatches.singleOrNull() ?: return null

        if (!Regex("^[a-fA-F0-9]{64}$").matches(selected.id)) return null
        return selected.copy(priority = priority)
    }

    private fun parseListings(raw: String): List<ReownWalletListing> {
        val root = JSONObject(raw)
        val value = root.opt("listings") ?: root.opt("data") ?: return emptyList()
        val objects = when (value) {
            is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
            is JSONObject -> {
                val result = mutableListOf<JSONObject>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    value.optJSONObject(key)?.let { listing ->
                        if (!listing.has("id")) listing.put("id", key)
                        result += listing
                    }
                }
                result
            }
            else -> emptyList()
        }
        return objects.mapNotNull { item ->
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            val homepage = sequenceOf("homepage", "url", "website")
                .map { item.optString(it).trim() }
                .firstOrNull { it.isNotBlank() }
            val imageId = item.optString("image_id").trim().takeIf { it.isNotBlank() }
            ReownWalletListing(
                id = id,
                name = name,
                homepage = homepage,
                imageId = imageId,
                priority = Int.MAX_VALUE
            )
        }
    }

    private fun decode(raw: String): List<ReownWalletListing>? =
        runCatching {
            gson.fromJson(raw, Array<ReownWalletListing>::class.java)?.toList()
        }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun hostOf(url: String): String? = runCatching {
        val normalized = if ("://" in url) url else "https://$url"
        Uri.parse(normalized).host
            ?.trimEnd('.')
            ?.lowercase(Locale.ROOT)
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
