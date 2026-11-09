package com.airdrophunter.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletActivityRepositoryTest {
    private class FakeLocal : WalletActivityLocalDataSource {
        val guest = mutableMapOf<String, String>()
        val account = mutableMapOf<String, String>()
        override suspend fun read(key: String, guest: Boolean): String? =
            (if (guest) this.guest else account)[key]
        override suspend fun write(key: String, value: String, guest: Boolean) {
            (if (guest) this.guest else account)[key] = value
        }
    }

    private class FakeRemote : WalletActivityRemoteDataSource {
        var evmCalls = 0
        val chainCalls = mutableListOf<String>()
        override suspend fun getEvm(address: String): WalletActivityBatchDto {
            evmCalls++
            return WalletActivityBatchDto(
                transactions = emptyList(),
                networks_checked = listOf("ethereum", "base"),
                networks_unavailable = listOf("polygon")
            )
        }
        override suspend fun getChain(chain: String, address: String): List<WalletTransactionDto> {
            chainCalls += chain
            return emptyList()
        }
    }

    @Test
    fun evmAddressLoadsAllSupportedEvmNetworksThroughBatchEndpoint() = runBlocking {
        val remote = FakeRemote()
        val wallet = wallet("0x" + "1".repeat(40), WalletEcosystem.EVM)
        val result = WalletActivityRepository(remote).load(wallet)

        assertEquals(1, remote.evmCalls)
        assertTrue(remote.chainCalls.isEmpty())
        assertEquals(listOf("ethereum", "base"), result.networksChecked)
        assertEquals(listOf("polygon"), result.networksUnavailable)
    }

    @Test
    fun tronAddressUsesTronEndpoint() = runBlocking {
        val remote = FakeRemote()
        val wallet = wallet("T" + "1".repeat(33), WalletEcosystem.TRON)
        WalletActivityRepository(remote).load(wallet)

        assertEquals(listOf("tron"), remote.chainCalls)
        assertEquals(0, remote.evmCalls)
    }

    @Test
    fun bitcoinAddressUsesBitcoinEndpoint() = runBlocking {
        val remote = FakeRemote()
        val wallet = wallet("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", WalletEcosystem.BTC)
        WalletActivityRepository(remote).load(wallet)

        assertEquals(listOf("bitcoin"), remote.chainCalls)
        assertEquals(0, remote.evmCalls)
    }

    @Test
    fun bitcoinAddressFormatsAreRecognized() {
        assertEquals(WalletEcosystem.BTC, WalletEcosystem.fromAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
        assertEquals(WalletEcosystem.BTC, WalletEcosystem.fromAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"))
        assertEquals("OTHER", WalletEcosystem.fromAddress("bc1q0MixedCaseAddress"))
        // Legacy wallet rows explicitly marked SOL must not be reclassified as BTC
        // merely because Solana and Bitcoin both use Base58 address alphabets.
        assertEquals(WalletEcosystem.SOL, WalletEcosystem.fromAddress("1".repeat(32), WalletEcosystem.SOL))
        assertEquals(WalletEcosystem.BTC, WalletEcosystem.fromAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", WalletEcosystem.BTC))
    }

    @Test
    fun solanaAddressUsesSolanaEndpoint() = runBlocking {
        val remote = FakeRemote()
        val wallet = wallet("1".repeat(32), WalletEcosystem.SOL)
        WalletActivityRepository(remote).load(wallet)

        assertEquals(listOf("solana"), remote.chainCalls)
        assertEquals(0, remote.evmCalls)
    }

    private fun wallet(address: String, ecosystem: String) = WalletDto(
        id = "wallet-1",
        wallet_name = "Test",
        chain = ecosystem,
        network_icon = ecosystem,
        address = address
    )

    @Test
    fun guestRefreshStoresSnapshotOnlyInGuestCacheAndCanReadItImmediately() = runBlocking {
        val local = FakeLocal()
        val repository = WalletActivityRepository(FakeRemote(), local)
        val wallet = wallet("0x" + "1".repeat(40), WalletEcosystem.EVM)

        val refreshed = repository.refresh(wallet, guest = true)
        val cached = repository.readCached(wallet, guest = true)

        assertEquals(listOf("ethereum", "base"), refreshed.networksChecked)
        assertTrue(refreshed.fetchedAtEpochMillis > 0L)
        assertEquals(refreshed.networksChecked, cached?.networksChecked)
        assertTrue(local.guest.containsKey("wallet_activity:wallet-1"))
        assertTrue(local.account.isEmpty())
    }

    @Test
    fun accountRefreshUsesAccountScopedCacheAndDoesNotTouchGuestCache() = runBlocking {
        val local = FakeLocal()
        val repository = WalletActivityRepository(FakeRemote(), local)
        val wallet = wallet("T" + "1".repeat(33), WalletEcosystem.TRON)

        repository.refresh(wallet, guest = false)
        val cached = repository.readCached(wallet, guest = false)

        assertEquals(listOf("tron"), cached?.networksChecked)
        assertTrue(local.account.containsKey("wallet_activity:wallet-1"))
        assertTrue(local.guest.isEmpty())
    }

}
