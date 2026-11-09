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

    @Test
    fun signingAndTransactionSubmissionMethodsAreAlwaysBlocked() {
        listOf(
            "personal_sign", "eth_sign", "eth_signTypedData_v4",
            "eth_signTransaction", "eth_sendTransaction", "eth_sendRawTransaction",
            "eth_sendUserOperation", "wallet_sendCalls", "wallet_sendTransaction"
        ).forEach { method ->
            assertTrue("$method must be blocked", DappProviderBridge.isSigningOrTransactionMethod(method))
        }
    }

    @Test
    fun readOnlyAccountAndChainMethodsAreNotClassifiedAsSigning() {
        listOf("eth_accounts", "eth_requestAccounts", "eth_chainId", "net_version", "eth_call").forEach { method ->
            assertEquals(false, DappProviderBridge.isSigningOrTransactionMethod(method))
        }
    }
}
