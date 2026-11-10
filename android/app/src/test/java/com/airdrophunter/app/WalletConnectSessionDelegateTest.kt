package com.airdrophunter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletConnectSessionDelegateTest {
    @Test
    fun sameAddressOnDifferentChainsRemainsDistinct() {
        val address = "0x0123456789012345678901234567890123456789"
        val accounts = WalletConnectSessionDelegate.parseAccounts(
            listOf(
                "eip155:1:$address",
                "eip155:137:$address",
                "eip155:1:$address"
            ),
            "Test Wallet"
        )

        assertEquals(2, accounts.size)
        assertEquals(setOf("1", "137"), accounts.map { it.chainId }.toSet())
    }

    @Test
    fun activeAccountSelectionUsesTheRequestedChain() {
        val address = "0x0123456789012345678901234567890123456789"
        val accounts = WalletConnectSessionDelegate.parseAccounts(
            listOf("eip155:1:$address", "eip155:137:$address"),
            "Test Wallet"
        )

        assertEquals("137", WalletConnectSessionDelegate.accountForChain(accounts, "eip155:137")?.chainId)
        assertEquals("1", WalletConnectSessionDelegate.accountForChain(accounts, "1")?.chainId)
        assertNull(WalletConnectSessionDelegate.accountForChain(accounts, "eip155:10"))
    }
}
