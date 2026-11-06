package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.AirdropDto
import com.airdrophunter.app.data.AirdropWriteRequest
import com.airdrophunter.app.data.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AirdropsUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val items: List<AirdropDto> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

class AirdropsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AirdropsUiState(isLoading = true))
    val uiState: StateFlow<AirdropsUiState> = _uiState.asStateFlow()

    fun loadAirdrops() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val items = ApiClient.service.getAirdrops()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    items = items,
                    error = null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "无法读取空投数据"
                )
            }
        }
    }

    fun createAirdrop(request: AirdropWriteRequest) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, message = null)
            try {
                ApiClient.service.createAirdrop(request)
                val items = ApiClient.service.getAirdrops()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    items = items,
                    message = "空投记录已保存到云端"
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = error.message ?: "保存空投记录失败"
                )
            }
        }
    }
}
