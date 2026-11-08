package com.airdrophunter.app.data

data class WalletTransactionDto(
    val chain: String,
    val hash: String,
    val timestamp: Long,
    val from_address: String,
    val to_address: String,
    val method_id: String? = null,
    val gas_used: String,
    val gas_price_base_unit: String,
    val gas_cost_native: String,
    val native_symbol: String,
    val gas_price_usd: String? = null,
    val gas_cost_usd: String? = null,
    val status: String,
    val project_official_id: String? = null,
    val attribution: String = "unassigned"
)
