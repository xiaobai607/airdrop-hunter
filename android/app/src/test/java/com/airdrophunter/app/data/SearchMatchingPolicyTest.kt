package com.airdrophunter.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatchingPolicyTest {
    @Test fun searchNormalizationIgnoresCaseSpacesAndPunctuation() {
        assertTrue(fuzzySearchMatch("b t c", "Bitcoin (BTC)"))
        assertTrue(fuzzySearchMatch("ETH", "Ethereum"))
    }

    @Test fun fuzzyMatchingSupportsSubsequenceAndChineseNames() {
        assertTrue(fuzzySearchMatch("btcn", "Bitcoin"))
        assertTrue(fuzzySearchMatch("以太坊", "Ethereum / 以太坊"))
        assertFalse(fuzzySearchMatch("solana", "Ethereum"))
    }

    @Test fun blankQueriesNeverMatch() {
        assertFalse(fuzzySearchMatch("   ", "Bitcoin BTC"))
    }
}
