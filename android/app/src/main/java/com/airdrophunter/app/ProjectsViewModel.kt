package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiService
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.ProjectWriteRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ProjectsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val projects: List<ProjectDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

class ProjectsViewModel(private val apiService: ApiService) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()
    private val gson = Gson()
    private val projectListType = object : TypeToken<List<ProjectDto>>() {}.type

    private suspend fun mergePnlProjects(base: List<ProjectDto>): List<ProjectDto> {
        val extras = withContext(Dispatchers.IO) {
            CacheStore.readLocal("pnl_projects")?.let {
                runCatching { gson.fromJson<List<ProjectDto>>(it, projectListType) }.getOrNull()
            }.orEmpty()
        }
        return (base + extras).distinctBy { it.id }
    }

    fun loadProjects(isGuest: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            if (isGuest) {
                val local = withContext(Dispatchers.IO) {
                    CacheStore.readLocal("projects")?.let { runCatching { gson.fromJson<List<ProjectDto>>(it, projectListType) }.getOrNull() }
                }.orEmpty()
                _uiState.value = _uiState.value.copy(isLoading = false, projects = mergePnlProjects(local), error = null)
                return@launch
            }
            withContext(Dispatchers.IO) {
                CacheStore.read("projects")?.let { json ->
                    runCatching { gson.fromJson<List<ProjectDto>>(json, projectListType) }.getOrNull()
                }?.let { cached -> _uiState.value = _uiState.value.copy(isLoading = false, projects = cached) }
            }
            try {
                val projects = apiService.getProjects()
                val merged = mergePnlProjects(projects)
                _uiState.value = _uiState.value.copy(isLoading = false, projects = merged, error = null)
                withContext(Dispatchers.IO) { CacheStore.write("projects", gson.toJson(projects)) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Network error")
            }
        }
    }

    fun createProject(request: ProjectWriteRequest, isGuest: Boolean = false) {
        if (isGuest) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
                val project = ProjectDto(
                    id = UUID.randomUUID().toString(),
                    name = request.name,
                    description = request.description,
                    status = request.status,
                    official_url = request.official_url,
                    claim_url = request.claim_url
                )
                val projects = _uiState.value.projects + project
                withContext(Dispatchers.IO) { CacheStore.writeLocal("projects", gson.toJson(projects)) }
                _uiState.value = _uiState.value.copy(isSaving = false, projects = projects, message = "已保存在本地")
            }
        } else {
            mutateProjects("Project added") { apiService.createProject(request) }
        }
    }

    fun updateProject(project: ProjectDto, request: ProjectWriteRequest, isGuest: Boolean = false) {
        if (isGuest) {
            viewModelScope.launch {
                val updated = project.copy(
                    name = request.name, description = request.description, status = request.status,
                    official_url = request.official_url, claim_url = request.claim_url
                )
                val projects = _uiState.value.projects.map { if (it.id == project.id) updated else it }
                withContext(Dispatchers.IO) { CacheStore.writeLocal("projects", gson.toJson(projects)) }
                _uiState.value = _uiState.value.copy(projects = projects, message = "已保存在本地")
            }
        } else mutateProjects("Project updated") { apiService.updateProject(project.id, request) }
    }

    fun deleteProject(project: ProjectDto, isGuest: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            try {
                val projects = _uiState.value.projects.filterNot { it.id == project.id }
                if (isGuest) {
                    withContext(Dispatchers.IO) { CacheStore.writeLocal("projects", gson.toJson(projects)) }
                } else {
                    apiService.deleteProject(project.id)
                    withContext(Dispatchers.IO) { CacheStore.write("projects", gson.toJson(projects)) }
                }
                _uiState.value = _uiState.value.copy(isSaving = false, projects = projects, message = if (isGuest) "已从本地删除" else "Project deleted")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _uiState.value = _uiState.value.copy(isSaving = false, error = error.message ?: "Delete failed") }
        }
    }

    private fun mutateProjects(successMessage: String, request: suspend () -> ProjectDto) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            try {
                request()
                val projects = apiService.getProjects()
                _uiState.value = _uiState.value.copy(isSaving = false, projects = projects, error = null, message = successMessage)
                withContext(Dispatchers.IO) { CacheStore.write("projects", gson.toJson(projects)) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _uiState.value = _uiState.value.copy(isSaving = false, error = error.message ?: "Save failed") }
        }
    }
}

class ProjectsViewModelFactory(
    private val apiService: com.airdrophunter.app.data.ApiService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProjectsViewModel::class.java)) {
            return ProjectsViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
