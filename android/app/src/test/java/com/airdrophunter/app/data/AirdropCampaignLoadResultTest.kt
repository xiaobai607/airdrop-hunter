package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AirdropCampaignLoadResultTest {
    @Test
    fun staleFallbackExposesWarningWhileRetainingRows() {
        val rows = emptyList<AirdropCampaignDto>()
        val result = AirdropCampaignLoadResult(rows, stale = true, errorMessage = "provider unavailable")

        assertEquals(rows, result.rows)
        assertEquals("provider unavailable", result.warning)
    }

    @Test
    fun successfulCacheOrFreshResponseDoesNotExposeWarning() {
        assertNull(AirdropCampaignLoadResult(emptyList(), stale = false, errorMessage = null).warning)
        assertNull(AirdropCampaignLoadResult(emptyList(), stale = false, errorMessage = "ignored for fresh data").warning)
    }
}
