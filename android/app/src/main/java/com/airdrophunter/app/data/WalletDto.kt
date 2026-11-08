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
    // Optional fields keep older locally cached address records readable.
    val note: String? = null,
    val purpose: String? = null,
    val tags: List<String>? = null,
    val projectIds: List<String>? = null
) {
    val ecosystem: String
        get() = WalletEcosystem.fromAddress(address, chain)
}

object WalletEcosystem {
    const val EVM = "EVM"
    const val TRON = "TRON"
    const val SOL = "SOL"
    const val BITCOIN = "Bitcoin"

    fun fromAddress(address: String, legacyChain: String = ""): String =
        when (AddressDetector.detect(address)) {
            DetectedNetwork.EVM -> EVM
            DetectedNetwork.TRON -> TRON
            DetectedNetwork.SOL -> SOL
            DetectedNetwork.BITCOIN -> BITCOIN
            DetectedNetwork.UNKNOWN -> when {
                legacyChain.equals("Ethereum", true) || legacyChain.equals("ERC20", true) ||
                    legacyChain.equals(EVM, true) || legacyChain.equals("BNB Chain", true) ||
                    legacyChain.equals("Polygon", true) || legacyChain.equals("Arbitrum", true) ||
                    legacyChain.equals("Optimism", true) || legacyChain.equals("Base", true) ||
                    legacyChain.equals("Avalanche", true) -> EVM
                legacyChain.equals("Tron", true) || legacyChain.equals("TRC20", true) ||
                    legacyChain.equals(TRON, true) -> TRON
                legacyChain.equals("Solana", true) || legacyChain.equals(SOL, true) -> SOL
                legacyChain.equals("Bitcoin", true) -> BITCOIN
                else -> "OTHER"
            }
        }

    fun label(ecosystem: String): String = when (ecosystem) {
        EVM -> "EVM 生态"
        TRON -> "TRON 生态"
        SOL -> "Solana 生态"
        BITCOIN -> "Bitcoin 网络"
        else -> "其他网络"
    }
}

object WalletAddressPurpose {
    const val MAIN_INTERACTION = "MAIN_INTERACTION"
    const val BACKUP = "BACKUP"
    const val TEST = "TEST"
    const val FUNDING = "FUNDING"
    const val OTHER = "OTHER"

    val values = listOf(MAIN_INTERACTION, BACKUP, TEST, FUNDING, OTHER)

    fun label(value: String?): String = when (value) {
        MAIN_INTERACTION -> "主交互地址"
        BACKUP -> "备用地址"
        TEST -> "测试地址"
        FUNDING -> "资金地址"
        OTHER -> "其他用途"
        else -> "未分类"
    }
}

data class WalletWriteRequest(
    val wallet_name: String,
    val chain: String,
    val address: String,
    val note: String? = null,
    val purpose: String? = WalletAddressPurpose.MAIN_INTERACTION,
    val tags: List<String>? = emptyList(),
    val projectIds: List<String>? = emptyList()
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
