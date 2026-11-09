package com.airdrophunter.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletActivityRepositoryTest {
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
        val wallet = wallet("bc1q" + "a".repeat(38), WalletEcosystem.BTC)
        WalletActivityRepository(remote).load(wallet)

        assertEquals(listOf("bitcoin"), remote.chainCalls)
        assertEquals(0, remote.evmCalls)
    }

    @Test
    fun bitcoinAddressFormatsAreRecognized() {
        assertEquals(WalletEcosystem.BTC, WalletEcosystem.fromAddress("1" + "A".repeat(25)))
        assertEquals(WalletEcosystem.BTC, WalletEcosystem.fromAddress("bc1q" + "a".repeat(38)))
        assertEquals("OTHER", WalletEcosystem.fromAddress("bc1qMixedCaseAddress"))
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
}
