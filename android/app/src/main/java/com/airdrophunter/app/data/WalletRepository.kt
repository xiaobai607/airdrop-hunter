package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

interface WalletRemoteDataSource {
    suspend fun getWallets(): List<WalletDto>
    suspend fun addWallet(request: WalletWriteRequest)
    suspend fun deleteWallet(walletId: String)
}
interface WalletLocalDataSource {
    suspend fun read(guest: Boolean): String?
    suspend fun write(value: String, guest: Boolean)
}
class RetrofitWalletRemoteDataSource(private val api: ApiService) : WalletRemoteDataSource {
    override suspend fun getWallets() = api.getWallets()
    override suspend fun addWallet(request: WalletWriteRequest) { api.addWallet(request) }
    override suspend fun deleteWallet(walletId: String) { api.deleteWallet(walletId) }
}
class CacheStoreWalletLocalDataSource : WalletLocalDataSource {
    override suspend fun read(guest: Boolean) = if (guest) CacheStore.readLocal("wallets") else CacheStore.read("wallets")
    override suspend fun write(value: String, guest: Boolean) {
        if (guest) CacheStore.writeLocal("wallets", value) else CacheStore.write("wallets", value)
    }
}
/** Persists public wallet addresses only; signing material is never accessed. */
class WalletRepository(
    private val remote: WalletRemoteDataSource,
    private val local: WalletLocalDataSource,
    private val gson: Gson = Gson()
) {
    private val listType = object : TypeToken<List<WalletDto>>() {}.type
    private fun decode(raw: String?): List<WalletDto> =
        raw?.let { runCatching { gson.fromJson<List<WalletDto>>(it, listType) }.getOrNull() }.orEmpty()

    suspend fun load(guest: Boolean): List<WalletDto> {
        if (guest) return decode(local.read(true))
        return remote.getWallets().also { local.write(gson.toJson(it), false) }
    }
    suspend fun add(request: WalletWriteRequest, guest: Boolean): List<WalletDto> {
        if (!guest) {
            remote.addWallet(request)
            return load(false)
        }
        val existing = decode(local.read(true))
        val candidate = WalletDto(
            id = UUID.randomUUID().toString(), wallet_name = request.wallet_name,
            chain = request.chain, network_icon = when (request.chain) {
                WalletEcosystem.EVM -> "E"
                WalletEcosystem.TRON -> "T"
                WalletEcosystem.BITCOIN -> "B"
                else -> "S"
            }, address = request.address
        )
        val updated = existing.filterNot {
            if (candidate.ecosystem == WalletEcosystem.EVM) it.address.equals(request.address, true) && it.chain.equals(request.chain, true)
            else it.address == request.address
        } + candidate
        local.write(gson.toJson(updated), true)
        return updated
    }
    suspend fun delete(wallet: WalletDto, guest: Boolean, current: List<WalletDto>): List<WalletDto> {
        if (!guest) remote.deleteWallet(wallet.id)
        val updated = current.filterNot { it.id == wallet.id }
        local.write(gson.toJson(updated), guest)
        return updated
    }
}
