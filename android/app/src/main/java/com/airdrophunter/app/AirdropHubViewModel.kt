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
    private var backgroundRefreshJob: Job? = null

    init { load() }

    fun selectChain(chain: String) {
        if (_uiState.value.selectedChain == chain) return
        _uiState.value = _uiState.value.copy(selectedChain = chain)
        load()
    }

    fun load() {
        loadJob?.cancel()
        backgroundRefreshJob?.cancel()
        val chain = _uiState.value.selectedChain
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isRefreshing = false, error = null)
            try {
                val snapshot = repository.load(chain.takeUnless { it == "全部" })
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    projects = snapshot.projects,
                    error = if (snapshot.projects.isEmpty()) "数据库中暂无项目。可点击刷新同步公开数据源。" else null,
                    sourceError = snapshot.sourceError
                )
                if (snapshot.refreshDue) refreshInBackground(chain.takeUnless { it == "全部" })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "服务器繁忙，请稍后重试。")
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        backgroundRefreshJob?.cancel()
        refreshInBackground(_uiState.value.selectedChain.takeUnless { it == "全部" })
    }

    private fun refreshInBackground(chain: String?) {
        if (backgroundRefreshJob?.isActive == true) return
        backgroundRefreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, isLoading = false, error = null)
            try {
                val projects = repository.refresh(chain)
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false, isLoading = false, projects = projects,
                    error = if (projects.isEmpty()) "同步已执行，但数据库中暂时没有可展示的项目。" else null,
                    sourceError = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = if (_uiState.value.projects.isEmpty()) "服务器繁忙，请稍后重试。" else null,
                    sourceError = "数据源暂时不可用，已保留上次数据。"
                )
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
