package com.airdrophunter.app.data

data class WalletTransactionDto(
    val chain: String,
    val hash: String,
    val timestamp: Long,
    val from_address: String,
    val to_address: String,
    val method_id: String? = null,
    val gas_used: String? = null,
    val gas_price_base_unit: String? = null,
    val total_fee_base_unit: String,
    val fee_type: String,
    val energy_used: String? = null,
    val bandwidth_used: String? = null,
    val energy_fee_base_unit: String? = null,
    val bandwidth_fee_base_unit: String? = null,
    val gas_cost_native: String,
    val native_symbol: String,
    val native_price_usd: String? = null,
    val gas_cost_usd: String? = null,
    val fee_paid_by_wallet: Boolean,
    val status: String,
    val project_official_id: String? = null,
    val attribution: String = "unassigned"
)


internal object WalletActivityNetworkPolicy {
    private val evmNetworks = setOf("ethereum", "bsc", "polygon", "arbitrum", "optimism", "base", "avalanche")
    private val supportedNetworks = evmNetworks + setOf("tron", "bitcoin")

    fun isSupported(network: String): Boolean = network.trim().lowercase() in supportedNetworks
    fun usesCaseInsensitiveAddress(network: String): Boolean = network.trim().lowercase() in evmNetworks
}

data class WalletActivityBatchDto(
    val transactions: List<WalletTransactionDto> = emptyList(),
    val networks_checked: List<String> = emptyList(),
    val networks_unavailable: List<String> = emptyList()
)
