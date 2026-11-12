package com.airdrophunter.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketWarmupPolicyTest {
    @Test fun retriesWhenBothMarketSourcesFail() {
        assertTrue(hasMarketWarmupFailure(priceRefreshSucceeded = false, hotCoinsRefreshSucceeded = false))
    }

    @Test fun retriesWhenOnlyPriceSourceFails() {
        assertTrue(hasMarketWarmupFailure(priceRefreshSucceeded = false, hotCoinsRefreshSucceeded = true))
    }

    @Test fun retriesWhenOnlyHotCoinsSourceFails() {
        assertTrue(hasMarketWarmupFailure(priceRefreshSucceeded = true, hotCoinsRefreshSucceeded = false))
    }

    @Test fun doesNotRetryWhenBothMarketSourcesSucceed() {
        assertFalse(hasMarketWarmupFailure(priceRefreshSucceeded = true, hotCoinsRefreshSucceeded = true))
    }
}
