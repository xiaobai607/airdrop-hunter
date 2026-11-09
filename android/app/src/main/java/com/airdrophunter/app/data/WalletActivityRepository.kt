package com.airdrophunter.app.data

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
    val networksUnavailable: List<String> = emptyList()
)

/** Routes a saved public address to the read-only chain activity API. */
class WalletActivityRepository(
    private val remote: WalletActivityRemoteDataSource
) {
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
