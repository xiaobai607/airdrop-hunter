package com.airdrophunter.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletActivityRepositoryTest {
    private class FakeLocal : WalletActivityLocalDataSource {
        val guest = mutableMapOf<String, String>()
        val account = mutableMapOf<String, String>()
        val sessionRows = mutableMapOf<String, String>()
        override suspend fun read(key: String, guest: Boolean): String? =
            (if (guest) this.guest else account)[key]
        override suspend fun write(key: String, value: String, guest: Boolean) {
            (if (guest) this.guest else account)[key] = value
        }
        override suspend fun readForSession(key: String, guest: Boolean, accessToken: String?): String? =
            sessionRows["${if (guest) "guest" else accessToken}:$key"]
        override suspend fun writeForSession(key: String, value: String, guest: Boolean, accessToken: String?) {
            sessionRows["${if (guest) "guest" else accessToken}:$key"] = value
        }
    }

    private class FakeRemote : WalletActivityRemoteDataSource {
        var evmCalls = 0
        var failure: Exception? = null
        val chainCalls = mutableListOf<String>()
        val authorizationHeaders = mutableListOf<String?>()
        val authModes = mutableListOf<String?>()
        override suspend fun getEvm(address: String, authorizationHeader: String?, authMode: String?): WalletActivityBatchDto {
            authorizationHeaders += authorizationHeader
            authModes += authMode
            evmCalls++
            return WalletActivityBatchDto(
                transactions = emptyList(),
                networks_checked = listOf("ethereum", "base"),
                networks_unavailable = listOf("polygon")
            )
        }
        override suspend fun getChain(chain: String, address: String, authorizationHeader: String?, authMode: String?): List<WalletTransactionDto> {
            authorizationHeaders += authorizationHeader
            authModes += authMode
            chainCalls += chain
            failure?.let { throw it }
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

    @Test
    fun walletActivityRefreshUsesSessionScopedPayloadAndScopedCoordinatorMetadata() = runBlocking {
        val local = FakeLocal()
        val remote = FakeRemote()
        val metadata = mutableMapOf<Pair<String, String>, CacheMetadataEntity>()
        val engine = CacheRefreshCoordinator(
            readMetadata = { key, source -> metadata[key to source] },
            writeMetadata = { row -> metadata[row.cacheKey to row.source] = row }
        )
        val repository = WalletActivityRepository(remote, local, cacheRefresh = engine)
        val wallet = wallet("T" + "2".repeat(33), WalletEcosystem.TRON)

        val first = repository.refresh(wallet, guest = true, accessToken = null)
        val cached = repository.readCached(wallet, guest = true, accessToken = null)
        assertEquals(first.networksChecked, cached?.networksChecked)
        assertTrue(local.sessionRows.containsKey("guest:wallet_activity:wallet-1"))

        // Ordinary refresh is cache-first; manual refresh is throttled for 30 seconds.
        repository.refresh(wallet, guest = true, accessToken = null)
        assertEquals(1, remote.chainCalls.size)

        val firstManual = repository.refresh(wallet, guest = true, accessToken = null, manual = true)
        assertEquals(2, remote.chainCalls.size)
        assertTrue(!firstManual.throttled)

        val repeatedManual = repository.refresh(wallet, guest = true, accessToken = null, manual = true)
        assertEquals(2, remote.chainCalls.size)
        assertTrue(repeatedManual.throttled)
        assertEquals(1, metadata.size)
        assertTrue(metadata.keys.single().first.startsWith("guest:wallet-1:tron"))
    }


    @Test
    fun staleCachedSnapshotExposesProviderFailureInsteadOfLookingSuccessful() = runBlocking {
        val local = FakeLocal()
        val remote = FakeRemote().apply { failure = IllegalStateException("provider unavailable") }
        val wallet = wallet("T" + "3".repeat(33), WalletEcosystem.TRON)
        val key = "guest:wallet_activity:wallet-1"
        local.sessionRows[key] = com.google.gson.Gson().toJson(
            WalletActivitySnapshot(transactions = emptyList(), networksChecked = listOf("tron"))
        )
        val repository = WalletActivityRepository(remote, local)

        val result = repository.refresh(wallet, guest = true, accessToken = null)

        assertTrue(result.stale)
        assertEquals("provider unavailable", result.errorMessage)
        assertEquals(listOf("tron"), repository.readCached(wallet, true, null)?.networksChecked)
    }

    private fun wallet(address: String, ecosystem: String) = WalletDto(
        id = "wallet-1",
        wallet_name = "Test",
        chain = ecosystem,
        network_icon = ecosystem,
        address = address
    )

    @Test
    fun guestRefreshExplicitlySuppressesAnyLaterAccountTokenInjection() = runBlocking {
        val remote = FakeRemote()
        val repository = WalletActivityRepository(remote)
        repository.refresh(wallet("T" + "1".repeat(33), WalletEcosystem.TRON), true, null)
        assertEquals(listOf(null), remote.authorizationHeaders)
        assertEquals(listOf("anonymous"), remote.authModes)
    }

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
        assertTrue(local.sessionRows.containsKey("guest:wallet_activity:wallet-1"))
        assertTrue(local.sessionRows.keys.none { it.startsWith("account-") })
    }

    @Test
    fun accountRefreshUsesAccountScopedCacheAndDoesNotTouchGuestCache() = runBlocking {
        val local = FakeLocal()
        val repository = WalletActivityRepository(FakeRemote(), local)
        val wallet = wallet("T" + "1".repeat(33), WalletEcosystem.TRON)

        repository.refresh(wallet, guest = false, accessToken = "account-test")
        val cached = repository.readCached(wallet, guest = false, accessToken = "account-test")

        assertEquals(listOf("tron"), cached?.networksChecked)
        assertTrue(local.sessionRows.containsKey("account-test:wallet_activity:wallet-1"))
        assertTrue(local.sessionRows.keys.none { it.startsWith("guest:") })
    }

}
