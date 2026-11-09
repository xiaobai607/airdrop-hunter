package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

interface AccountingLocalDataSource {
    fun observePendingBills(): Flow<List<PendingBillEntity>>
    suspend fun readLocal(key: String): String?
    suspend fun read(key: String): String?
    suspend fun writeLocal(key: String, payload: String)
    suspend fun readLocalForToken(key: String, accessToken: String): String? = readLocal(key)
    suspend fun mergePersonalLedgerForToken(
        key: String,
        cloudEntries: List<PersonalLedgerWriteRequest>,
        accessToken: String
    ): Boolean
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
        reward: Double,
        assetBreakdownJson: String? = null,
        publicContributionOptIn: Boolean = false
    ): Boolean
}

class CacheStoreAccountingLocalDataSource : AccountingLocalDataSource {
    override fun observePendingBills(): Flow<List<PendingBillEntity>> = CacheStore.observePendingBills()
    override suspend fun readLocal(key: String): String? = CacheStore.readLocal(key)
    override suspend fun read(key: String): String? = CacheStore.read(key)
    override suspend fun writeLocal(key: String, payload: String) = CacheStore.writeLocal(key, payload)
    override suspend fun readLocalForToken(key: String, accessToken: String): String? =
        CacheStore.readLocalForToken(key, accessToken)
    override suspend fun mergePersonalLedgerForToken(
        key: String,
        cloudEntries: List<PersonalLedgerWriteRequest>,
        accessToken: String
    ): Boolean = CacheStore.mergePersonalLedgerForToken(key, cloudEntries, accessToken)
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
        reward: Double,
        assetBreakdownJson: String?,
        publicContributionOptIn: Boolean
    ): Boolean = CacheStore.confirmPendingBill(
        id, projectLocalId, projectOfficialId, projectCustomName, gas, reward, assetBreakdownJson, publicContributionOptIn
    )
}

interface PersonalLedgerRemoteDataSource {
    suspend fun getPersonalLedger(authorizationHeader: String, limit: Int, offset: Int): List<PersonalLedgerEntryDto>
    suspend fun upsertPersonalLedger(
        authorizationHeader: String,
        clientRecordId: String,
        request: PersonalLedgerWriteRequest
    ): PersonalLedgerEntryDto
}

class ApiPersonalLedgerRemoteDataSource(private val api: ApiService) : PersonalLedgerRemoteDataSource {
    override suspend fun getPersonalLedger(
        authorizationHeader: String,
        limit: Int,
        offset: Int
    ): List<PersonalLedgerEntryDto> =
        api.getPersonalLedger(limit = limit, offset = offset, authorizationHeader = authorizationHeader)

    override suspend fun upsertPersonalLedger(
        authorizationHeader: String,
        clientRecordId: String,
        request: PersonalLedgerWriteRequest
    ): PersonalLedgerEntryDto =
        api.upsertPersonalLedger(clientRecordId, request, authorizationHeader)
}

class AccountingRepository(
    private val local: AccountingLocalDataSource,
    private val api: ApiService? = null,
    private val gson: Gson = Gson(),
    private val accessTokenProvider: () -> String? = { TokenStore.readTokenBlocking() },
    personalLedgerRemote: PersonalLedgerRemoteDataSource? = null
) {
    private val ledgerRemote = personalLedgerRemote ?: api?.let(::ApiPersonalLedgerRemoteDataSource)
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
        reward: Double,
        assetBreakdownJson: String? = null,
        publicContributionOptIn: Boolean = false
    ): Boolean {
        val confirmed = local.confirmPendingBill(
            id, projectLocalId, projectOfficialId, projectCustomName, gas, reward, assetBreakdownJson, publicContributionOptIn
        )
        if (confirmed) syncAndScheduleIfNeeded()
        return confirmed
    }

    private suspend fun syncAndScheduleIfNeeded() {
        if (accessTokenProvider().isNullOrBlank()) return
        if (!syncPersonalLedger()) local.schedulePrivateLedgerSync()
    }

    /**
     * Account records are uploaded only while authenticated. Guest records stay in
     * the guest-local cache and never call the private account endpoint.
     */
    suspend fun syncPersonalLedger(): Boolean {
        // Bind every request and local-cache operation to the session that started
        // this sync. An account switch during network work cannot redirect records.
        val accessToken = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: return false
        val authorizationHeader = "Bearer $accessToken"
        val remote = ledgerRemote ?: return false
        val localKey = "personal_ledger"
        val ledgerType = object : TypeToken<List<PersonalLedgerWriteRequest>>() {}.type
        val localEntries = local.readLocalForToken(localKey, accessToken)?.let { raw ->
            runCatching { gson.fromJson<List<PersonalLedgerWriteRequest>>(raw, ledgerType) }.getOrNull()
        }.orEmpty()

        return try {
            val cloudEntries = mutableListOf<PersonalLedgerEntryDto>()
            var offset = 0
            var pageCount = 0
            var reachedEnd = false
            // Fetch one final page at offset 10,000 to distinguish an exact-cap
            // ledger from an oversized ledger before changing local data.
            while (pageCount <= 20) {
                val page = remote.getPersonalLedger(authorizationHeader, limit = 500, offset = offset)
                cloudEntries += page
                check(cloudEntries.size <= MAX_PERSONAL_LEDGER_SYNC_ROWS) {
                    "个人账本超过当前单次恢复上限，已保留本地数据"
                }
                if (page.size < 500) {
                    reachedEnd = true
                    break
                }
                offset += page.size
                pageCount++
            }
            check(reachedEnd) { "个人账本超过当前单次恢复上限，已保留本地数据" }

            val cloudRecordIds = cloudEntries.mapTo(mutableSetOf()) { it.client_record_id }
            for (entry in localEntries.filterNot(::isDemoPersonalLedgerEntry).distinctBy { it.client_record_id }) {
                if (entry.client_record_id in cloudRecordIds) continue
                val saved = remote.upsertPersonalLedger(authorizationHeader, entry.client_record_id, entry)
                cloudEntries += saved
                cloudRecordIds += saved.client_record_id
            }

            val canonicalCloudEntries = cloudEntries.distinctBy { it.client_record_id }.map { entry ->
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

            // The local adapter merges inside the same Room transaction used by
            // bill confirmation. Rows added during network work cannot be lost.
            local.mergePersonalLedgerForToken(localKey, canonicalCloudEntries, accessToken)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

}

private const val MAX_PERSONAL_LEDGER_SYNC_ROWS = 10_000

internal fun isDemoPersonalLedgerEntry(entry: PersonalLedgerWriteRequest): Boolean =
    entry.extra["is_demo"] == "true" ||
        isDemoAccountingTransaction(entry.tx_hash, entry.wallet_address, entry.interaction_method)
