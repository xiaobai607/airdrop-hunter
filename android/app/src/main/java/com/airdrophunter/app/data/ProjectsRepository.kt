package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

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

    private suspend fun mergePnlProjects(base: List<ProjectDto>): List<ProjectDto> {
        val extras = decodeList<ProjectDto>(local.read("pnl_projects", guest = true), projectsType)
        return (base + extras).distinctBy { it.id }
    }

    suspend fun readCachedProjects(guest: Boolean): List<ProjectDto> {
        val cached = decodeList<ProjectDto>(local.read("projects", guest), projectsType)
        return mergePnlProjects(cached)
    }

    suspend fun refreshProjects(): List<ProjectDto> {
        val fetched = remote.getProjects()
        local.write("projects", gson.toJson(fetched), guest = false)
        return mergePnlProjects(fetched)
    }

    suspend fun createProject(request: ProjectWriteRequest, guest: Boolean): List<ProjectDto> {
        if (guest) {
            val current = decodeList<ProjectDto>(local.read("projects", true), projectsType)
            val created = ProjectDto(
                id = UUID.randomUUID().toString(),
                name = request.name,
                description = request.description,
                status = request.status,
                official_url = request.official_url,
                claim_url = request.claim_url
            )
            val updated = current + created
            local.write("projects", gson.toJson(updated), guest = true)
            return mergePnlProjects(updated)
        }
        remote.createProject(request)
        return refreshProjects()
    }

    suspend fun updateProject(
        project: ProjectDto,
        request: ProjectWriteRequest,
        guest: Boolean
    ): List<ProjectDto> {
        if (guest) {
            val current = decodeList<ProjectDto>(local.read("projects", true), projectsType)
            val updatedProject = project.copy(
                name = request.name,
                description = request.description,
                status = request.status,
                official_url = request.official_url,
                claim_url = request.claim_url
            )
            val updated = current.map { if (it.id == project.id) updatedProject else it }
            local.write("projects", gson.toJson(updated), guest = true)
            return mergePnlProjects(updated)
        }
        remote.updateProject(project.id, request)
        return refreshProjects()
    }

    suspend fun deleteProject(project: ProjectDto, guest: Boolean): List<ProjectDto> {
        if (!guest) remote.deleteProject(project.id)
        val current = decodeList<ProjectDto>(local.read("projects", guest), projectsType)
        val updated = current.filterNot { it.id == project.id }
        local.write("projects", gson.toJson(updated), guest)
        return mergePnlProjects(updated)
    }

    suspend fun readCachedTasks(projectId: String, guest: Boolean): List<TaskDto> =
        decodeList(local.read("tasks:$projectId", guest), tasksType).sortedBy { it.step_order }

    suspend fun refreshTasks(projectId: String): List<TaskDto> {
        val tasks = remote.getTasks(projectId).sortedBy { it.step_order }
        local.write("tasks:$projectId", gson.toJson(tasks), guest = false)
        return tasks
    }

    suspend fun addTask(projectId: String, name: String, guest: Boolean, current: List<TaskDto>): List<TaskDto> {
        val nextOrder = (current.maxOfOrNull { it.step_order } ?: 0) + 1
        val task = if (guest) TaskDto(UUID.randomUUID().toString(), projectId, name.trim(), false, nextOrder)
        else remote.createTask(projectId, TaskCreateRequest(name.trim(), nextOrder))
        return (current + task).sortedBy { it.step_order }.also { saveTasks(projectId, it, guest) }
    }

    suspend fun setTaskCompleted(
        projectId: String,
        task: TaskDto,
        completed: Boolean,
        guest: Boolean,
        current: List<TaskDto>
    ): List<TaskDto> {
        val updated = if (guest) task.copy(is_completed = completed)
        else remote.updateTask(projectId, task.id, TaskUpdateRequest(completed))
        return current.map { if (it.id == updated.id) updated else it }
            .also { saveTasks(projectId, it, guest) }
    }

    suspend fun deleteTask(
        projectId: String,
        task: TaskDto,
        guest: Boolean,
        current: List<TaskDto>
    ): List<TaskDto> {
        if (!guest) remote.deleteTask(projectId, task.id)
        return current.filterNot { it.id == task.id }.also { saveTasks(projectId, it, guest) }
    }

    suspend fun checkWallet(address: String): WalletCheckResponse = remote.checkWallet(address.trim())

    private suspend fun saveTasks(projectId: String, tasks: List<TaskDto>, guest: Boolean) {
        local.write("tasks:$projectId", gson.toJson(tasks), guest)
    }
}
