package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiClient
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

class ProjectDetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    fun load(projectId: String) {
        viewModelScope.launch {
            _uiState.value = ProjectDetailUiState()
            withContext(Dispatchers.IO) {
                CacheStore.read("tasks:$projectId")?.let { json -> runCatching { Gson().fromJson<List<TaskDto>>(json, object : TypeToken<List<TaskDto>>() {}.type) }.getOrNull() }?.let { _uiState.value = _uiState.value.copy(tasks = it, isLoading = false) }
            }
            try {
                val tasks = ApiClient.service.getTasks(projectId).sortedBy { it.step_order }
                _uiState.value = _uiState.value.copy(tasks = tasks, isLoading = false, hasError = false)
                save(projectId, tasks)
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(isLoading = false, hasError = true) }
        }
    }

    fun addTask(projectId: String, name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, hasError = false)
            try {
                val next = (_uiState.value.tasks.maxOfOrNull { it.step_order } ?: 0) + 1
                val task = ApiClient.service.createTask(projectId, TaskCreateRequest(name.trim(), next))
                val tasks = (_uiState.value.tasks + task).sortedBy { it.step_order }
                _uiState.value = _uiState.value.copy(tasks = tasks, isBusy = false)
                save(projectId, tasks)
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(isBusy = false, hasError = true) }
        }
    }

    fun setCompleted(projectId: String, task: TaskDto, completed: Boolean) {
        viewModelScope.launch {
            try {
                val updated = ApiClient.service.updateTask(projectId, task.id, TaskUpdateRequest(completed))
                val tasks = _uiState.value.tasks.map { if (it.id == updated.id) updated else it }
                _uiState.value = _uiState.value.copy(tasks = tasks, hasError = false)
                save(projectId, tasks)
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(hasError = true) }
        }
    }

    fun deleteTask(projectId: String, task: TaskDto) {
        viewModelScope.launch {
            try {
                ApiClient.service.deleteTask(projectId, task.id)
                val tasks = _uiState.value.tasks.filterNot { it.id == task.id }
                _uiState.value = _uiState.value.copy(tasks = tasks, hasError = false)
                save(projectId, tasks)
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(hasError = true) }
        }
    }

    fun checkWallet(address: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, hasError = false)
            try {
                val result = ApiClient.service.checkWallet(address.trim())
                _uiState.value = _uiState.value.copy(walletResult = result, isBusy = false)
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(isBusy = false, hasError = true) }
        }
    }

    private suspend fun save(projectId: String, tasks: List<TaskDto>) {
        withContext(Dispatchers.IO) { CacheStore.write("tasks:$projectId", Gson().toJson(tasks)) }
    }
}
