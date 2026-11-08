package com.airdrophunter.app.data

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingRepositoryTest {
    @Test
    fun loadProjectsMergesSourcesAndDeduplicatesByLocalId() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        source.local["projects"] = Gson().toJson(listOf(project("same", "Remote cache"), project("second", "Second")))
        source.cache["projects"] = Gson().toJson(listOf(project("third", "Account cache")))
        source.local["pnl_projects"] = Gson().toJson(listOf(project("same", "Custom override"), project("fourth", "Fourth")))

        val result = AccountingRepository(source).loadProjects()

        assertEquals(listOf("same", "second", "third", "fourth"), result.map { it.id })
        assertEquals("Remote cache", result.first().name)
    }

    @Test
    fun loadProjectsPrefersJoinedRoomProjectForSameCommunityId() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        source.local["projects"] = Gson().toJson(
            listOf(project("server-linea", "Linea from server").copy(communityOfficialId = "linea"))
        )
        source.userProjects += UserProjectEntity(
            id = "user-linea",
            projectLocalId = "community:linea",
            projectOfficialId = "linea",
            projectName = "Linea",
            status = "tracking",
            totalGasCostUsd = 0.0,
            totalActualRewardUsd = 0.0,
            netProfitUsd = 0.0,
            interactionCount = 0,
            lastInteractionAt = 0L,
            createdAt = 1L,
            updatedAt = 1L
        )

        val result = AccountingRepository(source).loadProjects()

        assertEquals(1, result.size)
        assertEquals("community:linea", result.single().id)
        assertEquals("linea", result.single().communityOfficialId)
    }

    @Test
    fun saveLocalProjectReplacesMatchingLocalIdWithoutDeletingOthers() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        source.local["pnl_projects"] = Gson().toJson(listOf(project("one", "Old"), project("two", "Keep")))

        val result = AccountingRepository(source).saveLocalProject(project("one", "Updated"))

        assertEquals(listOf("two", "one"), result.map { it.id })
        val stored = Gson().fromJson<List<ProjectDto>>(source.local["pnl_projects"], object : com.google.gson.reflect.TypeToken<List<ProjectDto>>() {}.type)
        assertEquals("Updated", stored.last().name)
    }

    @Test
    fun onchainWalletTransactionRecordingIsDelegatedToLocalDataSource() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        val result = AccountingRepository(source).recordWalletTransaction(
            projectLocalId = "project-local",
            projectOfficialId = "community-project",
            projectName = "Project",
            txHash = "0xabc",
            walletAddress = "0xwallet",
            network = "ethereum",
            gasCostUsd = 1.25,
            actualRewardUsd = 2.50,
            timestamp = 123456L
        )
        assertTrue(result)
        assertEquals("0xabc", source.recordedTransactionHash)
        assertEquals("ethereum", source.recordedNetwork)
    }

    @Test
    fun walletActivityNetworkPolicyAllowsEveryIntegratedChainAndNormalizesCase() {
        val supported = listOf("ethereum", "bsc", "polygon", "arbitrum", "optimism", "base", "avalanche", "tron", "bitcoin")
        supported.forEach { network ->
            assertTrue("$network should be supported", WalletActivityNetworkPolicy.isSupported(network))
            assertTrue("$network should be case-insensitive", WalletActivityNetworkPolicy.isSupported(network.uppercase()))
        }
        assertTrue(WalletActivityNetworkPolicy.usesCaseInsensitiveAddress("BSC"))
        assertTrue(WalletActivityNetworkPolicy.usesCaseInsensitiveAddress("POLYGON"))
        assertTrue(!WalletActivityNetworkPolicy.usesCaseInsensitiveAddress("bitcoin"))
        assertTrue(!WalletActivityNetworkPolicy.usesCaseInsensitiveAddress("tron"))
        assertTrue(!WalletActivityNetworkPolicy.isSupported("unknown"))
    }

    @Test
    fun pendingBillOperationsAreDelegatedToLocalDataSource() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        val repository = AccountingRepository(source)
        val bill = PendingBillEntity("bill-1", "tx-1", "0xabc", 1.25, "EVM", 100L)
        repository.addPendingBill(bill)
        val confirmed = repository.confirmPendingBill("bill-1", "project-1", null, "Custom", 1.25, 0.0)
        repository.deletePendingBill("bill-1")

        assertEquals("bill-1", source.addedBill?.id)
        assertEquals("bill-1", source.deletedBillId)
        assertTrue(confirmed)
    }
}

private class FakeAccountingLocalDataSource : AccountingLocalDataSource {
    val local = mutableMapOf<String, String>()
    val cache = mutableMapOf<String, String>()
    val userProjects = mutableListOf<UserProjectEntity>()
    var addedBill: PendingBillEntity? = null
    var deletedBillId: String? = null
    var recordedTransactionHash: String? = null
    var recordedNetwork: String? = null
    override fun observePendingBills(): Flow<List<PendingBillEntity>> = flowOf(emptyList())
    override suspend fun readLocal(key: String): String? = local[key]
    override suspend fun read(key: String): String? = cache[key]
    override suspend fun readUserProjects(): List<UserProjectEntity> = userProjects
    override suspend fun writeLocal(key: String, payload: String) { local[key] = payload }
    override suspend fun addPendingBill(bill: PendingBillEntity) { addedBill = bill }
    override suspend fun deletePendingBill(id: String) { deletedBillId = id }
    override suspend fun confirmPendingBill(id: String, projectLocalId: String, projectOfficialId: String?, projectCustomName: String, gas: Double, reward: Double): Boolean = true
    override suspend fun recordWalletTransaction(projectLocalId: String, projectOfficialId: String?, projectName: String, txHash: String, walletAddress: String, network: String, gasCostUsd: Double, actualRewardUsd: Double, timestamp: Long): Boolean {
        recordedTransactionHash = txHash
        recordedNetwork = network
        return true
    }
}

private fun project(id: String, name: String) = ProjectDto(
    id = id, name = name, description = "", status = "tracking"
)
