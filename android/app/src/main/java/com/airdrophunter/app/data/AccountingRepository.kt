package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

interface AccountingLocalDataSource {
    fun observePendingBills(): Flow<List<PendingBillEntity>>
    suspend fun readLocal(key: String): String?
    suspend fun read(key: String): String?
    suspend fun writeLocal(key: String, payload: String)
    suspend fun addPendingBill(bill: PendingBillEntity)
    suspend fun deletePendingBill(id: String)
    suspend fun confirmPendingBill(
        id: String,
        projectLocalId: String,
        projectOfficialId: String?,
        projectCustomName: String,
        gas: Double,
        reward: Double
    ): Boolean
}

class CacheStoreAccountingLocalDataSource : AccountingLocalDataSource {
    override fun observePendingBills(): Flow<List<PendingBillEntity>> = CacheStore.observePendingBills()
    override suspend fun readLocal(key: String): String? = CacheStore.readLocal(key)
    override suspend fun read(key: String): String? = CacheStore.read(key)
    override suspend fun writeLocal(key: String, payload: String) = CacheStore.writeLocal(key, payload)
    override suspend fun addPendingBill(bill: PendingBillEntity) {
        CacheStore.addPendingBill(bill)
    }
    override suspend fun deletePendingBill(id: String) { CacheStore.deletePendingBill(id) }
    override suspend fun confirmPendingBill(
        id: String,
        projectLocalId: String,
        projectOfficialId: String?,
        projectCustomName: String,
        gas: Double,
        reward: Double
    ): Boolean = CacheStore.confirmPendingBill(
        id, projectLocalId, projectOfficialId, projectCustomName, gas, reward
    )
}

class AccountingRepository(
    private val local: AccountingLocalDataSource,
    private val api: ApiService? = null,
    private val gson: Gson = Gson()
) {
    private val projectsType = object : TypeToken<List<ProjectDto>>() {}.type

    fun observePendingBills(): Flow<List<PendingBillEntity>> = local.observePendingBills()

    suspend fun loadProjects(): List<ProjectDto> {
        syncPersonalLedger()
        val sources = listOfNotNull(
            local.readLocal("projects"),
            local.read("projects"),
            local.readLocal("pnl_projects")
        )
        return sources.flatMap { raw ->
            runCatching { gson.fromJson<List<ProjectDto>>(raw, projectsType) }.getOrNull().orEmpty()
        }.distinctBy { it.id }
    }

    suspend fun saveLocalProject(project: ProjectDto): List<ProjectDto> {
        val existing = local.readLocal("pnl_projects")?.let { raw ->
            runCatching { gson.fromJson<List<ProjectDto>>(raw, projectsType) }.getOrNull()
        }.orEmpty()
        val updated = existing.filterNot { it.id == project.id } + project
        local.writeLocal("pnl_projects", gson.toJson(updated))
        return updated
    }

    suspend fun addPendingBill(bill: PendingBillEntity) = local.addPendingBill(bill)

    suspend fun deletePendingBill(id: String) = local.deletePendingBill(id)

    suspend fun confirmPendingBill(
        id: String,
        projectLocalId: String,
        projectOfficialId: String?,
        projectCustomName: String,
        gas: Double,
        reward: Double
    ): Boolean {
        val confirmed = local.confirmPendingBill(
            id, projectLocalId, projectOfficialId, projectCustomName, gas, reward
        )
        if (confirmed) syncPersonalLedger()
        return confirmed
    }

    /**
     * Account records are uploaded only while authenticated. Guest records stay in
     * the guest-local cache and never call the private account endpoint.
     */
    suspend fun syncPersonalLedger(): Boolean {
        if (TokenStore.readTokenBlocking().isNullOrBlank()) return false
        val remote = api ?: return false
        val localKey = "personal_ledger"
        val ledgerType = object : TypeToken<List<PersonalLedgerWriteRequest>>() {}.type
        val localEntries = local.readLocal(localKey)?.let { raw ->
            runCatching { gson.fromJson<List<PersonalLedgerWriteRequest>>(raw, ledgerType) }.getOrNull()
        }.orEmpty()
        return runCatching {
            // Pull the account's canonical cloud copy before uploading. Ledger rows are
            // append-only after confirmation; never let an older device cache overwrite
            // a record that has already reached the account cloud from another device.
            val cloudEntries = mutableListOf<PersonalLedgerEntryDto>()
            var before: String? = null
            var pageCount = 0
            while (pageCount < 20) {
                val page = remote.getPersonalLedger(limit = 500, before = before)
                cloudEntries += page
                if (page.size < 500) break
                val nextCursor = page.minOfOrNull { it.occurred_at } ?: break
                if (nextCursor == before) break
                before = nextCursor
                pageCount++
            }

            val cloudRecordIds = cloudEntries.mapTo(mutableSetOf()) { it.client_record_id }
            localEntries.filterNot { it.client_record_id in cloudRecordIds }.forEach { entry ->
                cloudEntries += remote.upsertPersonalLedger(entry.client_record_id, entry)
            }

            val restored = cloudEntries.distinctBy { it.client_record_id }.map { entry ->
                PersonalLedgerWriteRequest(
                    client_record_id = entry.client_record_id,
                    project_id = entry.project_id,
                    project_name = entry.project_name,
                    chain = entry.chain,
                    wallet_address = entry.wallet_address,
                    tx_hash = entry.tx_hash,
                    interaction_method = entry.interaction_method,
                    expected_gas_cost_usd = entry.expected_gas_cost_usd,
                    expected_reward_usd = entry.expected_reward_usd,
                    actual_gas_cost_usd = entry.actual_gas_cost_usd,
                    actual_reward_usd = entry.actual_reward_usd,
                    occurred_at = entry.occurred_at,
                    extra = entry.extra
                )
            }
            // Cloud entries are authoritative; this also recovers rows created on
            // another device and prevents stale local values from winning a merge.
            local.writeLocal(localKey, gson.toJson(restored))
            true
        }.getOrDefault(false)
    }
}
