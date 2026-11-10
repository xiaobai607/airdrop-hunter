package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityFavoriteSyncTest {
    @Test fun newerLocalFavoriteWinsDuringMerge() {
        val local = listOf(CommunityFavoriteStateDto("linea", "Linea", true, 20))
        val remote = listOf(CommunityFavoriteStateDto("linea", "Linea", false, 10))
        assertEquals(true, mergeCommunityFavoriteStates(local, remote).single().is_favorite)
    }

    @Test fun newerRemoteTombstoneWinsOverStaleLocalFavorite() {
        val local = listOf(CommunityFavoriteStateDto("scroll", "Scroll", true, 10))
        val remote = listOf(CommunityFavoriteStateDto("scroll", "Scroll", false, 20))
        val merged = mergeCommunityFavoriteStates(local, remote).single()
        assertEquals(false, merged.is_favorite)
        assertEquals(20L, merged.updated_at)
    }

    @Test fun mergeKeepsIndependentProjectsAndIsDeterministic() {
        val local = listOf(CommunityFavoriteStateDto("linea", "Linea", true, 1))
        val remote = listOf(CommunityFavoriteStateDto("scroll", "Scroll", true, 2))
        assertEquals(listOf("linea", "scroll"), mergeCommunityFavoriteStates(local, remote).map { it.project_official_id })
    }
}
