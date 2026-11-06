package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.AirdropHubDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AirdropHubUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedChain: String = "全部",
    val projects: List<AirdropHubDto> = emptyList(),
    val error: String? = null,
    val sourceError: String? = null
)

class AirdropHubViewModel : ViewModel() {
    val chains = listOf("全部", "Ethereum", "Solana", "BSC", "Arbitrum", "Base", "Linea", "Polygon", "Optimism", "Sui")
    private val _uiState = MutableStateFlow(AirdropHubUiState(isLoading = true))
    val uiState: StateFlow<AirdropHubUiState> = _uiState.asStateFlow()

    init { load() }

    fun selectChain(chain: String) {
        if (_uiState.value.selectedChain == chain) return
        _uiState.value = _uiState.value.copy(selectedChain = chain)
        load()
    }

    fun load() {
        val chain = _uiState.value.selectedChain
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                var projects = ApiClient.service.getAirdropHub(if (chain == "全部") null else chain)
                val syncStatus = runCatching { ApiClient.service.getAirdropHubStatus() }.getOrNull()
                if (projects.isEmpty() && syncStatus?.last_attempt_at == null) {
                    ApiClient.service.syncAirdrops()
                    projects = ApiClient.service.getAirdropHub(if (chain == "全部") null else chain)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    projects = projects,
                    error = if (projects.isEmpty()) "数据库中暂无项目。可点击刷新同步公开数据源。" else null,
                    sourceError = syncStatus?.error
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "服务器繁忙，请稍后重试。")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                ApiClient.service.syncAirdrops()
                val chain = _uiState.value.selectedChain
                val projects = ApiClient.service.getAirdropHub(if (chain == "全部") null else chain)
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
