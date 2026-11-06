package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiClient
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

data class ProjectsUiState(val isLoading: Boolean = true, val isSaving: Boolean = false, val projects: List<ProjectDto> = emptyList(), val error: String? = null, val message: String? = null)

class ProjectsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    fun loadProjects() {
        viewModelScope.launch {
            // Show cached content before making any network request.
            withContext(Dispatchers.IO) {
                CacheStore.read("projects")?.let { json ->
                    runCatching { Gson().fromJson<List<ProjectDto>>(json, object : TypeToken<List<ProjectDto>>() {}.type) }.getOrNull()
                }?.let { cached -> _uiState.value = _uiState.value.copy(isLoading = false, projects = cached) }
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val projects = ApiClient.service.getProjects()
                _uiState.value = _uiState.value.copy(isLoading = false, projects = projects, error = null)
                withContext(Dispatchers.IO) { CacheStore.write("projects", Gson().toJson(projects)) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Network error") }
        }
    }

    fun createProject(request: ProjectWriteRequest) = mutateProjects("Project added") { ApiClient.service.createProject(request) }
    fun updateProject(project: ProjectDto, request: ProjectWriteRequest) = mutateProjects("Project updated") { ApiClient.service.updateProject(project.id, request) }

    fun deleteProject(project: ProjectDto) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            try {
                ApiClient.service.deleteProject(project.id)
                val projects = _uiState.value.projects.filterNot { it.id == project.id }
                _uiState.value = _uiState.value.copy(isSaving = false, projects = projects, message = "Project deleted")
                withContext(Dispatchers.IO) { CacheStore.write("projects", Gson().toJson(projects)) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _uiState.value = _uiState.value.copy(isSaving = false, error = error.message ?: "Delete failed") }
        }
    }

    private fun mutateProjects(successMessage: String, request: suspend () -> ProjectDto) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            try {
                request()
                val projects = ApiClient.service.getProjects()
                _uiState.value = _uiState.value.copy(isSaving = false, projects = projects, error = null, message = successMessage)
                withContext(Dispatchers.IO) { CacheStore.write("projects", Gson().toJson(projects)) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _uiState.value = _uiState.value.copy(isSaving = false, error = error.message ?: "Save failed") }
        }
    }
}
