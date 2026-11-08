package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

interface WalletLocalDataSource {
    suspend fun read(guest: Boolean): String?
    suspend fun write(value: String, guest: Boolean)
}

class CacheStoreWalletLocalDataSource : WalletLocalDataSource {
    override suspend fun read(guest: Boolean) =
        if (guest) CacheStore.readLocal("wallets") else CacheStore.read("wallets")

    override suspend fun write(value: String, guest: Boolean) {
        if (guest) CacheStore.writeLocal("wallets", value) else CacheStore.write("wallets", value)
    }
}

/**
 * Address management is local-first for both guest and signed-in sessions.
 * Account/cloud synchronization is intentionally deferred; this repository never
 * uploads addresses or signing material to the backend.
 */
class WalletRepository(
    private val local: WalletLocalDataSource,
    private val gson: Gson = Gson()
) {
    private val listType = object : TypeToken<List<WalletDto>>() {}.type

    private fun decode(raw: String?): List<WalletDto> =
        raw?.let { runCatching { gson.fromJson<List<WalletDto>>(it, listType) }.getOrNull() }
            .orEmpty()
            .map { wallet ->
                wallet.copy(
                    note = wallet.note.orEmpty(),
                    purpose = wallet.purpose ?: WalletAddressPurpose.MAIN_INTERACTION,
                    tags = wallet.tags.orEmpty(),
                    projectIds = wallet.projectIds.orEmpty()
                )
            }

    suspend fun load(guest: Boolean): List<WalletDto> = decode(local.read(guest))

    suspend fun add(request: WalletWriteRequest, guest: Boolean): List<WalletDto> {
        val existing = decode(local.read(guest))
        val candidateEcosystem = WalletEcosystem.fromAddress(request.address, request.chain)
        val match = existing.firstOrNull { saved ->
            saved.chain.equals(request.chain, ignoreCase = true) &&
                if (candidateEcosystem == WalletEcosystem.EVM) {
                    saved.address.equals(request.address, ignoreCase = true)
                } else {
                    saved.address == request.address
                }
        }
        val updatedRecord = if (match == null) {
            WalletDto(
                id = UUID.randomUUID().toString(),
                wallet_name = request.wallet_name.trim().ifBlank { "地址" },
                chain = request.chain,
                network_icon = when (candidateEcosystem) {
                    WalletEcosystem.EVM -> "E"
                    WalletEcosystem.TRON -> "T"
                    WalletEcosystem.BITCOIN -> "B"
                    else -> "S"
                },
                address = request.address.trim(),
                note = request.note?.trim().orEmpty(),
                purpose = request.purpose ?: WalletAddressPurpose.MAIN_INTERACTION,
                tags = request.tags.orEmpty().map(String::trim).filter(String::isNotEmpty).distinct(),
                projectIds = request.projectIds.orEmpty().distinct()
            )
        } else {
            match.copy(
                wallet_name = request.wallet_name.trim().ifBlank { match.wallet_name },
                note = request.note?.trim().orEmpty(),
                purpose = request.purpose ?: match.purpose,
                tags = request.tags.orEmpty().map(String::trim).filter(String::isNotEmpty).distinct(),
                projectIds = (match.projectIds.orEmpty() + request.projectIds.orEmpty()).distinct()
            )
        }
        val updated = existing.filterNot { it.id == match?.id } + updatedRecord
        local.write(gson.toJson(updated), guest)
        return updated
    }

    suspend fun delete(wallet: WalletDto, guest: Boolean, current: List<WalletDto>): List<WalletDto> {
        val updated = current.filterNot { it.id == wallet.id }
        local.write(gson.toJson(updated), guest)
        return updated
    }
}
