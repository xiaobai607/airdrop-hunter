package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityCatalogMergeTest {
    private fun project(id: String, name: String, active: Boolean = true, order: Int = 100) =
        CommunityProjectCatalogDto(
            project_official_id = id,
            name = name,
            chain = "Ethereum",
            logo = "",
            description = "",
            tags = emptyList(),
            website_url = "",
            is_active = active,
            sort_order = order
        )

    @Test fun explicitInactiveStatusIsRetainedForTheSameOfficialId() {
        val merged = mergeCommunityCatalog(
            listOf(project("linea", "Linea")),
            listOf(project("linea", "Linea Updated", active = false))
        )
        assertEquals(1, merged.size)
        assertEquals("linea", merged.single().project_official_id)
        assertEquals("Linea Updated", merged.single().name)
        assertFalse(merged.single().is_active)
    }

    @Test fun missingIdDoesNotBecomeInactiveOrDisappear() {
        val merged = mergeCommunityCatalog(
            listOf(project("linea", "Linea"), project("scroll", "Scroll")),
            listOf(project("linea", "Linea", active = true))
        )
        assertEquals(setOf("linea", "scroll"), merged.map { it.project_official_id }.toSet())
        assertTrue(merged.first { it.project_official_id == "scroll" }.is_active)
    }

    @Test fun incomingOfficialMetadataReplacesCachedMetadata() {
        val merged = mergeCommunityCatalog(
            listOf(project("linea", "Old Name", order = 20)),
            listOf(project("linea", "Official New Name", order = 2))
        )
        assertEquals("Official New Name", merged.single().name)
        assertEquals(2, merged.single().sort_order)
    }
}
