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
    val last_synced_at: String? = null
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
        val value = address.trim()
        return when {
            Regex("^0x[a-fA-F0-9]{40}$").matches(value) -> EVM
            Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$").matches(value) -> TRON
            isBitcoinAddress(value) -> BTC
            Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$").matches(value) -> SOL
            legacyChain.equals("Ethereum", true) || legacyChain.equals("ERC20", true) ||
                legacyChain.equals(EVM, true) -> EVM
            legacyChain.equals("Tron", true) || legacyChain.equals("TRC20", true) ||
                legacyChain.equals(TRON, true) -> TRON
            legacyChain.equals("Solana", true) || legacyChain.equals(SOL, true) -> SOL
            legacyChain.equals("Bitcoin", true) || legacyChain.equals("BTC", true) -> BTC
            else -> "OTHER"
        }
    }

    private fun isBitcoinAddress(value: String): Boolean {
        val base58 = Regex("^(1|3)[a-km-zA-HJ-NP-Z1-9]{25,34}$")
        if (base58.matches(value)) return true
        if (!value.equals(value.lowercase()) && !value.equals(value.uppercase())) return false
        return Regex("^bc1[ac-hj-np-z02-9]{11,71}$", RegexOption.IGNORE_CASE).matches(value)
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
    val address: String
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
