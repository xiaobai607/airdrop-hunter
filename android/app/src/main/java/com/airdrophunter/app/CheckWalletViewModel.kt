package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.AssetRepository
import com.airdrophunter.app.data.AssetRepositoryProvider
import com.airdrophunter.app.data.UnifiedAssetData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CheckWalletUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val assets: UnifiedAssetData? = null
)

class CheckWalletViewModel(
    private val repository: AssetRepository = AssetRepositoryProvider.instance
) : ViewModel() {
    private val _state = MutableStateFlow(CheckWalletUiState())
    val state = _state.asStateFlow()

    fun query(address: String) {
        val value = address.trim()
        if (value.isBlank()) return
        viewModelScope.launch {
            _state.value = CheckWalletUiState(isLoading = true)
            val result = repository.fetchAssets(value)
            _state.value = result.fold(
                onSuccess = { CheckWalletUiState(assets = it) },
                onFailure = { CheckWalletUiState(errorMessage = it.message ?: "查询失败，请稍后重试。") }
            )
        }
    }

    fun clearResult() { _state.value = CheckWalletUiState() }
}
