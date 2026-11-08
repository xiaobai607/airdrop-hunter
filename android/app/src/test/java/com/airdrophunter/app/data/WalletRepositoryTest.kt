package com.airdrophunter.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRepositoryTest {
    private class Local : WalletLocalDataSource {
        val guest = mutableMapOf<String, String>()
        val account = mutableMapOf<String, String>()
        override suspend fun read(guest: Boolean) = (if (guest) this.guest else account)["wallets"]
        override suspend fun write(value: String, guest: Boolean) {
            (if (guest) this.guest else account)["wallets"] = value
        }
    }

    @Test fun guestAddressesAreStoredLocallyWithoutRemoteSync() = runBlocking {
        val local = Local()
        val repository = WalletRepository(local)
        val result = repository.add(
            WalletWriteRequest("Guest", WalletEcosystem.EVM, "0x1111111111111111111111111111111111111111"),
            true
        )
        assertEquals(1, result.size)
        assertTrue(local.guest.containsKey("wallets"))
        assertFalse(local.account.containsKey("wallets"))
    }

    @Test fun signedInAddressesRemainLocalUntilCloudSyncIsImplemented() = runBlocking {
        val local = Local()
        val repository = WalletRepository(local)
        val result = repository.add(
            WalletWriteRequest("Main", "Ethereum", "0x2222222222222222222222222222222222222222"),
            false
        )
        assertEquals(1, result.size)
        assertTrue(local.account.containsKey("wallets"))
        assertFalse(local.guest.containsKey("wallets"))
    }

    @Test fun evmAddressIsDeduplicatedPerNetworkAndCaseInsensitively() = runBlocking {
        val local = Local()
        val repository = WalletRepository(local)
        val address = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
        repository.add(WalletWriteRequest("A", "Ethereum", address), true)
        val sameNetwork = repository.add(WalletWriteRequest("B", "Ethereum", address.uppercase()), true)
        val otherNetwork = repository.add(WalletWriteRequest("Base", "Base", address), true)
        assertEquals(1, sameNetwork.size)
        assertEquals("B", sameNetwork.single().wallet_name)
        assertEquals(2, otherNetwork.size)
    }

    @Test fun addressPurposeTagsAndProjectAssociationsSurviveLocalPersistence() = runBlocking {
        val local = Local()
        val repository = WalletRepository(local)
        val request = WalletWriteRequest(
            wallet_name = "Airdrop Main",
            chain = "Ethereum",
            address = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd",
            note = "用于主要交互",
            purpose = WalletAddressPurpose.MAIN_INTERACTION,
            tags = listOf("主号", "Linea"),
            projectIds = listOf("linea", "scroll")
        )
        val saved = repository.add(request, true).single()
        val reloaded = repository.load(true).single()
        assertEquals(saved.id, reloaded.id)
        assertEquals("用于主要交互", reloaded.note)
        assertEquals(listOf("主号", "Linea"), reloaded.tags)
        assertEquals(listOf("linea", "scroll"), reloaded.projectIds)
    }
}
