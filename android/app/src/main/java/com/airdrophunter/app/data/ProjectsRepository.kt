package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import java.net.URI

/**
 * Narrow persistence boundary for the project feature. Guest data and authenticated
 * account data remain in their existing CacheStore scopes.
 */
interface ProjectsLocalDataSource {
    suspend fun read(key: String, guest: Boolean): String?
    suspend fun write(key: String, value: String, guest: Boolean)
}

class CacheStoreProjectsLocalDataSource : ProjectsLocalDataSource {
    override suspend fun read(key: String, guest: Boolean): String? =
        if (guest) CacheStore.readLocal(key) else CacheStore.read(key)

    override suspend fun write(key: String, value: String, guest: Boolean) {
        if (guest) CacheStore.writeLocal(key, value) else CacheStore.write(key, value)
    }
}

/** API boundary limited to project, task and wallet-check operations. */
interface ProjectsRemoteDataSource {
    suspend fun getProjects(): List<ProjectDto>
    suspend fun createProject(request: ProjectWriteRequest): ProjectDto
    suspend fun updateProject(projectId: String, request: ProjectWriteRequest): ProjectDto
    suspend fun deleteProject(projectId: String)
    suspend fun getTasks(projectId: String): List<TaskDto>
    suspend fun createTask(projectId: String, request: TaskCreateRequest): TaskDto
    suspend fun updateTask(projectId: String, taskId: String, request: TaskUpdateRequest): TaskDto
    suspend fun deleteTask(projectId: String, taskId: String)
    suspend fun checkWallet(address: String): WalletCheckResponse
    suspend fun getClaimUrl(projectId: String, walletId: String): ClaimUrlResponse
}

class RetrofitProjectsRemoteDataSource(private val api: ApiService) : ProjectsRemoteDataSource {
    override suspend fun getProjects() = api.getProjects()
    override suspend fun createProject(request: ProjectWriteRequest) = api.createProject(request)
    override suspend fun updateProject(projectId: String, request: ProjectWriteRequest) = api.updateProject(projectId, request)
    override suspend fun deleteProject(projectId: String) = api.deleteProject(projectId)
    override suspend fun getTasks(projectId: String) = api.getTasks(projectId)
    override suspend fun createTask(projectId: String, request: TaskCreateRequest) = api.createTask(projectId, request)
    override suspend fun updateTask(projectId: String, taskId: String, request: TaskUpdateRequest) =
        api.updateTask(projectId, taskId, request)
    override suspend fun deleteTask(projectId: String, taskId: String) = api.deleteTask(projectId, taskId)
    override suspend fun checkWallet(address: String) = api.checkWallet(address)
    override suspend fun getClaimUrl(projectId: String, walletId: String) = api.getClaimUrl(projectId, walletId)
}

/**
 * Owns project/task persistence rules. ViewModels only coordinate UI state.
 * The cache-first authenticated loading strategy and guest-only local writes
 * intentionally match the existing application behavior.
 */
