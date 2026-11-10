package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommunityProjectCatalogTest {
    @Test fun exactNameSuggestsOfficialProject() {
        assertEquals("linea", CommunityProjectCatalog.findMatch("linea")?.officialId)
    }

    @Test fun customSuffixStillSuggestsButDoesNotAutoLink() {
        assertEquals("linea", CommunityProjectCatalog.findMatch("Linea测试号")?.officialId)
    }

    @Test fun unknownProjectDoesNotMatchCommunityCatalog() {
        assertNull(CommunityProjectCatalog.findMatch("myprivateproject"))
    }

    @Test fun matchingIsCaseInsensitive() {
        assertEquals("zksync", CommunityProjectCatalog.findMatch("ZKSync")?.officialId)
    }
}
