package com.airdrophunter.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityContributionRetryPolicyTest {
    @Test
    fun idempotencyConflictIsTerminalAndDoesNotEnterAutomaticRetry() {
        assertTrue(isTerminalContributionConflict(409))
    }

    @Test
    fun transientServerAndRateLimitResponsesRemainRetryable() {
        assertFalse(isTerminalContributionConflict(408))
        assertFalse(isTerminalContributionConflict(429))
        assertFalse(isTerminalContributionConflict(500))
        assertFalse(isTerminalContributionConflict(503))
    }
}
