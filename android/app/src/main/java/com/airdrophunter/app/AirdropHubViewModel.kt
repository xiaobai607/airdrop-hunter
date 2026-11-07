package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.AirdropHubRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AirdropHubUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedChain: String = "全部",
    val projects: List<com.airdrophunter.app.data.AirdropHubDto> = emptyList(),
    val error: String? = null,
    val sourceError: String? = null
)

class AirdropHubViewModel(
    private val repository: AirdropHubRepository
) : ViewModel() {
    val chains = listOf("全部", "Ethereum", "Solana", "BSC", "Arbitrum", "Base", "Linea", "Polygon", "Optimism", "Sui")
    private val _uiState = MutableStateFlow(AirdropHubUiState(isLoading = true))
    val uiState: StateFlow<AirdropHubUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun selectChain(chain: String) {
        if (_uiState.value.selectedChain == chain) return
        _uiState.value = _uiState.value.copy(selectedChain = chain)
        load()
    }

    fun load() {
        loadJob?.cancel()
        val chain = _uiState.value.selectedChain
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val snapshot = repository.load(chain.takeUnless { it == "全部" })
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    projects = snapshot.projects,
                    error = if (snapshot.projects.isEmpty()) "数据库中暂无项目。可点击刷新同步公开数据源。" else null,
                    sourceError = snapshot.sourceError
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "服务器繁忙，请稍后重试。")
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                val chain = _uiState.value.selectedChain
                val projects = repository.refresh(chain.takeUnless { it == "全部" })
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false, isLoading = false, projects = projects,
                    error = if (projects.isEmpty()) "同步已执行，但数据库中暂时没有可展示的项目。" else null,
                    sourceError = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = "服务器繁忙，请稍后重试。")
            }
        }
    }
}

class AirdropHubViewModelFactory(
    private val repository: AirdropHubRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AirdropHubViewModel::class.java)) {
            return AirdropHubViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
