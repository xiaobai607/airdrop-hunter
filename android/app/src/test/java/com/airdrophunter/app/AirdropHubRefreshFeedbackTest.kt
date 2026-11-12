package com.airdrophunter.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AirdropHubRefreshFeedbackTest {
    @Test fun preservesSpecificManualCooldownMessage() {
        assertEquals(
            "刚刚刷新过，请30秒后再试",
            airdropHubRefreshWarning("刚刚刷新过，请30秒后再试")
        )
    }

    @Test fun mapsProviderFailureToRetainedCacheWarning() {
        assertEquals(
            "数据源暂时不可用，已保留上次数据。",
            airdropHubRefreshWarning("provider unavailable")
        )
    }

    @Test fun mapsMissingErrorToRetainedCacheWarning() {
        assertEquals(
            "数据源暂时不可用，已保留上次数据。",
            airdropHubRefreshWarning(null)
        )
    }
}
