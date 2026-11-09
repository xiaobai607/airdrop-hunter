package com.airdrophunter.app

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun trustedOriginMustMatchTheTopLevelHttpsHostAndPort() {
        assertTrue(
            DappProviderBridge.isTrustedMainFrame(
                Uri.parse("https://dapp.example"),
                "https://dapp.example/path"
            )
        )
        assertFalse(
            DappProviderBridge.isTrustedMainFrame(
                Uri.parse("https://evil.example"),
                "https://dapp.example/path"
            )
        )
        assertFalse(
            DappProviderBridge.isTrustedMainFrame(
                Uri.parse("http://dapp.example"),
                "https://dapp.example/path"
            )
        )
        assertFalse(
            DappProviderBridge.isTrustedMainFrame(
                Uri.parse("https://dapp.example:444"),
                "https://dapp.example/path"
            )
        )
    }
}
