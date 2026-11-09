package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WalletTransactionIdentityTest {
    @Test
    fun evmIdentityNormalizesAddressAndHashCase() {
        val upper = WalletTransactionIdentity.pendingBillId(
            "ethereum", "0xABCDEF", "0xAABBCC", WalletEcosystem.EVM
        )
        val lower = WalletTransactionIdentity.pendingBillId(
            "ETHEREUM", "0xabcdef", "0xaabbcc", WalletEcosystem.EVM
        )
        assertEquals(upper, lower)
    }

    @Test
    fun solanaSignatureRetainsBase58CaseSensitivity() {
        val mixed = WalletTransactionIdentity.pendingBillId(
            "solana", "WalletABC", "AbCdEf", "SOLANA"
        )
        val lower = WalletTransactionIdentity.pendingBillId(
            "solana", "WalletABC", "abcdef", "SOLANA"
        )
        assertNotEquals(mixed, lower)
    }

    @Test
    fun identityIsStableAcrossGuestAndAccountScopes() {
        val guestId = WalletTransactionIdentity.pendingBillId(
            "bitcoin", "bc1qexample", "ABCDEF", "BITCOIN"
        )
        val accountId = WalletTransactionIdentity.pendingBillId(
            "bitcoin", "bc1qexample", "abcdef", "BITCOIN"
        )
        assertEquals(guestId, accountId)
    }
}
