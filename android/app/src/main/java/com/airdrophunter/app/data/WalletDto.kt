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
    const val BITCOIN = "Bitcoin"

    fun fromAddress(address: String, legacyChain: String = ""): String {
        return when (AddressDetector.detect(address)) {
            DetectedNetwork.EVM -> EVM
            DetectedNetwork.TRON -> TRON
            DetectedNetwork.SOL -> SOL
            DetectedNetwork.BITCOIN -> BITCOIN
            DetectedNetwork.UNKNOWN -> when {
                legacyChain.equals("Ethereum", true) || legacyChain.equals("ERC20", true) ||
                    legacyChain.equals(EVM, true) -> EVM
                legacyChain.equals("Tron", true) || legacyChain.equals("TRC20", true) ||
                    legacyChain.equals(TRON, true) -> TRON
                legacyChain.equals("Solana", true) || legacyChain.equals(SOL, true) -> SOL
                legacyChain.equals("Bitcoin", true) || legacyChain.equals(BITCOIN, true) -> BITCOIN
                else -> "OTHER"
            }
        }
    }

    fun label(ecosystem: String): String = when (ecosystem) {
        EVM -> "EVM 生态通用"
        TRON -> "TRON 生态"
        SOL -> "Solana 生态"
        BITCOIN -> "Bitcoin 网络"
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
