package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.AirdropCampaignDto
import com.airdrophunter.app.data.AirdropCampaignFilters
import com.airdrophunter.app.data.AirdropCampaignRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AirdropCampaignsUiState(
    val items: List<AirdropCampaignDto> = emptyList(),
    val selectedTier: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
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
        val filters = AirdropCampaignFilters(tier = _uiState.value.selectedTier)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val rows = repository.load(filters)
                _uiState.value = _uiState.value.copy(items = rows, isLoading = false, error = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "空投活动暂不可用")
            }
        }
    }

    fun refresh() {
        val filters = AirdropCampaignFilters(tier = _uiState.value.selectedTier)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                val rows = repository.refresh(filters)
                _uiState.value = _uiState.value.copy(items = rows, isRefreshing = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false, error = error.message ?: "刷新失败")
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
