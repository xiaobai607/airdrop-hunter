package com.airdrophunter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPageLoadPolicyTest {
    @Test
    fun loadAttemptTimeoutIsTwelveSeconds() {
        assertEquals(12_000L, BrowserPageLoadPolicy.ATTEMPT_TIMEOUT_MS)
    }

    @Test
    fun successfulPageFinishRequiresNoMainFrameFailure() {
        assertTrue(BrowserPageLoadPolicy.shouldMarkSuccessful(mainFrameFailed = false))
        assertFalse(BrowserPageLoadPolicy.shouldMarkSuccessful(mainFrameFailed = true))
    }
}
