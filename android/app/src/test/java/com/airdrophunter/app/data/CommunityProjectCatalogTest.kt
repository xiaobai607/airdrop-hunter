package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.airdrophunter.app.matchesCommunityProjectStatus

class CommunityProjectCatalogTest {
    @Test fun pickerUsesDynamicDirectoryAndOnlyActiveProjects() {
        val directory = listOf(
            CommunityProjectCatalogDto("linea", "Linea", "Ethereum L2", "", "zkEVM", listOf("L2"), "https://linea.build/", is_active = true),
            CommunityProjectCatalogDto("old", "Old Project", "Ethereum", "", "", emptyList(), "", is_active = false)
        )
        val visible = directory.filter { it.is_active && it.matchesSearch("linea") }
        assertEquals(listOf("linea"), visible.map { it.project_official_id })
        assertTrue(directory.first { it.project_official_id == "linea" }.matchesSearch("L2"))
        assertFalse(directory.first { it.project_official_id == "old" }.is_active)
        assertTrue(matchesCommunityProjectStatus(true, showDelistedOnly = false))
        assertFalse(matchesCommunityProjectStatus(false, showDelistedOnly = false))
        assertTrue(matchesCommunityProjectStatus(false, showDelistedOnly = true))
        assertFalse(matchesCommunityProjectStatus(true, showDelistedOnly = true))
    }

    @Test fun customProjectHasNoCommunityAssociationUnlessExplicitlySelected() {
        val request = ProjectWriteRequest(name = "Private project", description = "", status = "tracking")
        assertEquals(null, request.community_official_id)
    }
}
