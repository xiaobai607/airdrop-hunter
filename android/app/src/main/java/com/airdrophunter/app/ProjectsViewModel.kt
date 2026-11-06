package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.ProjectWriteRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val projects: List<ProjectDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

class ProjectsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectsUiState(isLoading = true))
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)
            try {
                val projects = ApiClient.service.getProjects()
                _uiState.value = _uiState.value.copy(isLoading = false, projects = projects, error = null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Network error")
            }
        }
    }

    fun createProject(request: ProjectWriteRequest) {
        mutateProjects("Project added") { ApiClient.service.createProject(request) }
    }

    fun updateProject(project: ProjectDto, request: ProjectWriteRequest) {
        // The editor submits all fields so editing a title cannot erase existing URLs or financial values.
        mutateProjects("Project updated") { ApiClient.service.updateProject(project.id, request) }
    }

    fun deleteProject(project: ProjectDto) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            try {
                ApiClient.service.deleteProject(project.id)
                _uiState.value = _uiState.value.copy(isSaving = false, projects = _uiState.value.projects.filterNot { it.id == project.id }, message = "Project deleted")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = error.message ?: "Delete failed")
            }
        }
    }

    private fun mutateProjects(successMessage: String, request: suspend () -> ProjectDto) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            try {
                request()
                val projects = ApiClient.service.getProjects()
                _uiState.value = _uiState.value.copy(isSaving = false, projects = projects, error = null, message = successMessage)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = error.message ?: "Save failed")
            }
        }
    }
}
