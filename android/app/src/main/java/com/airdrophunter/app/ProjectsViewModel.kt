package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.ProjectWriteRequest
import com.airdrophunter.app.data.ProjectsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val projects: List<ProjectDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

class ProjectsViewModel(private val repository: ProjectsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    fun loadProjects(isGuest: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val cached = repository.readCachedProjects(isGuest)
                if (cached.isNotEmpty() || isGuest) {
                    _uiState.value = _uiState.value.copy(isLoading = false, projects = cached)
                }
                if (isGuest) return@launch
                val refreshed = repository.refreshProjects()
                _uiState.value = _uiState.value.copy(isLoading = false, projects = refreshed, error = null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Network error"
                )
            }
        }
    }

    fun createProject(request: ProjectWriteRequest, isGuest: Boolean = false) {
        mutateProjects(isGuest, if (isGuest) "已保存在本地" else "Project added") {
            repository.createProject(request, isGuest)
        }
    }

    fun updateProject(project: ProjectDto, request: ProjectWriteRequest, isGuest: Boolean = false) {
        mutateProjects(isGuest, if (isGuest) "已保存在本地" else "Project updated") {
            repository.updateProject(project, request, isGuest)
        }
    }

    fun deleteProject(project: ProjectDto, isGuest: Boolean = false) {
        mutateProjects(isGuest, if (isGuest) "已从本地删除" else "Project deleted") {
            repository.deleteProject(project, isGuest)
        }
    }

    private fun mutateProjects(
        guest: Boolean,
        successMessage: String,
        operation: suspend () -> List<ProjectDto>
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            try {
                val projects = operation()
                _uiState.value = _uiState.value.copy(
                    isSaving = false, projects = projects, error = null, message = successMessage
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false, error = error.message ?: "Save failed"
                )
            }
        }
    }
}

class ProjectsViewModelFactory(
    private val repository: ProjectsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProjectsViewModel::class.java)) {
            return ProjectsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
