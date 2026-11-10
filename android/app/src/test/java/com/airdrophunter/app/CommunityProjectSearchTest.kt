package com.airdrophunter.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityProjectSearchTest {
    private val tags = listOf("L2", "零撸", "明牌空投")

    @Test
    fun blankQueryKeepsAllProjects() {
        assertTrue(matchesCommunityProjectQuery("Linea", "Ethereum L2", tags, "  "))
    }

    @Test
    fun matchesProjectNameChainAndTagsWithoutCaseSensitivity() {
        assertTrue(matchesCommunityProjectQuery("Linea", "Ethereum L2", tags, "linea"))
        assertTrue(matchesCommunityProjectQuery("Linea", "Ethereum L2", tags, "ethereum"))
        assertTrue(matchesCommunityProjectQuery("Linea", "Ethereum L2", tags, "零撸"))
        assertTrue(matchesCommunityProjectQuery("Linea", "Ethereum L2", tags, "L2"))
    }

    @Test
    fun rejectsUnmatchedQuery() {
        assertFalse(matchesCommunityProjectQuery("Linea", "Ethereum L2", tags, "Solana"))
    }
}
