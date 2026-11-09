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
    fun saveLocalProjectReplacesMatchingLocalIdWithoutDeletingOthers() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        source.local["pnl_projects"] = Gson().toJson(listOf(project("one", "Old"), project("two", "Keep")))

        val result = AccountingRepository(source).saveLocalProject(project("one", "Updated"))

        assertEquals(listOf("two", "one"), result.map { it.id })
        val stored = Gson().fromJson<List<ProjectDto>>(source.local["pnl_projects"], object : com.google.gson.reflect.TypeToken<List<ProjectDto>>() {}.type)
        assertEquals("Updated", stored.last().name)
    }

    @Test
    fun demoLedgerRowsAreRecognizedAndNeverTreatedAsSyncable() {
        val demo = PersonalLedgerWriteRequest(
            client_record_id = "demo-row",
            project_id = null,
            project_name = "Demo",
            chain = "ethereum",
            wallet_address = "0x" + "1".repeat(40),
            tx_hash = "0x" + "a".repeat(64),
            interaction_method = "模拟测试",
            expected_gas_cost_usd = 0.0,
            expected_reward_usd = null,
            actual_gas_cost_usd = 0.0,
            actual_reward_usd = 0.0,
            occurred_at = "2026-11-09T00:00:00Z"
        )
        assertTrue(isDemoPersonalLedgerEntry(demo))
        assertTrue(isDemoPersonalLedgerEntry(demo.copy(interaction_method = "chain import", extra = mapOf("is_demo" to "true"))))
        assertTrue(!isDemoPersonalLedgerEntry(demo.copy(interaction_method = "chain import", extra = emptyMap())))
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
    var addedBill: PendingBillEntity? = null
    var deletedBillId: String? = null
    override fun observePendingBills(): Flow<List<PendingBillEntity>> = flowOf(emptyList())
    override suspend fun readLocal(key: String): String? = local[key]
    override suspend fun read(key: String): String? = cache[key]
    override suspend fun writeLocal(key: String, payload: String) { local[key] = payload }
    override suspend fun addPendingBill(bill: PendingBillEntity) { addedBill = bill }
    override suspend fun deletePendingBill(id: String) { deletedBillId = id }
    override suspend fun hasRecordedTransaction(network: String, walletAddress: String, txHash: String): Boolean = false
    override suspend fun confirmPendingBill(id: String, projectLocalId: String, projectOfficialId: String?, projectCustomName: String, gas: Double, reward: Double, assetBreakdownJson: String?): Boolean = true
}

private fun project(id: String, name: String) = ProjectDto(
    id = id, name = name, description = "", status = "tracking"
)
