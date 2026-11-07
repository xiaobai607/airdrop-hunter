package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiService
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.TaskCreateRequest
import com.airdrophunter.app.data.TaskDto
import com.airdrophunter.app.data.TaskUpdateRequest
import com.airdrophunter.app.data.WalletCheckResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProjectDetailUiState(val tasks: List<TaskDto> = emptyList(), val walletResult: WalletCheckResponse? = null, val isLoading: Boolean = true, val isBusy: Boolean = false, val hasError: Boolean = false)

class ProjectDetailViewModel(private val apiService: ApiService) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    fun load(projectId: String, isGuest: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = ProjectDetailUiState()
            withContext(Dispatchers.IO) {
                val key = if (isGuest) "local:tasks:$projectId" else "tasks:$projectId"
                val cached = if (isGuest) CacheStore.readLocal("tasks:$projectId") else CacheStore.read("tasks:$projectId")
                cached?.let { json -> runCatching { Gson().fromJson<List<TaskDto>>(json, object : TypeToken<List<TaskDto>>() {}.type) }.getOrNull() }?.let { _uiState.value = _uiState.value.copy(tasks = it, isLoading = false) }
            }
            if (isGuest) {
                _uiState.value = _uiState.value.copy(isLoading = false, hasError = false)
                return@launch
            }
            try {
                val tasks = apiService.getTasks(projectId).sortedBy { it.step_order }
                _uiState.value = _uiState.value.copy(tasks = tasks, isLoading = false, hasError = false)
                save(projectId, tasks)
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(isLoading = false, hasError = true) }
        }
    }

    fun addTask(projectId: String, name: String, isGuest: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, hasError = false)
            try {
                val next = (_uiState.value.tasks.maxOfOrNull { it.step_order } ?: 0) + 1
                val task = if (isGuest) TaskDto(java.util.UUID.randomUUID().toString(), projectId, name.trim(), false, next)
                    else apiService.createTask(projectId, TaskCreateRequest(name.trim(), next))
                val tasks = (_uiState.value.tasks + task).sortedBy { it.step_order }
                _uiState.value = _uiState.value.copy(tasks = tasks, isBusy = false)
                if (isGuest) saveLocal(projectId, tasks) else save(projectId, tasks)
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(isBusy = false, hasError = true) }
        }
    }

    fun setCompleted(projectId: String, task: TaskDto, completed: Boolean, isGuest: Boolean = false) {
        viewModelScope.launch {
            try {
                val updated = if (isGuest) task.copy(is_completed = completed)
                    else apiService.updateTask(projectId, task.id, TaskUpdateRequest(completed))
                val tasks = _uiState.value.tasks.map { if (it.id == updated.id) updated else it }
                _uiState.value = _uiState.value.copy(tasks = tasks, hasError = false)
                if (isGuest) saveLocal(projectId, tasks) else save(projectId, tasks)
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(hasError = true) }
        }
    }

    fun deleteTask(projectId: String, task: TaskDto, isGuest: Boolean = false) {
        viewModelScope.launch {
            try {
                if (!isGuest) apiService.deleteTask(projectId, task.id)
                val tasks = _uiState.value.tasks.filterNot { it.id == task.id }
                _uiState.value = _uiState.value.copy(tasks = tasks, hasError = false)
                if (isGuest) saveLocal(projectId, tasks) else save(projectId, tasks)
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(hasError = true) }
        }
    }

    fun checkWallet(address: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, hasError = false)
            try {
                val result = apiService.checkWallet(address.trim())
                _uiState.value = _uiState.value.copy(walletResult = result, isBusy = false)
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(isBusy = false, hasError = true) }
        }
    }

    private suspend fun save(projectId: String, tasks: List<TaskDto>) {
        withContext(Dispatchers.IO) { CacheStore.write("tasks:$projectId", Gson().toJson(tasks)) }
    }

    private suspend fun saveLocal(projectId: String, tasks: List<TaskDto>) {
        withContext(Dispatchers.IO) { CacheStore.writeLocal("tasks:$projectId", Gson().toJson(tasks)) }
    }
}

class ProjectDetailViewModelFactory(
    private val apiService: com.airdrophunter.app.data.ApiService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProjectDetailViewModel::class.java)) {
            return ProjectDetailViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
