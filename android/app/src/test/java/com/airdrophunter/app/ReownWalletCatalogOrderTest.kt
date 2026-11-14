package com.airdrophunter.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReownWalletCatalogOrderTest {
    @Test
    fun successfulConnectionCountSortsWalletsBeforeDefaultPriority() {
        val wallets = listOf(
            ReownWalletListing("a".repeat(64), "MetaMask", null, null, 0),
            ReownWalletListing("b".repeat(64), "Phantom", null, null, 4)
        )
        val usage = listOf(
            WalletUsageRecord("name:metamask", "MetaMask", 2, 90L),
            WalletUsageRecord("name:phantom", "Phantom", 8, 40L)
        )

        val ordered = CuratedWalletCatalog.orderBySuccessfulConnections(wallets, usage)

        assertEquals(listOf("Phantom", "MetaMask"), ordered.map { it.name })
    }

    @Test
    fun latestSuccessfulConnectionBreaksEqualCountTies() {
        val wallets = listOf(
            ReownWalletListing("a".repeat(64), "MetaMask", null, null, 0),
            ReownWalletListing("b".repeat(64), "Phantom", null, null, 4)
        )
        val usage = listOf(
            WalletUsageRecord("name:metamask", "MetaMask", 5, 100L),
            WalletUsageRecord("name:phantom", "Phantom", 5, 200L)
        )

        val ordered = CuratedWalletCatalog.orderBySuccessfulConnections(wallets, usage)

        assertEquals(listOf("Phantom", "MetaMask"), ordered.map { it.name })
    }

    @Test
    fun unusedWalletsKeepTheCuratedCatalogPriority() {
        val wallets = listOf(
            ReownWalletListing("a".repeat(64), "MetaMask", null, null, 0),
            ReownWalletListing("b".repeat(64), "Phantom", null, null, 4),
            ReownWalletListing("c".repeat(64), "Trust Wallet", null, null, 2)
        )

        val ordered = CuratedWalletCatalog.orderBySuccessfulConnections(wallets, emptyList())

        assertEquals(listOf("MetaMask", "Trust Wallet", "Phantom"), ordered.map { it.name })
    }
}
