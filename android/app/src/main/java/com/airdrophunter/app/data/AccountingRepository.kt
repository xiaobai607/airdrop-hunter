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
    suspend fun hasRecordedTransaction(network: String, walletAddress: String, txHash: String): Boolean
    fun schedulePrivateLedgerSync() {}

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
    override suspend fun hasRecordedTransaction(network: String, walletAddress: String, txHash: String): Boolean =
        CacheStore.hasRecordedTransaction(network, walletAddress, txHash)
    override fun schedulePrivateLedgerSync() = CacheStore.schedulePrivateLedgerSync()
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
        syncAndScheduleIfNeeded()
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

    suspend fun hasRecordedTransaction(network: String, walletAddress: String, txHash: String): Boolean =
        local.hasRecordedTransaction(network, walletAddress, txHash)

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
        if (confirmed) syncAndScheduleIfNeeded()
        return confirmed
    }

    private suspend fun syncAndScheduleIfNeeded() {
        if (TokenStore.readTokenBlocking().isNullOrBlank()) return
        if (!syncPersonalLedger()) local.schedulePrivateLedgerSync()
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
            var offset = 0
            var pageCount = 0
            var reachedEnd = false
            // Read one final page at the 10,000-row boundary so an exact-cap ledger
            // is accepted only after the API confirms there are no more rows.
            while (pageCount <= 20) {
                val page = remote.getPersonalLedger(limit = 500, offset = offset)
                cloudEntries += page
                if (page.size < 500) {
                    reachedEnd = true
                    break
                }
                offset += page.size
                pageCount++
            }
            // Never replace a device cache with a silently truncated cloud copy.
            // A larger ledger can be handled by increasing the explicit safety cap.
            check(reachedEnd) { "个人账本超过当前单次恢复上限，已保留本地数据" }

            val cloudRecordIds = cloudEntries.mapTo(mutableSetOf()) { it.client_record_id }
            // Simulated toolbar bills are retained on this device for testing, but
            // must never be uploaded to the account's real personal ledger.
            val demoLocalEntries = localEntries.filter(::isDemoPersonalLedgerEntry)
                .filterNot { it.client_record_id in cloudRecordIds }
            val syncableLocalEntries = localEntries.filterNot(::isDemoPersonalLedgerEntry)
            syncableLocalEntries.filterNot { it.client_record_id in cloudRecordIds }.forEach { entry ->
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
            local.writeLocal(
                localKey,
                gson.toJson(restored + demoLocalEntries)
            )
            true
        }.getOrDefault(false)
    }
}

internal fun isDemoPersonalLedgerEntry(entry: PersonalLedgerWriteRequest): Boolean =
    entry.extra["is_demo"] == "true" ||
        isDemoAccountingTransaction(entry.tx_hash, entry.wallet_address, entry.interaction_method)
