package com.airdrophunter.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityMetricWeightsTest {
    @Test fun zeroGasHasNoFabricatedRedSegment() {
        val (gas, reward) = communityMetricWeights(0.0, 100.0)
        assertEquals(0f, gas, 0.000001f)
        assertEquals(1f, reward, 0.000001f)
    }

    @Test fun zeroRewardHasNoFabricatedGreenSegment() {
        val (gas, reward) = communityMetricWeights(100.0, 0.0)
        assertEquals(1f, gas, 0.000001f)
        assertEquals(0f, reward, 0.000001f)
    }

    @Test fun comparableValuesUseExactProportions() {
        val (gas, reward) = communityMetricWeights(25.0, 75.0)
        assertEquals(0.25f, gas, 0.000001f)
        assertEquals(0.75f, reward, 0.000001f)
    }

    @Test fun emptyOrInvalidValuesProduceNoBarWeights() {
        assertEquals(0f to 0f, communityMetricWeights(0.0, 0.0))
        assertEquals(0f to 0f, communityMetricWeights(Double.NaN, Double.POSITIVE_INFINITY))
    }
}
