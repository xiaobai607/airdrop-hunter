package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.ProjectDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val isLoading: Boolean = false,
    val projects: List<ProjectDto> = emptyList(),
    val error: String? = null
)

class ProjectsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectsUiState(isLoading = true))
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val projects = ApiClient.service.getProjects()
                _uiState.value = ProjectsUiState(projects = projects)
            } catch (error: Exception) {
                _uiState.value = ProjectsUiState(
                    error = error.message ?: "无法连接服务器，请检查后端地址和网络"
                )
            }
        }
    }
}
