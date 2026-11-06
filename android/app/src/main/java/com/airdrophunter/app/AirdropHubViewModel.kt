package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.AirdropHubDto
import com.airdrophunter.app.data.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

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
                val projects = ApiClient.service.getAirdropHub(if (chain == "全部") null else chain)
                val syncStatus = runCatching { ApiClient.service.getAirdropHubStatus() }.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    projects = projects,
                    error = if (projects.isEmpty() && syncStatus?.error != null) syncStatus.error
                        else if (projects.isEmpty()) "当前数据列表为空，请稍后重试或手动刷新数据源。"
                        else null,
                    sourceError = syncStatus?.error
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = (error as? HttpException)?.response()?.errorBody()?.string()
                    ?: error.message ?: "读取空投大厅失败，请检查网络后重试。"
                _uiState.value = _uiState.value.copy(isLoading = false, error = message)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                ApiClient.service.refreshAirdropHub()
                val chain = _uiState.value.selectedChain
                val projects = ApiClient.service.getAirdropHub(if (chain == "全部") null else chain)
                _uiState.value = _uiState.value.copy(isRefreshing = false, isLoading = false, projects = projects,
                    error = if (projects.isEmpty()) "数据源已请求，但目前没有可展示的项目。" else null, sourceError = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = (error as? HttpException)?.response()?.errorBody()?.string()
                    ?: error.message ?: "数据源暂时不可用，请稍后重试。"
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = message)
            }
        }
    }
}
