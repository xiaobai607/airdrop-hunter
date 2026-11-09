package com.airdrophunter.app.data

import com.google.gson.Gson

interface WalletActivityLocalDataSource {
    suspend fun read(key: String, guest: Boolean): String?
    suspend fun write(key: String, value: String, guest: Boolean)
}

class CacheStoreWalletActivityLocalDataSource : WalletActivityLocalDataSource {
    override suspend fun read(key: String, guest: Boolean): String? =
        if (guest) CacheStore.readLocal(key) else CacheStore.read(key)

    override suspend fun write(key: String, value: String, guest: Boolean) {
        if (guest) CacheStore.writeLocal(key, value) else CacheStore.write(key, value)
    }
}

interface WalletActivityRemoteDataSource {
    suspend fun getEvm(address: String): WalletActivityBatchDto
    suspend fun getChain(chain: String, address: String): List<WalletTransactionDto>
}

class RetrofitWalletActivityRemoteDataSource(
    private val api: ApiService
) : WalletActivityRemoteDataSource {
    override suspend fun getEvm(address: String) = api.getEvmWalletTransactions(address)
    override suspend fun getChain(chain: String, address: String) =
        api.getWalletTransactions(chain, address)
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
    suspend fun readCached(wallet: WalletDto, guest: Boolean): WalletActivitySnapshot? {
        val raw = local?.read(cacheKey(wallet), guest) ?: return null
        return runCatching { gson.fromJson(raw, WalletActivitySnapshot::class.java) }.getOrNull()
    }

    suspend fun refresh(wallet: WalletDto, guest: Boolean): WalletActivitySnapshot {
        val fresh = load(wallet).copy(fetchedAtEpochMillis = System.currentTimeMillis())
        local?.write(cacheKey(wallet), gson.toJson(fresh), guest)
        return fresh
    }

    private fun cacheKey(wallet: WalletDto) = "wallet_activity:${wallet.id}"

    suspend fun load(wallet: WalletDto): WalletActivitySnapshot =
        when (wallet.ecosystem) {
            WalletEcosystem.EVM -> remote.getEvm(wallet.address).let {
                WalletActivitySnapshot(
                    transactions = it.transactions,
                    networksChecked = it.networks_checked,
                    networksUnavailable = it.networks_unavailable
                )
            }
            WalletEcosystem.TRON -> WalletActivitySnapshot(
                transactions = remote.getChain("tron", wallet.address),
                networksChecked = listOf("tron")
            )
            WalletEcosystem.SOL -> WalletActivitySnapshot(
                transactions = remote.getChain("solana", wallet.address),
                networksChecked = listOf("solana")
            )
            WalletEcosystem.BTC -> WalletActivitySnapshot(
                transactions = remote.getChain("bitcoin", wallet.address),
                networksChecked = listOf("bitcoin")
            )
            else -> throw IllegalArgumentException("该地址类型暂不支持交易记录查询")
        }
}
