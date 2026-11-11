package com.airdrophunter.app.data

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualProjectAccountingPolicyTest {
    private fun wallet(
        id: String,
        reward: ManualRewardInput? = null,
        hash: String = "",
        network: String = "ethereum",
        optIn: Boolean = false
    ) = ManualWalletAccountingInput(
        walletId = id,
        walletAddress = "0x" + id.padEnd(40, '1').take(40),
        network = network,
        gasCostUsd = BigDecimal("2.50"),
        txHash = hash,
        reward = reward,
        publicContributionOptIn = optIn
    )


    @Test
    fun communityDirectoryRecordsContributeByDefaultOnlyWhenVerifiable() {
        val hash = "0x" + "c".repeat(64)
        assertTrue(ManualProjectAccountingPolicy.isEligibleForDefaultCommunityContribution(
            "linea", ManualRewardKind.USD, "ethereum", hash
        ))
        assertTrue(ManualProjectAccountingPolicy.isEligibleForDefaultCommunityContribution(
            "scroll", ManualRewardKind.MANUAL_USD_VALUATION, "base", hash
        ))
        assertFalse(ManualProjectAccountingPolicy.isEligibleForDefaultCommunityContribution(
            null, ManualRewardKind.USD, "ethereum", hash
        ))
        assertFalse(ManualProjectAccountingPolicy.isEligibleForDefaultCommunityContribution(
            "linea", ManualRewardKind.POINTS, "ethereum", hash
        ))
        assertFalse(ManualProjectAccountingPolicy.isEligibleForDefaultCommunityContribution(
            "linea", ManualRewardKind.USD, "ethereum", ""
        ))
        assertFalse(ManualProjectAccountingPolicy.isEligibleForDefaultCommunityContribution(
            "linea", ManualRewardKind.USD, "tron", hash
        ))
    }

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
        val allocatedTotal = batch.entries.mapNotNull { it.actualRewardUsd }
            .fold(BigDecimal.ZERO, BigDecimal::add)
        assertEquals(0, allocatedTotal.compareTo(BigDecimal("10.00")))
        assertEquals(3, batch.entries.size)
        assertTrue(batch.entries.all { it.netProfitUsd != null })
    }

    @Test
    fun anonymousContributionConsentIsIndependentForEachWallet() {
        val hash = "0x" + "a".repeat(64)
        val batch = ManualProjectAccountingPolicy.prepare(
            listOf(
                wallet("one", ManualRewardInput(ManualRewardKind.UNKNOWN)),
                wallet("two", ManualRewardInput(ManualRewardKind.USD, BigDecimal.ZERO), hash = hash, optIn = true)
            ),
            ManualRewardAllocationMode.PER_WALLET
        )
        assertFalse(batch.entries[0].publicContributionOptIn)
        assertTrue(batch.entries[1].publicContributionOptIn)
        assertTrue(batch.hasPublicContributionOptIn)
    }

    @Test(expected = IllegalArgumentException::class)
    fun optedInWalletRejectsMissingHash() {
        ManualProjectAccountingPolicy.prepare(
            listOf(wallet("one", optIn = true)),
            ManualRewardAllocationMode.PER_WALLET
        )
    }

    @Test
    fun onlyEvmNetworksAreEligibleForCurrentBackendVerification() {
        val evmHash = "0x" + "b".repeat(64)
        val evm = ManualProjectAccountingPolicy.prepare(
            listOf(wallet("eth", ManualRewardInput(ManualRewardKind.UNKNOWN), hash = evmHash, optIn = true)),
            ManualRewardAllocationMode.PER_WALLET
        ).entries.single()
        assertTrue(evm.publicContributionOptIn)
        assertFalse(ManualProjectAccountingPolicy.isTransactionHashFormatValid("tron", "c".repeat(64)))
        assertFalse(ManualProjectAccountingPolicy.isTransactionHashFormatValid("solana", "1".repeat(88)))
        assertTrue(ManualProjectAccountingPolicy.isTransactionVerificationNetworkSupported("bsc"))
        assertFalse(ManualProjectAccountingPolicy.isTransactionVerificationNetworkSupported("EVM"))
        assertFalse(ManualProjectAccountingPolicy.isTransactionVerificationNetworkSupported("tron"))
    }

    @Test
    fun publicHashFormatValidatorRecognizesSupportedNetworkAliases() {
        assertTrue(ManualProjectAccountingPolicy.isTransactionHashFormatValid("erc20", "0x" + "a".repeat(64)))
        assertFalse(ManualProjectAccountingPolicy.isTransactionHashFormatValid("trc20", "b".repeat(64)))
        assertFalse(ManualProjectAccountingPolicy.isTransactionHashFormatValid("unknown", "b".repeat(64)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedEvmHash() {
        ManualProjectAccountingPolicy.prepare(
            listOf(wallet("one", hash = "0xabc", optIn = true)),
            ManualRewardAllocationMode.PER_WALLET
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOptInOnUnsupportedNetwork() {
        ManualProjectAccountingPolicy.prepare(
            listOf(wallet("one", hash = "anything", network = "unknown-chain", optIn = true)),
            ManualRewardAllocationMode.PER_WALLET
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
