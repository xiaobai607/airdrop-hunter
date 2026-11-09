package com.airdrophunter.app.data

import java.util.Locale
import java.util.UUID

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

/**
 * Only prefill a confirmed, complete fee when chain data proves this
 * address paid it. Ambiguous/shared fees remain blank for manual review.
 */
object WalletTransactionAccountingPolicy {
    fun suggestedGasCostUsd(transaction: WalletTransactionDto): String =
        if (
            transaction.status != "pending" &&
            transaction.fee_paid_by_wallet &&
            transaction.fee_type != "evm_l2_execution_fee_wei"
        ) transaction.gas_cost_usd.orEmpty() else ""
}

object WalletTransactionIdentity {
    /** Stable transaction ID shared across guest/account scopes; Room scopes the row separately. */
    fun pendingBillId(chain: String, walletAddress: String, txHash: String, ecosystem: String): String {
        val network = chain.trim().lowercase(Locale.US)
        val address = if (ecosystem == WalletEcosystem.EVM) walletAddress.trim().lowercase(Locale.US) else walletAddress.trim()
        val hash = if (network == "solana") txHash else txHash.lowercase(Locale.US)
        val identity = "$network|$address|$hash"
        return UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)).toString()
    }
}

data class WalletActivityBatchDto(
    val transactions: List<WalletTransactionDto> = emptyList(),
    val networks_checked: List<String> = emptyList(),
    val networks_unavailable: List<String> = emptyList()
)
