package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ProjectsRepository
import com.airdrophunter.app.data.TaskDto
import com.airdrophunter.app.data.WalletCheckResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectDetailUiState(
    val tasks: List<TaskDto> = emptyList(),
    val walletResult: WalletCheckResponse? = null,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val hasError: Boolean = false
)

class ProjectDetailViewModel(private val repository: ProjectsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    fun load(projectId: String, isGuest: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = ProjectDetailUiState()
            try {
                val cached = repository.readCachedTasks(projectId, isGuest)
                _uiState.value = _uiState.value.copy(tasks = cached, isLoading = false)
                if (isGuest) return@launch
                val refreshed = repository.refreshTasks(projectId)
                _uiState.value = _uiState.value.copy(tasks = refreshed, isLoading = false, hasError = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
            }
        }
    }

    fun addTask(projectId: String, name: String, isGuest: Boolean = false) {
        mutateTasks {
            repository.addTask(projectId, name, isGuest, _uiState.value.tasks)
        }
    }

    fun setCompleted(projectId: String, task: TaskDto, completed: Boolean, isGuest: Boolean = false) {
        mutateTasks {
            repository.setTaskCompleted(projectId, task, completed, isGuest, _uiState.value.tasks)
        }
    }

    fun deleteTask(projectId: String, task: TaskDto, isGuest: Boolean = false) {
        mutateTasks {
            repository.deleteTask(projectId, task, isGuest, _uiState.value.tasks)
        }
    }

    fun checkWallet(address: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, hasError = false)
            try {
                val result = repository.checkWallet(address)
                _uiState.value = _uiState.value.copy(walletResult = result, isBusy = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, hasError = true)
            }
        }
    }

    private fun mutateTasks(operation: suspend () -> List<TaskDto>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, hasError = false)
            try {
                val tasks = operation()
                _uiState.value = _uiState.value.copy(tasks = tasks, isBusy = false, hasError = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, hasError = true)
            }
        }
    }
}

class ProjectDetailViewModelFactory(
    private val repository: ProjectsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProjectDetailViewModel::class.java)) {
            return ProjectDetailViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
