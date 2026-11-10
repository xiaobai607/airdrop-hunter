package com.airdrophunter.app.data

data class WalletDto(
    val id: String,
    val wallet_name: String,
    val chain: String,
    val network_icon: String,
    val address: String,
    val public_key: String = address,
    val daily_gas_usd: Double = 0.0,
    val recent_airdrop_income_usd: Double = 0.0,
    val last_synced_at: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String = ""
) {
    // The address determines the ecosystem. An EVM address is shared by all EVM networks.
    val ecosystem: String
        get() = WalletEcosystem.fromAddress(address, chain)
}

object WalletEcosystem {
    const val EVM = "EVM"
    const val TRON = "TRON"
    const val SOL = "SOL"
    const val BTC = "BTC"

    fun fromAddress(address: String, legacyChain: String = ""): String {
        val stored = WalletAddressValidator.canonicalChain(legacyChain)
        if (stored.isNotEmpty()) return stored
        val value = address.trim()
        return when {
            WalletAddressValidator.isValidForChain(EVM, value) -> EVM
            WalletAddressValidator.isValidForChain(TRON, value) -> TRON
            WalletAddressValidator.isValidForChain(BTC, value) -> BTC
            WalletAddressValidator.isValidForChain(SOL, value) -> SOL
            else -> "OTHER"
        }
    }

    fun label(ecosystem: String): String = when (ecosystem) {
        EVM -> "EVM 生态通用"
        TRON -> "TRON 生态"
        SOL -> "Solana 生态"
        BTC -> "Bitcoin 生态"
        else -> "其他网络"
    }
}

data class WalletWriteRequest(
    val wallet_name: String,
    val chain: String,
    val address: String,
    val tags: List<String> = emptyList(),
    val notes: String = ""
)

data class WalletCheckResponse(
    val valid: Boolean,
    val message: String,
    val chain: String? = null,
    val eligibility_available: Boolean = false,
    val eligible: Boolean? = null,
    val estimated_reward_usd: Double? = null
)

data class ClaimUrlResponse(
    val url: String,
    val project_name: String,
    val wallet_name: String,
    val address: String
)
