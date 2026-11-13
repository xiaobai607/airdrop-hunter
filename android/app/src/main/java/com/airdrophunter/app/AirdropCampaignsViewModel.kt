package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.AirdropCampaignDto
import com.airdrophunter.app.data.AirdropCampaignFilters
import com.airdrophunter.app.data.AirdropCampaignRepository
import com.airdrophunter.app.data.AirdropHubSourceStatusDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AirdropCampaignsUiState(
    val items: List<AirdropCampaignDto> = emptyList(),
    val selectedTier: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val sourceStatuses: List<AirdropHubSourceStatusDto> = emptyList(),
    val sourceStatusError: String? = null
)

class AirdropCampaignsViewModel(
    private val repository: AirdropCampaignRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AirdropCampaignsUiState())
    val uiState = _uiState.asStateFlow()

    init { load() }

    fun selectTier(tier: String?) {
        if (_uiState.value.selectedTier == tier) return
        _uiState.value = _uiState.value.copy(selectedTier = tier)
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val statuses = repository.loadSourceStatuses()
                _uiState.value = _uiState.value.copy(sourceStatuses = statuses, sourceStatusError = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(sourceStatusError = "暂时无法读取数据源状态")
            }
        }
        val filters = AirdropCampaignFilters(tier = _uiState.value.selectedTier)
        viewModelScope.launch {
            // Publish the last-known-good Room snapshot before waiting for network refresh.
            val cached = repository.readCached(filters)
            if (cached != null) {
                _uiState.value = _uiState.value.copy(items = cached, isLoading = false, error = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
            try {
                val result = repository.loadWithStatus(filters)
                _uiState.value = _uiState.value.copy(
                    items = result.rows,
                    isLoading = false,
                    error = result.warning
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "空投活动暂不可用"
                )
            }
        }
    }

    fun refresh() {
        val filters = AirdropCampaignFilters(tier = _uiState.value.selectedTier)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                val rows = repository.refresh(filters)
                val sourceStatusResult = runCatching { repository.loadSourceStatuses() }
                _uiState.value = _uiState.value.copy(
                    items = rows,
                    isRefreshing = false,
                    sourceStatuses = sourceStatusResult.getOrDefault(_uiState.value.sourceStatuses),
                    sourceStatusError = if (sourceStatusResult.isFailure) "暂时无法读取数据源状态" else null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val latestStatuses = runCatching { repository.loadSourceStatuses() }
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = error.message ?: "刷新失败",
                    sourceStatuses = latestStatuses.getOrDefault(_uiState.value.sourceStatuses),
                    sourceStatusError = if (latestStatuses.isFailure) "暂时无法读取数据源状态" else null
                )
            }
        }
    }
}

class AirdropCampaignsViewModelFactory(
    private val repository: AirdropCampaignRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AirdropCampaignsViewModel::class.java)) {
            return AirdropCampaignsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: \${modelClass.name}")
    }
}
