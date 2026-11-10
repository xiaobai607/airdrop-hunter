package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    fun guestLedgerNeverCallsPrivateCloudAndKeepsLocalRows() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        val guestRow = ledgerWrite("guest-row")
        source.local["personal_ledger"] = Gson().toJson(listOf(guestRow))
        val remote = FakePersonalLedgerRemoteDataSource()
        val repository = AccountingRepository(
            source,
            accessTokenProvider = { null },
            personalLedgerRemote = remote
        )

        assertTrue(repository.syncPersonalLedger())
        assertEquals(0, remote.getCalls)
        assertTrue(remote.uploaded.isEmpty())
        assertEquals(Gson().toJson(listOf(guestRow)), source.local["personal_ledger"])
    }

    @Test
    fun currentReleaseDisablesPrivateCloudSyncEvenWhenLegacyTokenExists() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        val localRow = ledgerWrite("keep-local")
        source.sessionLocal["token-account-A"] = mutableMapOf("personal_ledger" to Gson().toJson(listOf(localRow)))
        val remote = FakePersonalLedgerRemoteDataSource()
        val repository = AccountingRepository(
            source,
            accessTokenProvider = { "token-account-A" },
            personalLedgerRemote = remote
        )

        assertTrue(repository.syncPersonalLedger())
        assertEquals(0, remote.getCalls)
        assertTrue(remote.uploaded.isEmpty())
        assertEquals(Gson().toJson(listOf(localRow)), source.sessionLocal.getValue("token-account-A").getValue("personal_ledger"))
    }

    @Test
    fun syncUsesCapturedAccountAndPreservesRowsAddedDuringNetworkWork() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        var activeToken = "token-account-A"
        val localRow = ledgerWrite("local-row", mapOf("asset_breakdown" to "native-token-json"))
        val staleDuplicate = ledgerWrite("cloud-row", mapOf("version" to "stale"))
        val demoRow = ledgerWrite("demo-row", mapOf("is_demo" to "true"))
        val addedDuringSync = ledgerWrite("added-during-sync")
        source.sessionLocal["token-account-A"] = mutableMapOf(
            "personal_ledger" to Gson().toJson(listOf(localRow, staleDuplicate, demoRow))
        )
        val accountBRow = ledgerWrite("account-B-private")
        source.sessionLocal["token-account-B"] = mutableMapOf(
            "personal_ledger" to Gson().toJson(listOf(accountBRow))
        )
        val canonicalCloudRow = ledgerEntry("cloud-row", mapOf("version" to "cloud-canonical"))
        val remote = FakePersonalLedgerRemoteDataSource(mutableListOf(canonicalCloudRow))
        remote.onUpsert = {
            activeToken = "token-account-B"
            source.sessionLocal["token-account-A"]?.set(
                "personal_ledger",
                Gson().toJson(listOf(localRow, staleDuplicate, demoRow, addedDuringSync))
            )
        }
        val repository = AccountingRepository(
            source,
            accessTokenProvider = { activeToken },
            personalLedgerRemote = remote,
            privateCloudSyncEnabled = true
        )

        assertTrue(repository.syncPersonalLedger())

        assertTrue(remote.authorizationHeaders.all { it == "Bearer token-account-A" })
        assertEquals(listOf("local-row"), remote.uploaded.map { it.client_record_id })
        val accountA = Gson().fromJson<List<PersonalLedgerWriteRequest>>(
            source.sessionLocal.getValue("token-account-A").getValue("personal_ledger"),
            object : TypeToken<List<PersonalLedgerWriteRequest>>() {}.type
        )
        assertEquals(
            setOf("cloud-row", "local-row", "demo-row", "added-during-sync"),
            accountA.map { it.client_record_id }.toSet()
        )
        assertEquals("cloud-canonical", accountA.single { it.client_record_id == "cloud-row" }.extra["version"])
        assertEquals(
            localRow.extra["asset_breakdown"],
            accountA.single { it.client_record_id == "local-row" }.extra["asset_breakdown"]
        )
        val accountB = Gson().fromJson<List<PersonalLedgerWriteRequest>>(
            source.sessionLocal.getValue("token-account-B").getValue("personal_ledger"),
            object : TypeToken<List<PersonalLedgerWriteRequest>>() {}.type
        )
        assertEquals(listOf("account-B-private"), accountB.map { it.client_record_id })
    }

    @Test
    fun oversizedCloudLedgerDoesNotOverwriteLocalCacheOrUploadRows() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        val localRow = ledgerWrite("keep-local")
        source.sessionLocal["token-account-A"] = mutableMapOf(
            "personal_ledger" to Gson().toJson(listOf(localRow))
        )
        val remote = FakePersonalLedgerRemoteDataSource(alwaysFullPages = true)
        val repository = AccountingRepository(
            source,
            accessTokenProvider = { "token-account-A" },
            personalLedgerRemote = remote,
            privateCloudSyncEnabled = true
        )

        assertTrue(!repository.syncPersonalLedger())
        assertEquals(Gson().toJson(listOf(localRow)), source.sessionLocal.getValue("token-account-A").getValue("personal_ledger"))
        assertTrue(remote.uploaded.isEmpty())
    }


    @Test
    fun unknownExpectedGasRemainsNullDuringCloudUploadAndLocalMerge() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        val unknownEstimate = ledgerWrite("unknown-gas").copy(expected_gas_cost_usd = null)
        source.sessionLocal["token-account-A"] = mutableMapOf(
            "personal_ledger" to Gson().toJson(listOf(unknownEstimate))
        )
        val remote = FakePersonalLedgerRemoteDataSource()
        val repository = AccountingRepository(source, accessTokenProvider = { "token-account-A" }, personalLedgerRemote = remote, privateCloudSyncEnabled = true)

        assertTrue(repository.syncPersonalLedger())
        assertEquals(null, remote.uploaded.single().expected_gas_cost_usd)
        val restored = Gson().fromJson<List<PersonalLedgerWriteRequest>>(
            source.sessionLocal.getValue("token-account-A").getValue("personal_ledger"),
            object : TypeToken<List<PersonalLedgerWriteRequest>>() {}.type
        )
        assertEquals(null, restored.single().expected_gas_cost_usd)
    }

    @Test
    fun cloudRestorePaginatesAndPreservesUnknownExpectedGas() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        val cloudRows = (0 until 501).map { index ->
            val request = ledgerWrite("cloud-$index").copy(expected_gas_cost_usd = if (index == 0) null else 1.0)
            ledgerEntry("cloud-$index", request = request)
        }.toMutableList()
        val remote = FakePersonalLedgerRemoteDataSource(cloudEntries = cloudRows)
        val repository = AccountingRepository(source, accessTokenProvider = { "token-account-A" }, personalLedgerRemote = remote)

        assertTrue(repository.syncPersonalLedger())
        assertEquals(2, remote.getCalls)
        val restored = Gson().fromJson<List<PersonalLedgerWriteRequest>>(
            source.sessionLocal.getValue("token-account-A").getValue("personal_ledger"),
            object : TypeToken<List<PersonalLedgerWriteRequest>>() {}.type
        )
        assertEquals(501, restored.size)
        assertEquals(null, restored.single { it.client_record_id == "cloud-0" }.expected_gas_cost_usd)
    }

    @Test
    fun pendingBillOperationsAreDelegatedToLocalDataSource() = runBlocking {
        val source = FakeAccountingLocalDataSource()
        val repository = AccountingRepository(source)
        val bill = PendingBillEntity("bill-1", "tx-1", "0xabc", 1.25, "EVM", 100L)
        repository.addPendingBill(bill)
        val confirmed = repository.confirmPendingBill("bill-1", "project-1", null, "Custom", 1.25, 0.0, publicContributionOptIn = true)
        repository.deletePendingBill("bill-1")

        assertEquals("bill-1", source.addedBill?.id)
        assertEquals("bill-1", source.deletedBillId)
        assertTrue(confirmed)
        assertTrue(source.lastPublicContributionOptIn)
    }
}

