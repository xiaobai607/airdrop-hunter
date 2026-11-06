package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.SourceDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SourcesUiState(
    val isLoading: Boolean = false,
    val sources: List<SourceDto> = emptyList(),
    val error: String? = null
)

class SourcesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SourcesUiState(isLoading = true))
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    fun loadSources() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val sources = ApiClient.service.getSources()
                _uiState.value = SourcesUiState(
                    isLoading = false,
                    sources = sources
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "暂时无法读取网站列表"
                )
            }
        }
    }
}
