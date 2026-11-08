package com.airdrophunter.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityContributionEligibilityTest {
    @Test
    fun simulatedBrowserBillCannotEnterPublicStats() {
        val bill = PendingBillEntity(
            id = "bill-1",
            txHash = "MOCK-123",
            walletAddress = "0xabc",
            gasCostUsd = 0.25,
            interactionMethod = "模拟合约交互",
            timestamp = 1L,
            network = "EVM"
        )
        assertFalse(isEligibleForPublicContribution(bill))
    }

    @Test
    fun realTransactionMetadataCanBeEligible() {
        val bill = PendingBillEntity(
            id = "bill-2",
            txHash = "0x123456",
            walletAddress = "0xabc",
            gasCostUsd = 0.25,
            interactionMethod = "交易回执抓取",
            timestamp = 1L,
            network = "Ethereum"
        )
        assertTrue(isEligibleForPublicContribution(bill))
    }

    @Test
    fun missingNetworkOrPlaceholderWalletCannotBeEligible() {
        val unknownNetwork = PendingBillEntity(
            id = "bill-3", txHash = "0x123", walletAddress = "0xabc",
            gasCostUsd = 0.1, interactionMethod = "交易回执抓取", timestamp = 1L
        )
        val placeholderWallet = unknownNetwork.copy(
            txHash = "0x123", walletAddress = "MOCK-WALLET-LOCAL", network = "Ethereum"
        )
        assertFalse(isEligibleForPublicContribution(unknownNetwork))
        assertFalse(isEligibleForPublicContribution(placeholderWallet))
    }
}
