package com.airdrophunter.app.data

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualProjectAccountingPolicyTest {
    private fun wallet(id: String, reward: ManualRewardInput? = null, hash: String = "") =
        ManualWalletAccountingInput(
            walletId = id,
            walletAddress = "0x" + id.padEnd(40, '1').take(40),
            network = "ethereum",
            gasCostUsd = BigDecimal("2.50"),
            txHash = hash,
            reward = reward
        )

    @Test
    fun perWalletUsdRewardProducesExplicitNetProfit() {
        val batch = ManualProjectAccountingPolicy.prepare(
            listOf(wallet("one", ManualRewardInput(ManualRewardKind.USD, BigDecimal("10")))),
            ManualRewardAllocationMode.PER_WALLET
        )
        assertEquals(BigDecimal("10"), batch.entries.single().actualRewardUsd)
        assertEquals(BigDecimal("7.50"), batch.entries.single().netProfitUsd)
        assertTrue(batch.hasCompleteUsdProfit)
    }

    @Test
    fun unknownPointsAndUnissuedRewardsNeverBecomeZeroUsdProfit() {
        val rewards = listOf(
            ManualRewardInput(ManualRewardKind.UNKNOWN),
            ManualRewardInput(ManualRewardKind.POINTS, BigDecimal("250"), "XP"),
            ManualRewardInput(ManualRewardKind.UNISSUED, BigDecimal("3"), "TOKEN")
        )
        rewards.forEach { reward ->
            val entry = ManualProjectAccountingPolicy.prepare(
                listOf(wallet("one", reward)),
                ManualRewardAllocationMode.PER_WALLET
            ).entries.single()
            assertNull(entry.actualRewardUsd)
            assertNull(entry.netProfitUsd)
        }
    }

    @Test
    fun tokenWithoutHistoricalOrManualValuationKeepsUsdProfitUnknown() {
        val entry = ManualProjectAccountingPolicy.prepare(
            listOf(wallet("one", ManualRewardInput(ManualRewardKind.TOKEN, BigDecimal("3.25"), "ABC"))),
            ManualRewardAllocationMode.PER_WALLET
        ).entries.single()
        assertNull(entry.actualRewardUsd)
        assertNull(entry.netProfitUsd)
    }

    @Test
    fun totalRewardSplitsEvenlyAndPreservesRemainderExactly() {
        val batch = ManualProjectAccountingPolicy.prepare(
            listOf(wallet("one"), wallet("two"), wallet("three")),
            ManualRewardAllocationMode.EVEN_SPLIT_TOTAL,
            ManualRewardInput(ManualRewardKind.USD, BigDecimal("10.00"))
        )
        assertEquals(BigDecimal("10.00"), batch.entries.mapNotNull { it.actualRewardUsd }
            .fold(BigDecimal.ZERO, BigDecimal::add))
        assertEquals(3, batch.entries.size)
        assertTrue(batch.entries.all { it.netProfitUsd != null })
    }

    @Test
    fun anonymousContributionIsOptInAndRequiresHashForEveryWallet() {
        val privateBatch = ManualProjectAccountingPolicy.prepare(
            listOf(wallet("one")),
            ManualRewardAllocationMode.PER_WALLET,
            publicContributionOptIn = false
        )
        assertFalse(privateBatch.publicContributionOptIn)

        val optedIn = ManualProjectAccountingPolicy.prepare(
            listOf(wallet("one", hash = "0xabc")),
            ManualRewardAllocationMode.PER_WALLET,
            publicContributionOptIn = true
        )
        assertTrue(optedIn.publicContributionOptIn)
    }

    @Test(expected = IllegalArgumentException::class)
    fun optedInBatchRejectsMissingPerWalletHash() {
        ManualProjectAccountingPolicy.prepare(
            listOf(wallet("one"), wallet("two", hash = "0xabc")),
            ManualRewardAllocationMode.PER_WALLET,
            publicContributionOptIn = true
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateWalletRowsBeforePersistence() {
        ManualProjectAccountingPolicy.prepare(
            listOf(wallet("same"), wallet("same")),
            ManualRewardAllocationMode.PER_WALLET
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMixedPerWalletAndTotalRewardInput() {
        ManualProjectAccountingPolicy.prepare(
            listOf(wallet("one", ManualRewardInput(ManualRewardKind.USD, BigDecimal.ONE))),
            ManualRewardAllocationMode.PER_WALLET,
            totalReward = ManualRewardInput(ManualRewardKind.USD, BigDecimal.ONE)
        )
    }
}