private class FakeAccountingLocalDataSource : AccountingLocalDataSource {
    val local = mutableMapOf<String, String>()
    val cache = mutableMapOf<String, String>()
    val sessionLocal = mutableMapOf<String, MutableMap<String, String>>()
    override suspend fun readLocalForToken(key: String, accessToken: String): String? =
        sessionLocal[accessToken]?.get(key) ?: local[key]
    override suspend fun mergePersonalLedgerForToken(
        key: String,
        cloudEntries: List<PersonalLedgerWriteRequest>,
        accessToken: String
    ): Boolean {
        val localRows = readLocalForToken(key, accessToken)?.let { raw ->
            runCatching {
                Gson().fromJson<List<PersonalLedgerWriteRequest>>(
                    raw, object : TypeToken<List<PersonalLedgerWriteRequest>>() {}.type
                )
            }.getOrNull()
        }.orEmpty()
        val cloudIds = cloudEntries.mapTo(mutableSetOf()) { it.client_record_id }
        val merged = (cloudEntries + localRows.filterNot { it.client_record_id in cloudIds })
            .distinctBy { it.client_record_id }
        sessionLocal.getOrPut(accessToken) { mutableMapOf() }[key] = Gson().toJson(merged)
        return true
    }
    var addedBill: PendingBillEntity? = null
    var deletedBillId: String? = null
    var lastPublicContributionOptIn: Boolean = false
    override fun observePendingBills(): Flow<List<PendingBillEntity>> = flowOf(emptyList())
    override suspend fun readLocal(key: String): String? = local[key]
    override suspend fun read(key: String): String? = cache[key]
    override suspend fun writeLocal(key: String, payload: String) { local[key] = payload }
    override suspend fun addPendingBill(bill: PendingBillEntity) { addedBill = bill }
    override suspend fun deletePendingBill(id: String) { deletedBillId = id }
    override suspend fun hasRecordedTransaction(network: String, walletAddress: String, txHash: String): Boolean = false
    override suspend fun confirmPendingBill(id: String, projectLocalId: String, projectOfficialId: String?, projectCustomName: String, gas: Double, reward: Double, assetBreakdownJson: String?, publicContributionOptIn: Boolean): Boolean {
        lastPublicContributionOptIn = publicContributionOptIn
        return true
    }
}

