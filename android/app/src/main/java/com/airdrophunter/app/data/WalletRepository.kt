package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

interface WalletRemoteDataSource {
    suspend fun getWallets(): List<WalletDto>
    suspend fun addWallet(request: WalletWriteRequest)
    suspend fun updateWallet(walletId: String, request: WalletWriteRequest)
    suspend fun deleteWallet(walletId: String)
}
interface WalletLocalDataSource {
    suspend fun read(guest: Boolean): String?
    suspend fun write(value: String, guest: Boolean)
}
class RetrofitWalletRemoteDataSource(private val api: ApiService) : WalletRemoteDataSource {
    override suspend fun getWallets() = api.getWallets()
    override suspend fun addWallet(request: WalletWriteRequest) { api.addWallet(request) }
    override suspend fun updateWallet(walletId: String, request: WalletWriteRequest) { api.updateWallet(walletId, request) }
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
    private fun normalizeRequest(request: WalletWriteRequest): WalletWriteRequest {
        val name = request.wallet_name.trim()
        require(name.isNotEmpty() && name.length <= 80) { "钱包名称不能为空且不能超过80个字符" }
        require(request.tags.size <= 12 && request.tags.all { it.trim().isNotEmpty() && it.trim().length <= 24 }) {
            "最多添加12个标签，每个标签不能超过24个字符"
        }
        require(request.notes.length <= 500) { "钱包备注不能超过500个字符" }
        return request.copy(
            wallet_name = name,
            tags = request.tags.map { it.trim() }.distinctBy { it.lowercase() },
            notes = request.notes.trim()
        )
    }

    suspend fun add(request: WalletWriteRequest, guest: Boolean): List<WalletDto> {
        val normalized = normalizeRequest(request)
        if (!guest) {
            remote.addWallet(normalized)
            return load(false)
        }
        val existing = decode(local.read(true))
        val candidate = WalletDto(
            id = UUID.randomUUID().toString(), wallet_name = normalized.wallet_name,
            chain = normalized.chain, network_icon = when (normalized.chain) {
                WalletEcosystem.EVM -> "E"
                WalletEcosystem.TRON -> "T"
                WalletEcosystem.BTC -> "B"
                WalletEcosystem.SOL -> "S"
                else -> "?"
            }, address = normalized.address, tags = normalized.tags, notes = normalized.notes
        )
        val updated = existing.filterNot {
            if (candidate.ecosystem == WalletEcosystem.EVM) it.address.equals(normalized.address, true)
            else it.address == normalized.address
        } + candidate
        local.write(gson.toJson(updated), true)
        return updated
    }
    suspend fun update(
        wallet: WalletDto,
        request: WalletWriteRequest,
        guest: Boolean,
        current: List<WalletDto>
    ): List<WalletDto> {
        require(current.any { it.id == wallet.id }) { "钱包不存在或已被删除" }
        val normalized = normalizeRequest(request)
        if (!guest) {
            remote.updateWallet(wallet.id, normalized)
            return load(false)
        }
        val updated = current.map { existing ->
            if (existing.id == wallet.id) existing.copy(
                wallet_name = normalized.wallet_name,
                chain = normalized.chain,
                address = normalized.address,
                public_key = normalized.address,
                tags = normalized.tags,
                notes = normalized.notes
            ) else existing
        }
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
