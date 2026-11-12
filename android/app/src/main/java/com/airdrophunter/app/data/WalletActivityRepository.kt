package com.airdrophunter.app.data

import com.google.gson.Gson

interface WalletActivityLocalDataSource {
    suspend fun read(key: String, guest: Boolean): String?
    suspend fun write(key: String, value: String, guest: Boolean)
    suspend fun readForSession(key: String, guest: Boolean, accessToken: String?): String? = read(key, guest)
    suspend fun writeForSession(key: String, value: String, guest: Boolean, accessToken: String?) =
        write(key, value, guest)
}

class CacheStoreWalletActivityLocalDataSource : WalletActivityLocalDataSource {
    override suspend fun read(key: String, guest: Boolean): String? =
        if (guest) CacheStore.readLocal(key) else CacheStore.read(key)

    override suspend fun write(key: String, value: String, guest: Boolean) {
        if (guest) CacheStore.writeLocal(key, value) else CacheStore.write(key, value)
    }
    override suspend fun readForSession(key: String, guest: Boolean, accessToken: String?): String? =
        if (guest) CacheStore.readLocalForToken(key, accessToken)
        else accessToken?.let { CacheStore.readForToken(key, it) }
    override suspend fun writeForSession(key: String, value: String, guest: Boolean, accessToken: String?) {
        if (guest) CacheStore.writeLocalForToken(key, value, accessToken)
        else accessToken?.let { CacheStore.writeForToken(key, value, it) }
    }
}

interface WalletActivityRemoteDataSource {
    suspend fun getEvm(address: String, authorizationHeader: String? = null, authMode: String? = null): WalletActivityBatchDto
    suspend fun getChain(chain: String, address: String, authorizationHeader: String? = null, authMode: String? = null): List<WalletTransactionDto>
}

class RetrofitWalletActivityRemoteDataSource(
    private val api: ApiService
) : WalletActivityRemoteDataSource {
    override suspend fun getEvm(address: String, authorizationHeader: String?, authMode: String?) =
        api.getEvmWalletTransactions(address, authorizationHeader = authorizationHeader, authMode = authMode)
    override suspend fun getChain(chain: String, address: String, authorizationHeader: String?, authMode: String?) =
        api.getWalletTransactions(chain, address, authorizationHeader = authorizationHeader, authMode = authMode)
}

data class WalletActivitySnapshot(
    val transactions: List<WalletTransactionDto>,
    val networksChecked: List<String> = emptyList(),
    val networksUnavailable: List<String> = emptyList(),
    val fetchedAtEpochMillis: Long = 0L
)

/** Routes a saved public address to the read-only chain activity API. */
class WalletActivityRepository(
    private val remote: WalletActivityRemoteDataSource,
    private val local: WalletActivityLocalDataSource? = null,
    private val gson: Gson = Gson()
) {
    suspend fun readCached(
        wallet: WalletDto,
        guest: Boolean,
        accessToken: String? = TokenStore.readTokenBlocking()
    ): WalletActivitySnapshot? {
        val raw = local?.readForSession(cacheKey(wallet), guest, accessToken) ?: return null
        return runCatching { gson.fromJson(raw, WalletActivitySnapshot::class.java) }.getOrNull()
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun refresh(
        wallet: WalletDto,
        guest: Boolean,
        accessToken: String? = TokenStore.readTokenBlocking(),
        force: Boolean = false
    ): WalletActivitySnapshot {
        val token = accessToken?.takeIf { it.isNotBlank() }
        val authorizationHeader = token?.let { "Bearer $it" }
        val authMode = if (token == null) "anonymous" else "captured"
        val fresh = load(wallet, authorizationHeader, authMode)
            .copy(fetchedAtEpochMillis = System.currentTimeMillis())
        local?.writeForSession(cacheKey(wallet), gson.toJson(fresh), guest, token)
        return fresh
    }

    private fun cacheKey(wallet: WalletDto) = "wallet_activity:${wallet.id}"

    suspend fun load(
        wallet: WalletDto,
        authorizationHeader: String? = null,
        authMode: String? = null
    ): WalletActivitySnapshot =
        when (wallet.ecosystem) {
            WalletEcosystem.EVM -> remote.getEvm(wallet.address, authorizationHeader, authMode).let {
                WalletActivitySnapshot(
                    transactions = it.transactions,
                    networksChecked = it.networks_checked,
                    networksUnavailable = it.networks_unavailable
                )
            }
            WalletEcosystem.TRON -> WalletActivitySnapshot(
                transactions = remote.getChain("tron", wallet.address, authorizationHeader, authMode),
                networksChecked = listOf("tron")
            )
            WalletEcosystem.SOL -> WalletActivitySnapshot(
                transactions = remote.getChain("solana", wallet.address, authorizationHeader, authMode),
                networksChecked = listOf("solana")
            )
            WalletEcosystem.BTC -> WalletActivitySnapshot(
                transactions = remote.getChain("bitcoin", wallet.address, authorizationHeader, authMode),
                networksChecked = listOf("bitcoin")
            )
            else -> throw IllegalArgumentException("该地址类型暂不支持交易记录查询")
        }
}
