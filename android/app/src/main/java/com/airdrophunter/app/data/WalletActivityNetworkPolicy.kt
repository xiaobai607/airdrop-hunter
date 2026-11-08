package com.airdrophunter.app.data

internal object WalletActivityNetworkPolicy {
    private val supportedNetworks = setOf(
        "ethereum", "bsc", "polygon", "arbitrum", "optimism", "base",
        "avalanche", "tron", "bitcoin"
    )
    fun isSupported(network: String): Boolean = network.trim().lowercase() in supportedNetworks
}

internal fun isEligibleForPublicContribution(bill: PendingBillEntity): Boolean =
    bill.txHash.isNotBlank() &&
        !bill.txHash.startsWith("MOCK-", ignoreCase = true) &&
        bill.walletAddress.isNotBlank() &&
        !bill.walletAddress.startsWith("MOCK-", ignoreCase = true) &&
        bill.network.isNotBlank() &&
        !bill.network.equals("unknown", ignoreCase = true) &&
        !bill.interactionMethod.contains("模拟", ignoreCase = true) &&
        !bill.interactionMethod.contains("mock", ignoreCase = true)
