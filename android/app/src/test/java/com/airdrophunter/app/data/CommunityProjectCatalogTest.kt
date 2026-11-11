package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.airdrophunter.app.AirdropFilterMetadata
import com.airdrophunter.app.AirdropFilterSelection
import com.airdrophunter.app.matchesAirdropFilters

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
        assertEquals(listOf("linea"), directory.filter { it.is_active }.map { it.project_official_id })
    }

    @Test fun airdropFiltersUseOrWithinGroupAndAndAcrossGroups() {
        val project = AirdropFilterMetadata(
            chain = "Ethereum L2",
            supportedChains = listOf("base", "arbitrum-one"),
            categories = listOf("layer2", "defi"),
            participationRequirements = listOf("bridge", "onchain_interaction"),
            fundingRequirement = "gas_only",
            campaignStage = "unknown",
            rewardTypes = emptyList(),
            riskFlags = listOf("contract_approval"),
            effortLevel = "recurring"
        )
        val selection = AirdropFilterSelection()
            .toggle("supported_chains", "base")
            .toggle("supported_chains", "linea")
            .toggle("participation_requirements", "bridge")
            .toggle("funding_requirement", "gas_only")
        assertTrue(matchesAirdropFilters(project, selection))
        assertFalse(matchesAirdropFilters(project, selection.toggle("campaign_stage", "claim_open")))
        assertTrue(matchesAirdropFilters(project, AirdropFilterSelection().toggle("campaign_stage", "unknown")))
        assertFalse(matchesAirdropFilters(project, AirdropFilterSelection().toggle("campaign_stage", "claim_open")))
    }

    @Test fun customProjectHasNoCommunityAssociationUnlessExplicitlySelected() {
        val request = ProjectWriteRequest(name = "Private project", description = "", status = "tracking")
        assertEquals(null, request.community_official_id)
    }
}
