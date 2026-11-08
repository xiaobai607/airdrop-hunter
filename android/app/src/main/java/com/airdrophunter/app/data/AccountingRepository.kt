package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

interface AccountingLocalDataSource {
    fun observePendingBills(): Flow<List<PendingBillEntity>>
    suspend fun readLocal(key: String): String?
    suspend fun read(key: String): String?
    suspend fun readUserProjects(): List<UserProjectEntity>
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
    override suspend fun readUserProjects(): List<UserProjectEntity> = CacheStore.readUserProjects()
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
    private val gson: Gson = Gson()
) {
    private val projectsType = object : TypeToken<List<ProjectDto>>() {}.type

    fun observePendingBills(): Flow<List<PendingBillEntity>> = local.observePendingBills()

    suspend fun loadProjects(): List<ProjectDto> {
        val sources = listOfNotNull(
            local.readLocal("projects"),
            local.read("projects"),
            local.readLocal("pnl_projects")
        )
        val cachedProjects = sources.flatMap { raw ->
            runCatching { gson.fromJson<List<ProjectDto>>(raw, projectsType) }.getOrNull().orEmpty()
        }
        val joinedProjects = local.readUserProjects().map { saved ->
            ProjectDto(
                id = saved.projectLocalId,
                name = saved.projectName,
                description = "",
                status = saved.status,
                communityOfficialId = saved.projectOfficialId
            )
        }
        // Prefer the Room library entry for a linked community project so
        // accounting always writes to the same private project ID as its ledger.
        return (joinedProjects + cachedProjects).distinctBy {
            it.communityOfficialId?.let { officialId -> "official:$officialId" } ?: "local:${it.id}"
        }
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
    ): Boolean = local.confirmPendingBill(
        id, projectLocalId, projectOfficialId, projectCustomName, gas, reward
    )
}