private fun project(id: String, name: String) = ProjectDto(
    id = id, name = name, description = "", status = "tracking"
)

private class FakePersonalLedgerRemoteDataSource(
    var cloudEntries: MutableList<PersonalLedgerEntryDto> = mutableListOf(),
    private val alwaysFullPages: Boolean = false
) : PersonalLedgerRemoteDataSource {
    var getCalls = 0
    val authorizationHeaders = mutableListOf<String>()
    val uploaded = mutableListOf<PersonalLedgerWriteRequest>()
    var onUpsert: (() -> Unit)? = null

    override suspend fun getPersonalLedger(
        authorizationHeader: String,
        limit: Int,
        offset: Int
    ): List<PersonalLedgerEntryDto> {
        getCalls++
        authorizationHeaders += authorizationHeader
        return if (alwaysFullPages) {
            List(limit) { index -> ledgerEntry("overflow-${offset + index}") }
        } else {
            cloudEntries.drop(offset).take(limit)
        }
    }

    override suspend fun upsertPersonalLedger(
        authorizationHeader: String,
        clientRecordId: String,
        request: PersonalLedgerWriteRequest
    ): PersonalLedgerEntryDto {
        authorizationHeaders += authorizationHeader
        uploaded += request
        onUpsert?.invoke()
        val saved = ledgerEntry(clientRecordId, request.extra, request)
        cloudEntries.removeAll { it.client_record_id == clientRecordId }
        cloudEntries += saved
        return saved
    }
}

private fun ledgerWrite(id: String, extra: Map<String, String> = emptyMap()) =
    PersonalLedgerWriteRequest(
        client_record_id = id,
        project_id = "project-$id",
        project_name = "Project $id",
        chain = "ethereum",
        wallet_address = "0x" + "1".repeat(40),
        tx_hash = "0x" + "a".repeat(64),
        interaction_method = "chain import",
        expected_gas_cost_usd = 1.0,
        expected_reward_usd = null,
        actual_gas_cost_usd = 0.5,
        actual_reward_usd = 2.0,
        occurred_at = "2026-11-09T00:00:00Z",
        extra = extra
    )

private fun ledgerEntry(
    id: String,
    extra: Map<String, String> = emptyMap(),
    request: PersonalLedgerWriteRequest? = null
) = PersonalLedgerEntryDto(
    id = "server-$id",
    client_record_id = id,
    project_id = request?.project_id ?: "project-$id",
    project_name = request?.project_name ?: "Project $id",
    chain = request?.chain ?: "ethereum",
    wallet_address = request?.wallet_address ?: "0x" + "2".repeat(40),
    tx_hash = request?.tx_hash ?: "0x" + "b".repeat(64),
    interaction_method = request?.interaction_method ?: "chain import",
    expected_gas_cost_usd = request?.expected_gas_cost_usd ?: if (request == null) 1.0 else null,
    expected_reward_usd = request?.expected_reward_usd,
    actual_gas_cost_usd = request?.actual_gas_cost_usd ?: 0.5,
    actual_reward_usd = request?.actual_reward_usd ?: 2.0,
    occurred_at = request?.occurred_at ?: "2026-11-09T00:00:00Z",
    extra = request?.extra ?: extra
)
