package com.airdrophunter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityVerificationTierTest {
    @Test
    fun contributionStatusShowsPendingVerifiedAndRejectedStates() {
        assertEquals("等待链上核验", communityContributionStatusLabel("pending_verification", null))
        assertEquals("链上精确核验", communityContributionStatusLabel("verified", "amount_verified"))
        assertEquals("链上已确认", communityContributionStatusLabel("verified", "transaction_linked"))
        assertEquals("核验拒绝", communityContributionStatusLabel("rejected", null))
        assertEquals("不符合公共贡献条件", communityContributionStatusLabel("not_eligible", null))
    }

    @Test
    fun segmentsKeepStableTrustOrderAndExactShares() {
        val segments = communityVerificationTierSegments(
            mapOf("amount_verified" to 75.0, "user_reported" to 25.0, "transaction_linked" to 0.0)
        )
        assertEquals(listOf("user_reported", "amount_verified"), segments.map { it.key })
        assertEquals(0.25f, segments[0].share, 0.000001f)
        assertEquals(0.75f, segments[1].share, 0.000001f)
        assertEquals(100.0, segments.sumOf { it.amount }, 0.000001)
    }

    @Test
    fun invalidNegativeZeroAndUnknownTiersAreNotRendered() {
        val segments = communityVerificationTierSegments(
            mapOf(
                "user_reported" to Double.NaN,
                "transaction_linked" to -2.0,
                "amount_verified" to Double.POSITIVE_INFINITY,
                "unknown" to 99.0
            )
        )
        assertTrue(segments.isEmpty())
    }

    @Test
    fun allPositiveTiersAreNormalizedWithoutChangingTheirAmounts() {
        val segments = communityVerificationTierSegments(
            mapOf("user_reported" to 10.0, "transaction_linked" to 20.0, "amount_verified" to 70.0)
        )
        assertEquals(listOf(0.1f, 0.2f, 0.7f), segments.map { it.share })
        assertEquals(listOf(10.0, 20.0, 70.0), segments.map { it.amount })
    }
}