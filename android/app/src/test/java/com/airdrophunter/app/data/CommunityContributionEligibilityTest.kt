package com.airdrophunter.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityContributionEligibilityTest {
    @Test
    fun simulatedBrowserBillNeverEntersPublicStats() {
        val bill = PendingBillEntity(
            id = "bill-demo",
            txHash = "MOCK-123",
            walletAddress = "0xabc",
            gasCostUsd = 0.25,
            interactionMethod = "模拟合约交互",
            timestamp = 1L,
            chain = "Ethereum",
            network = "ethereum"
        )
        assertFalse(isEligibleForPublicContribution(bill))
    }

    @Test
    fun supportedRealTransactionCanEnterPublicStats() {
        val bill = PendingBillEntity(
            id = "bill-real",
            txHash = "0x123456",
            walletAddress = "0xabc",
            gasCostUsd = 0.25,
            interactionMethod = "交易回执抓取",
            timestamp = 1L,
            chain = "Ethereum",
            network = "ethereum"
        )
        assertTrue(isEligibleForPublicContribution(bill))
    }

    @Test
    fun unknownNetworkAndPlaceholderWalletAreRejected() {
        val unknownNetwork = PendingBillEntity(
            id = "bill-unknown", txHash = "0x123", walletAddress = "0xabc",
            gasCostUsd = 0.1, interactionMethod = "交易回执抓取", timestamp = 1L
        )
        val placeholderWallet = unknownNetwork.copy(
            txHash = "0x123", walletAddress = "MOCK-WALLET-LOCAL", network = "ethereum"
        )
        assertFalse(isEligibleForPublicContribution(unknownNetwork))
        assertFalse(isEligibleForPublicContribution(placeholderWallet))
    }
}
