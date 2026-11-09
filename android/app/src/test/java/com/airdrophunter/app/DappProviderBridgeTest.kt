package com.airdrophunter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DappProviderBridgeTest {
    @Test
    fun originRulesUseTheWebViewSupportedGlobalWildcardSyntax() {
        assertEquals(setOf("*"), DappProviderBridge.HTTPS_ORIGIN_RULES)
    }

    @Test
    fun providerScriptDoesNotInstallInSubframes() {
        assertTrue(DappProviderBridge.PROVIDER_SCRIPT.contains("window.top !== window"))
    }
}