class ProjectsRepository(
    private val remote: ProjectsRemoteDataSource,
    private val local: ProjectsLocalDataSource,
    private val gson: Gson = Gson()
) {
    private val projectsType = object : TypeToken<List<ProjectDto>>() {}.type
    private val tasksType = object : TypeToken<List<TaskDto>>() {}.type

    private fun <T> decodeList(json: String?, type: java.lang.reflect.Type): List<T> =
        json?.let { runCatching { gson.fromJson<List<T>>(it, type) }.getOrNull() }.orEmpty()

    private suspend fun mergePnlProjects(base: List<ProjectDto>, guest: Boolean): List<ProjectDto> {
        // Migrate legacy browser-accounting projects inside their original scope.
        // Guest projects must never leak into an authenticated account cache.
        val extras = decodeList<ProjectDto>(local.read("pnl_projects", guest), projectsType)
        val merged = (base + extras).distinctBy { it.id }
        if (extras.isNotEmpty()) local.write("projects", gson.toJson(merged), guest)
        return merged
    }

    suspend fun readCachedProjects(guest: Boolean): List<ProjectDto> {
        val cached = decodeList<ProjectDto>(local.read("projects", guest), projectsType)
        return mergePnlProjects(cached, guest)
    }

    suspend fun refreshProjects(): List<ProjectDto> {
        val fetched = remote.getProjects()
        val cached = decodeList<ProjectDto>(local.read("projects", guest = false), projectsType)
        // Keep stable local IDs used by historical bills. Server IDs are transport keys.
        val merged = fetched.map { server ->
            cached.firstOrNull { it.remoteId == server.id || (it.remoteId == null && it.id == server.id) }
                ?.let { server.copy(id = it.id, remoteId = server.id, syncPending = false) }
                ?: server.copy(remoteId = server.id, syncPending = false)
        } + cached.filter { it.syncPending }
        val unique = merged.distinctBy { it.id }
        local.write("projects", gson.toJson(unique), guest = false)
        return mergePnlProjects(unique, guest = false)
    }

    suspend fun createProject(request: ProjectWriteRequest, guest: Boolean, localId: String? = null): List<ProjectDto> {
        val normalized = normalizeProjectRequest(request)
        val current = readCachedProjects(guest)
        val created = ProjectDto(
            id = localId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = normalized.name,
            description = normalized.description,
            status = normalized.status,
            official_url = normalized.official_url,
            claim_url = normalized.claim_url,
            twitter_url = normalized.twitter_url,
            deadline = normalized.deadline,
            expected_income = normalized.expected_income,
            gas_cost = normalized.gas_cost,
            actual_income = normalized.actual_income,
            syncPending = !guest
        )
        val updated = current + created
        // Local persistence is the success boundary; the network is secondary.
        local.write("projects", gson.toJson(updated), guest)
        if (guest) return mergePnlProjects(updated, guest = true)

        return try {
            val serverProject = remote.createProject(normalized)
            val synchronized = updated.map {
                if (it.id == created.id) it.copy(remoteId = serverProject.id, syncPending = false) else it
            }
            local.write("projects", gson.toJson(synchronized), guest = false)
            mergePnlProjects(synchronized, guest = false)
        } catch (_: Exception) {
            // Preserve the local project; it can be retried by a later sync pass.
            mergePnlProjects(updated, guest = false)
        }
    }

    suspend fun updateProject(
        project: ProjectDto,
        request: ProjectWriteRequest,
        guest: Boolean
    ): List<ProjectDto> {
        val normalized = normalizeProjectRequest(request)
        if (guest) {
            val current = decodeList<ProjectDto>(local.read("projects", true), projectsType)
            require(current.any { it.id == project.id }) { "项目不存在或不是本地自建项目" }
            val updatedProject = project.copy(
                name = normalized.name,
                description = normalized.description,
                status = normalized.status,
                official_url = normalized.official_url,
                claim_url = normalized.claim_url,
                twitter_url = normalized.twitter_url,
                deadline = normalized.deadline,
                expected_income = normalized.expected_income,
                gas_cost = normalized.gas_cost,
                actual_income = normalized.actual_income
            )
            val updated = current.map { if (it.id == project.id) updatedProject else it }
            local.write("projects", gson.toJson(updated), guest = true)
            return mergePnlProjects(updated, guest)
        }
        remote.updateProject(project.remoteId ?: project.id, normalized)
        return refreshProjects()
    }

    suspend fun deleteProject(project: ProjectDto, guest: Boolean): List<ProjectDto> {
        if (!guest) remote.deleteProject(project.remoteId ?: project.id)
        val current = readCachedProjects(guest)
        val updated = current.filterNot { it.id == project.id }
        local.write("projects", gson.toJson(updated), guest)
        // Project tasks and wallet assignments are project-owned working data. Keep
        // confirmed P&L and interaction history intact when a project is removed.
        local.write("tasks:${project.id}", "[]", guest)
        runCatching { CacheStore.saveProjectWalletAssignments(project.id, emptySet()) }
        return mergePnlProjects(updated, guest)
    }

    suspend fun readCachedTasks(projectId: String, guest: Boolean): List<TaskDto> =
        decodeList<TaskDto>(local.read("tasks:$projectId", guest), tasksType).sortedBy { it.step_order }

    suspend fun refreshTasks(projectId: String): List<TaskDto> {
        val tasks = remote.getTasks(projectId).sortedBy { it.step_order }
        local.write("tasks:$projectId", gson.toJson(tasks), guest = false)
        return tasks
    }

    suspend fun addTask(projectId: String, name: String, guest: Boolean, current: List<TaskDto>): List<TaskDto> {
        require(projectId.isNotBlank()) { "项目 ID 不能为空" }
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "任务名称不能为空" }
        require(normalizedName.length <= 240) { "任务名称不能超过240个字符" }
        require(current.all { it.project_id == projectId }) { "任务列表包含其他项目的任务" }
        val nextOrder = (current.maxOfOrNull { it.step_order } ?: 0) + 1
        val task = if (guest) TaskDto(UUID.randomUUID().toString(), projectId, normalizedName, false, nextOrder)
        else remote.createTask(projectId, TaskCreateRequest(normalizedName, nextOrder))
        require(task.project_id == projectId) { "服务器返回的任务不属于当前项目" }
        return (current + task).sortedBy { it.step_order }.also { saveTasks(projectId, it, guest) }
    }

    private fun requireTaskBelongsToProject(projectId: String, task: TaskDto, current: List<TaskDto>) {
        require(projectId.isNotBlank() && task.project_id == projectId) { "不能操作其他项目的任务" }
        require(current.any { it.id == task.id && it.project_id == projectId }) { "任务已不存在或不属于当前项目" }
        require(current.all { it.project_id == projectId }) { "任务列表包含其他项目的任务" }
    }

    suspend fun setTaskCompleted(
        projectId: String,
        task: TaskDto,
        completed: Boolean,
        guest: Boolean,
        current: List<TaskDto>
    ): List<TaskDto> {
        requireTaskBelongsToProject(projectId, task, current)
        val updated = if (guest) task.copy(is_completed = completed)
        else remote.updateTask(projectId, task.id, TaskUpdateRequest(completed))
        require(updated.project_id == projectId) { "服务器返回的任务不属于当前项目" }
        return current.map { if (it.id == updated.id) updated else it }
            .also { saveTasks(projectId, it, guest) }
    }

    suspend fun renameTask(
        projectId: String,
        task: TaskDto,
        name: String,
        guest: Boolean,
        current: List<TaskDto>
    ): List<TaskDto> {
        requireTaskBelongsToProject(projectId, task, current)
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "任务名称不能为空" }
        require(normalizedName.length <= 240) { "任务名称不能超过240个字符" }
        val updated = if (guest) task.copy(task_name = normalizedName)
        else remote.updateTask(projectId, task.id, TaskUpdateRequest(task_name = normalizedName))
        require(updated.project_id == projectId) { "服务器返回的任务不属于当前项目" }
        return current.map { if (it.id == updated.id) updated else it }
            .also { saveTasks(projectId, it, guest) }
    }

    suspend fun deleteTask(
        projectId: String,
        task: TaskDto,
        guest: Boolean,
        current: List<TaskDto>
    ): List<TaskDto> {
        requireTaskBelongsToProject(projectId, task, current)
        if (!guest) remote.deleteTask(projectId, task.id)
        return current.filterNot { it.id == task.id }.also { saveTasks(projectId, it, guest) }
    }

    private fun normalizeProjectRequest(request: ProjectWriteRequest): ProjectWriteRequest {
        val name = request.name.trim()
        val description = request.description.trim()
        require(name.isNotEmpty() && name.length <= 120) { "项目名称不能为空且不能超过120个字符" }
        require(description.length <= 5000) { "项目说明不能超过5000个字符" }
        require(request.status in setOf("tracking", "upcoming", "completed")) { "项目状态无效" }
        require(listOf(request.expected_income, request.gas_cost, request.actual_income).all {
            it.isFinite() && it >= 0.0 && it <= 999_999_999_999.9999
        }) { "项目金额必须是有效的非负数" }
        fun validateUrl(value: String, label: String): String {
            val normalized = value.trim()
            if (normalized.isEmpty()) return normalized
            val parsed = runCatching { URI(normalized) }.getOrNull()
            require(
                parsed != null && parsed.scheme.equals("https", ignoreCase = true) &&
                    !parsed.host.isNullOrBlank() && parsed.rawUserInfo == null &&
                    (parsed.port == -1 || parsed.port in 1..65535)
            ) { "${label}必须是有效的 HTTPS URL" }
            return normalized
        }
        return request.copy(
            name = name,
            description = description,
            official_url = validateUrl(request.official_url, "项目官网"),
            claim_url = validateUrl(request.claim_url, "资格查询入口"),
            twitter_url = validateUrl(request.twitter_url, "社交链接")
        )
    }

    suspend fun checkWallet(address: String): WalletCheckResponse = remote.checkWallet(address.trim())

    suspend fun getClaimUrl(projectId: String, walletId: String): ClaimUrlResponse =
        remote.getClaimUrl(projectId, walletId)

    private suspend fun saveTasks(projectId: String, tasks: List<TaskDto>, guest: Boolean) {
        local.write("tasks:$projectId", gson.toJson(tasks), guest)
    }
}
