package com.airdrophunter.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletActivityNetworkPolicyTest {
    private fun bill(
        txHash: String = "0x" + "a".repeat(64),
        walletAddress: String = "0x" + "b".repeat(40),
        method: String = "链上交易导入",
        network: String = "ethereum"
    ) = PendingBillEntity(
        id = "tx-id",
        txHash = txHash,
        walletAddress = walletAddress,
        gasCostUsd = 1.25,
        interactionMethod = method,
        timestamp = 1_700_000_000_000L,
        chain = network,
        network = network
    )

    @Test
    fun supportedRealTransactionCanContributeAfterManualConfirmation() {
        assertTrue(isEligibleForPublicContribution(bill()))
    }

    @Test
    fun mockHashWalletAndMethodAreAllExcluded() {
        assertFalse(isEligibleForPublicContribution(bill(txHash = "MOCK-test")))
        assertFalse(isEligibleForPublicContribution(bill(walletAddress = "MOCK-wallet")))
        assertFalse(isEligibleForPublicContribution(bill(method = "模拟测试")))
        assertFalse(isEligibleForPublicContribution(bill(method = "mock transaction")))
    }

    @Test
    fun unknownAndUnsupportedNetworksAreExcluded() {
        assertFalse(isEligibleForPublicContribution(bill(network = "unknown")))
        assertFalse(isEligibleForPublicContribution(bill(network = "unsupported-chain")))
    }
}
