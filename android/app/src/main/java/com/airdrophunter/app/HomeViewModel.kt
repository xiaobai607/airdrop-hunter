package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.OkxMarketRepository
import com.airdrophunter.app.data.OkxTicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.net.SocketTimeoutException

data class HomeUiState(
    val market: List<OkxTicker> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasError: Boolean = false,
    val marketError: String? = null
)

class HomeViewModel : ViewModel() {
    private val repository = OkxMarketRepository()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun load(isGuest: Boolean = false) {
        refresh(isGuest)
    }

    fun refresh(isGuest: Boolean = true) {
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isRefreshing = true,
                hasError = false,
                marketError = null
            )

            try {
                val tickers = repository.fetchSpotTickers()
                _uiState.value = HomeUiState(
                    market = tickers,
                    isLoading = false,
                    isRefreshing = false
                )
            } catch (error: Exception) {
                val message = when (error) {
                    is UnknownHostException -> "网络连接失败，请检查网络或 VPN 后重试。"
                    is SocketTimeoutException -> "行情请求超时，请稍后点击重试。"
                    else -> error.message ?: "行情服务暂时不可用，请稍后重试。"
                }
                _uiState.value = HomeUiState(
                    market = emptyList(),
                    isLoading = false,
                    isRefreshing = false,
                    hasError = true,
                    marketError = message
                )
            }
        }
    }
}
