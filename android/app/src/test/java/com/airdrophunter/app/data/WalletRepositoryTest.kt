package com.airdrophunter.app.data

import com.google.gson.Gson
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
    private class Remote : WalletRemoteDataSource {
        val wallets = mutableListOf<WalletDto>()
        var addCalls = 0
        override suspend fun getWallets() = wallets.toList()
        override suspend fun addWallet(request: WalletWriteRequest) {
            addCalls++
            wallets += WalletDto("remote-$addCalls", request.wallet_name, request.chain, "E", request.address)
        }
        override suspend fun deleteWallet(walletId: String) { wallets.removeAll { it.id == walletId } }
    }

    @Test fun guestWalletsNeverCallRemoteAndStayInGuestCache() = runBlocking {
        val local = Local()
        val remote = Remote()
        val repository = WalletRepository(remote, local)
        val result = repository.add(WalletWriteRequest("Guest", WalletEcosystem.EVM, "0x1111111111111111111111111111111111111111"), true)
        assertEquals(1, result.size)
        assertEquals(0, remote.addCalls)
        assertTrue(local.guest.containsKey("wallets"))
        assertFalse(local.account.containsKey("wallets"))
    }

    @Test fun authenticatedWalletsUseRemoteAndAccountCache() = runBlocking {
        val local = Local()
        val remote = Remote()
        val repository = WalletRepository(remote, local)
        val result = repository.add(WalletWriteRequest("Main", WalletEcosystem.EVM, "0x2222222222222222222222222222222222222222"), false)
        assertEquals(1, result.size)
        assertEquals(1, remote.addCalls)
        assertTrue(local.account.containsKey("wallets"))
        assertFalse(local.guest.containsKey("wallets"))
    }

    @Test fun guestEvmAddressIsDeduplicatedCaseInsensitively() = runBlocking {
        val local = Local()
        val repository = WalletRepository(Remote(), local)
        val first = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
        repository.add(WalletWriteRequest("A", WalletEcosystem.EVM, first), true)
        val result = repository.add(WalletWriteRequest("B", WalletEcosystem.EVM, first.uppercase()), true)
        assertEquals(1, result.size)
        assertEquals("B", result.single().wallet_name)
    }
}
