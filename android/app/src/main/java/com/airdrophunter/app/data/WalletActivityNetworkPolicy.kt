package com.airdrophunter.app.data

internal object WalletActivityNetworkPolicy {
    private val supportedNetworks = setOf(
        "ethereum", "bsc", "polygon", "arbitrum", "optimism", "base",
        "avalanche", "tron", "bitcoin", "solana"
    )

    fun isSupported(network: String): Boolean = network.trim().lowercase() in supportedNetworks
}

internal fun isDemoAccountingTransaction(txHash: String, walletAddress: String, interactionMethod: String): Boolean =
    txHash.startsWith("MOCK-", ignoreCase = true) ||
        walletAddress.startsWith("MOCK-", ignoreCase = true) ||
        interactionMethod.contains("模拟", ignoreCase = true) ||
        interactionMethod.contains("mock", ignoreCase = true)

internal fun isEligibleForPublicContribution(bill: PendingBillEntity): Boolean =
    bill.txHash.isNotBlank() &&
        bill.walletAddress.isNotBlank() &&
        WalletActivityNetworkPolicy.isSupported(bill.network) &&
        !isDemoAccountingTransaction(bill.txHash, bill.walletAddress, bill.interactionMethod)
