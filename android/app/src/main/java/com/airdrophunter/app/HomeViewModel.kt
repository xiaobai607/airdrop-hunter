package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.MarketResponse
import com.airdrophunter.app.data.UserStatsDto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val market: MarketResponse? = null,
    val stats: UserStatsDto? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasError: Boolean = false
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun load(isGuest: Boolean = false) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CacheStore.read("market")?.let {
                    runCatching { Gson().fromJson(it, MarketResponse::class.java) }.getOrNull()
                }?.let { _uiState.value = _uiState.value.copy(market = it, isLoading = false) }
            }
            refresh(isGuest)
        }
    }

    fun refresh(isGuest: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = _uiState.value.market == null,
                isRefreshing = true,
                hasError = false
            )
            try {
                val market = ApiClient.service.getMarketPrices()
                val stats = if (isGuest) UserStatsDto() else ApiClient.service.getUserStats()
                _uiState.value = _uiState.value.copy(
                    market = market, stats = stats, isLoading = false,
                    isRefreshing = false, hasError = false
                )
                withContext(Dispatchers.IO) { CacheStore.write("market", Gson().toJson(market)) }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    stats = if (isGuest) UserStatsDto() else _uiState.value.stats,
                    isLoading = false, isRefreshing = false, hasError = true
                )
            }
        }
    }
}
