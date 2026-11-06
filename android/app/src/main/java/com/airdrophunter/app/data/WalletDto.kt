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
)

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
