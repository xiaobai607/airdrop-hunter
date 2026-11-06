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
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    projects = projects,
                    error = null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "无法连接服务器，请检查网络"
                )
            }
        }
    }

    fun createProject(name: String, description: String, status: String) {
        mutateProjects("项目已添加") {
            ApiClient.service.createProject(ProjectWriteRequest(name, description, status))
        }
    }

    fun updateProject(project: ProjectDto, name: String, description: String, status: String) {
        mutateProjects("项目已更新") {
            ApiClient.service.updateProject(
                project.id,
                ProjectWriteRequest(name, description, status)
            )
        }
    }

    fun deleteProject(project: ProjectDto) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            try {
                ApiClient.service.deleteProject(project.id)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    projects = _uiState.value.projects.filterNot { it.id == project.id },
                    message = "项目已删除"
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = error.message ?: "删除失败"
                )
            }
        }
    }

    private fun mutateProjects(successMessage: String, request: suspend () -> ProjectDto) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            try {
                request()
                val projects = ApiClient.service.getProjects()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    projects = projects,
                    error = null,
                    message = successMessage
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = error.message ?: "保存失败"
                )
            }
        }
    }
}
