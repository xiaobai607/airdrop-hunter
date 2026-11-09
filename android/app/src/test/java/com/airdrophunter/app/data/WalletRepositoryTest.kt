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
        var lastAuthorization: String? = null
        var lastAuthMode: String? = null
        override suspend fun getWallets() = wallets.toList()
        override suspend fun getWallets(authorizationHeader: String?, authMode: String?) = wallets.toList().also { lastAuthorization = authorizationHeader; lastAuthMode = authMode }
        override suspend fun addWallet(request: WalletWriteRequest) {
            addCalls++
            wallets += WalletDto(
                "remote-$addCalls", request.wallet_name, request.chain, "E", request.address,
                tags = request.tags, notes = request.notes
            )
        }
        override suspend fun updateWallet(walletId: String, request: WalletWriteRequest) {
            val index = wallets.indexOfFirst { it.id == walletId }
            if (index >= 0) wallets[index] = wallets[index].copy(
                wallet_name = request.wallet_name,
                chain = request.chain,
                address = request.address,
                public_key = request.address,
                tags = request.tags,
                notes = request.notes
            )
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

    @Test fun backgroundWalletLoadUsesCapturedBearerToken() = runBlocking {
        val remote = Remote()
        WalletRepository(remote, Local()).loadForSession(false, "header.payload.signature")
        assertEquals("Bearer header.payload.signature", remote.lastAuthorization)
        assertEquals("captured", remote.lastAuthMode)
    }

    @Test fun authenticatedWalletsUseRemoteAndAccountCache() = runBlocking {
        val local = Local()
        val remote = Remote()
        val repository = WalletRepository(remote, local, accessTokenProvider = { "unit-test-session-token" })
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

    @Test fun guestWalletMetadataUpdatesLocallyWithoutRemoteCalls() = runBlocking {
        val local = Local()
        val remote = Remote()
        val repository = WalletRepository(remote, local)
        val added = repository.add(
            WalletWriteRequest("Main", WalletEcosystem.EVM, "0x" + "3".repeat(40)),
            guest = true
        )
        val wallet = added.single()
        val updated = repository.update(
            wallet,
            WalletWriteRequest(
                "Main vault", WalletEcosystem.EVM, wallet.address,
                tags = listOf("Airdrop", "Long-term"), notes = "Primary interaction address"
            ),
            guest = true,
            current = added
        )
        assertEquals("Main vault", updated.single().wallet_name)
        assertEquals(listOf("Airdrop", "Long-term"), updated.single().tags)
        assertEquals("Primary interaction address", updated.single().notes)
        assertEquals(0, remote.addCalls)
        assertTrue(local.guest.containsKey("wallets"))
        assertFalse(local.account.containsKey("wallets"))
    }

    @Test fun authenticatedWalletMetadataUpdatesThroughOwnerScopedRemoteAndAccountCache() = runBlocking {
        val local = Local()
        val remote = Remote()
        val repository = WalletRepository(remote, local, accessTokenProvider = { "unit-test-session-token" })
        val added = repository.add(
            WalletWriteRequest("Main", WalletEcosystem.EVM, "0x" + "4".repeat(40)),
            guest = false
        )
        val wallet = added.single()
        val updated = repository.update(
            wallet,
            WalletWriteRequest(
                "Trading", WalletEcosystem.EVM, wallet.address,
                tags = listOf("DeFi"), notes = "Managed account wallet"
            ),
            guest = false,
            current = added
        )
        assertEquals("Trading", updated.single().wallet_name)
        assertEquals(listOf("DeFi"), updated.single().tags)
        assertEquals("Managed account wallet", updated.single().notes)
        assertTrue(local.account.containsKey("wallets"))
        assertFalse(local.guest.containsKey("wallets"))
    }

}
