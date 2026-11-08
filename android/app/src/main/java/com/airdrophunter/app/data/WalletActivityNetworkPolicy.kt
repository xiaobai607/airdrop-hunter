package com.airdrophunter.app.data

/**
 * Networks for which the app can import real, public-chain transaction activity.
 *
 * Keep this allowlist aligned with the backend wallet-activity route and the
 * network identifiers returned by the wallet activity APIs. Unknown or
 * placeholder network labels must never be accepted as on-chain records.
 */
internal object WalletActivityNetworkPolicy {
    private val supportedNetworks = setOf(
        "ethereum",
        "bsc",
        "polygon",
        "arbitrum",
        "optimism",
        "base",
        "avalanche",
        "tron",
        "bitcoin"
    )

    fun isSupported(network: String): Boolean =
        network.trim().lowercase() in supportedNetworks
}
