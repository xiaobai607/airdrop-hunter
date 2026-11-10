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
    fun onlyExplicitlySupportedMethodsMayBeRelayedToAnExternalWallet() {
        listOf(
            "personal_sign", "eth_sign", "eth_signTypedData", "eth_signTypedData_v3",
            "eth_signTypedData_v4", "eth_signTransaction", "eth_sendTransaction"
        ).forEach { method ->
            assertTrue("$method should be eligible for external-wallet relay", DappProviderBridge.isSupportedExternalWalletMethod(method))
        }
        listOf(
            "eth_sendRawTransaction", "eth_sendUserOperation", "wallet_sendCalls",
            "wallet_sendTransaction", "eth_submitTransaction", "wallet_switchEthereumChain"
        ).forEach { method ->
            assertEquals(false, DappProviderBridge.isSupportedExternalWalletMethod(method))
        }
    }

    @Test
    fun rawSignedTransactionSubmissionRemainsBlocked() {
        listOf("eth_sendRawTransaction", "eth_sendUserOperation", "wallet_sendCalls", "wallet_sendTransaction")
            .forEach { method -> assertTrue(DappProviderBridge.isBlockedRawSubmissionMethod(method)) }
    }

    @Test
    fun signingRequestsMustTargetAnAccountApprovedInTheExternalWalletSession() {
        val account = "0x0123456789012345678901234567890123456789"
        val accounts = setOf(account)
        assertTrue(DappProviderBridge.isAuthorizedAccountRequest(
            "personal_sign", "0xdeadbeef", account, null, accounts
        ))
        assertTrue(DappProviderBridge.isAuthorizedAccountRequest(
            "eth_signTypedData_v4", account, "{}", null, accounts
        ))
        assertTrue(DappProviderBridge.isAuthorizedAccountRequest(
            "eth_sendTransaction", null, null, account, accounts
        ))
        assertEquals(false, DappProviderBridge.isAuthorizedAccountRequest(
            "eth_sendTransaction", null, null, "0x9999999999999999999999999999999999999999", accounts
        ))
        assertEquals(false, DappProviderBridge.isAuthorizedAccountRequest(
            "personal_sign", "0xdeadbeef", null, null, accounts
        ))
        assertEquals(false, DappProviderBridge.isAuthorizedAccountRequest(
            "personal_sign", account, account, null, accounts
        ))
    }

    @Test
    fun readOnlyAccountAndChainMethodsAreNotExternalSigningMethods() {
        listOf("eth_accounts", "eth_requestAccounts", "eth_chainId", "net_version", "eth_call").forEach { method ->
            assertEquals(false, DappProviderBridge.isSupportedExternalWalletMethod(method))
        }
    }
}
