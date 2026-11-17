package com.airdrophunter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class DappProviderBridgeTest {
    @Test
    fun x5PromptRequiresTheExactSecureDocumentUrl() {
        assertTrue(DappProviderBridge.isTrustedX5Prompt(
            "https://example.com/path?app=1#section", "https://example.com/path?app=1"
        ))
        assertTrue(DappProviderBridge.isTrustedX5Prompt(
            "https://example.com:8443/rpc", "https://example.com:8443/rpc"
        ))
        assertEquals(false, DappProviderBridge.isTrustedX5Prompt(
            "https://example.com/other", "https://example.com/path"
        ))
        assertEquals(false, DappProviderBridge.isTrustedX5Prompt(
            "https://example.com/path?app=2", "https://example.com/path?app=1"
        ))
        assertEquals(false, DappProviderBridge.isTrustedX5Prompt(
            "http://example.com/", "https://example.com/"
        ))
        assertEquals(false, DappProviderBridge.isTrustedX5Prompt(
            "https://evil.example/", "https://example.com/"
        ))
        assertEquals(false, DappProviderBridge.isTrustedX5Prompt(
            "https://example.com:8443/", "https://example.com/"
        ))
        assertEquals(false, DappProviderBridge.isTrustedX5Prompt(
            "https://user@example.com/", "https://example.com/"
        ))
        assertEquals(false, DappProviderBridge.isTrustedX5Prompt(
            "https://example.com/", "http://example.com/"
        ))
    }

    @Test
    fun x5ProviderUsesPromptOnlyToQueueAndReceivesAsyncResponses() {
        assertTrue(DappProviderBridge.X5_PROVIDER_SCRIPT.contains("window.prompt(\"AH_WALLET_RPC_V1\""))
        assertTrue(DappProviderBridge.X5_PROVIDER_SCRIPT.contains("response.id"))
        assertTrue(DappProviderBridge.X5_PROVIDER_SCRIPT.contains("ahWalletRpcResponse"))
        assertEquals(false, DappProviderBridge.X5_PROVIDER_SCRIPT.contains("JSON.parse(ack)"))
    }

    @Test
    fun x5ResponseDispatchSafelyEscapesPayload() {
        val payload = """{"id":9,"result":"a\\\\b"}"""
        val script = DappProviderBridge.x5ResponseDispatchScript(payload)
        assertTrue(script.contains("ahWalletRpcResponse"))
        assertTrue(script.contains("detail:"))
        assertTrue(script.contains(JSONObject.quote(payload)))
    }

    @Test
    fun x5ProviderReceivesWalletStateEventsAndUpdatesEip1193State() {
        assertTrue(DappProviderBridge.X5_PROVIDER_SCRIPT.contains("ahWalletRpcEvent"))
        assertTrue(DappProviderBridge.X5_PROVIDER_SCRIPT.contains("accountsChanged"))
        assertTrue(DappProviderBridge.X5_PROVIDER_SCRIPT.contains("chainChanged"))
        assertTrue(DappProviderBridge.X5_PROVIDER_SCRIPT.contains("emit(update.event, update.data)"))
        val accounts = """["0x0123456789012345678901234567890123456789"]"""
        val accountsScript = DappProviderBridge.x5EventDispatchScript("accountsChanged", accounts)
        assertTrue(accountsScript.contains("ahWalletRpcEvent"))
        assertTrue(accountsScript.contains("accountsChanged"))
        assertTrue(accountsScript.contains("0x0123456789012345678901234567890123456789"))
        val chainScript = DappProviderBridge.x5EventDispatchScript("chainChanged", JSONObject.quote("0x89"))
        assertTrue(chainScript.contains("chainChanged"))
        assertTrue(chainScript.contains("0x89"))
    }

    @Test
    fun x5PromptParserRequiresTheFixedProtocolPrefix() {
        val request = DappProviderBridge.parseX5Prompt(
            "AH_WALLET_RPC_V1",
            """{"id":7,"method":"eth_accounts","params":[]}"""
        )
        assertEquals(7L, request?.id)
        assertEquals("eth_accounts", request?.method)
        assertEquals(null, DappProviderBridge.parseX5Prompt(
            "UNTRUSTED",
            """{"id":7,"method":"eth_accounts"}"""
        ))
        assertEquals(null, DappProviderBridge.parseX5Prompt("AH_WALLET_RPC_V1", "not-json"))
        assertEquals(null, DappProviderBridge.parseX5Prompt("AH_WALLET_RPC_V1", null))
    }

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
    fun transactionPayloadCannotOverrideTheAuthorizedActiveChain() {
        assertTrue(DappProviderBridge.isTransactionChainCompatible("eth_sendTransaction", "0x1", "eip155:1"))
        assertTrue(DappProviderBridge.isTransactionChainCompatible("eth_signTransaction", "137", "eip155:137"))
        assertEquals(false, DappProviderBridge.isTransactionChainCompatible("eth_sendTransaction", "0x89", "eip155:1"))
        assertEquals(false, DappProviderBridge.isTransactionChainCompatible("eth_sendTransaction", "not-a-chain", "eip155:1"))
        assertTrue(DappProviderBridge.isTransactionChainCompatible("personal_sign", "0x89", "eip155:1"))
        assertTrue(DappProviderBridge.isTransactionChainCompatible("eth_sendTransaction", null, "eip155:1"))
    }

    @Test
    fun requestedAccountIsExtractedFromEachSupportedSigningShape() {
        val first = "0x0123456789012345678901234567890123456789"
        val second = "0x9999999999999999999999999999999999999999"
        assertEquals(first, DappProviderBridge.requestedAccountAddress("personal_sign", "0xdeadbeef", first, null))
        assertEquals(first, DappProviderBridge.requestedAccountAddress("eth_signTypedData_v4", first, "{}", null))
        assertEquals(second, DappProviderBridge.requestedAccountAddress("eth_sendTransaction", null, null, second))
        assertEquals(null, DappProviderBridge.requestedAccountAddress("personal_sign", first, first, null))
        assertEquals(null, DappProviderBridge.requestedAccountAddress("eth_sendTransaction", null, null, null))
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
    fun signingAndTransactionPayloadsAreValidatedBeforeWalletForwarding() {
        val account = "0x0123456789012345678901234567890123456789"
        assertTrue(DappProviderBridge.isValidExternalWalletParams(
            "personal_sign", JSONArray().put("0xdeadbeef").put(account)
        ))
        assertEquals(false, DappProviderBridge.isValidExternalWalletParams(
            "personal_sign", JSONArray().put("not-hex").put(account)
        ))
        assertEquals(false, DappProviderBridge.isValidExternalWalletParams(
            "eth_sign", JSONArray().put(account).put("0xabc")
        ))
        val typed = JSONObject().put("types", JSONObject().put("Mail", JSONArray()
            .put(JSONObject().put("name", "contents").put("type", "string"))))
            .put("primaryType", "Mail").put("domain", JSONObject()).put("message", JSONObject())
        assertTrue(DappProviderBridge.isValidExternalWalletParams(
            "eth_signTypedData_v4", JSONArray().put(account).put(typed)
        ))
        assertEquals(false, DappProviderBridge.isValidExternalWalletParams(
            "eth_signTypedData_v4", JSONArray().put(account).put("{bad-json")
        ))
        val tx = JSONObject().put("from", account).put("to", "0x9999999999999999999999999999999999999999")
            .put("value", "0x10").put("data", "0x")
        assertTrue(DappProviderBridge.isValidExternalWalletParams("eth_sendTransaction", JSONArray().put(tx)))
        assertEquals(false, DappProviderBridge.isValidExternalWalletParams(
            "eth_sendTransaction", JSONArray().put(JSONObject().put("from", "0x123").put("value", "10"))
        ))
    }

    @Test
    fun walletResponsesMustMatchExactRequestIdAndSessionContext() {
        assertTrue(DappProviderBridge.isMatchingExternalWalletResponse(
            101L, 101L, "topic-a", "topic-a", "personal_sign", "personal_sign",
            "eip155:1", "eip155:1"
        ))
        assertEquals(false, DappProviderBridge.isMatchingExternalWalletResponse(
            101L, 202L, "topic-a", "topic-a", "personal_sign", "personal_sign",
            "eip155:1", "eip155:1"
        ))
        assertEquals(false, DappProviderBridge.isMatchingExternalWalletResponse(
            101L, 101L, "topic-a", "topic-b", "personal_sign", "personal_sign",
            "eip155:1", "eip155:1"
        ))
        assertEquals(false, DappProviderBridge.isMatchingExternalWalletResponse(
            101L, 101L, "topic-a", "topic-a", "personal_sign", "eth_sendTransaction",
            "eip155:1", "eip155:1"
        ))
        assertEquals(false, DappProviderBridge.isMatchingExternalWalletResponse(
            101L, 101L, "topic-a", "topic-a", "personal_sign", "personal_sign",
            "eip155:1", "eip155:137"
        ))
        assertEquals(false, DappProviderBridge.isMatchingExternalWalletResponse(
            101L, 101L, "topic-a", "topic-a", "personal_sign", "personal_sign",
            "eip155:1", null
        ))
        assertEquals(false, DappProviderBridge.isMatchingExternalWalletResponse(
            101L, 101L, "topic-a", "topic-a", "personal_sign", "personal_sign",
            "eip155:1", ""
        ))
        assertEquals(false, DappProviderBridge.isMatchingExternalWalletResponse(
            null, 101L, "topic-a", "topic-a", "personal_sign", "personal_sign",
            "eip155:1", "eip155:1"
        ))
    }

    @Test
    fun readOnlyAccountAndChainMethodsAreNotExternalSigningMethods() {
        listOf("eth_accounts", "eth_requestAccounts", "eth_chainId", "net_version", "eth_call").forEach { method ->
            assertEquals(false, DappProviderBridge.isSupportedExternalWalletMethod(method))
        }
    }
}
