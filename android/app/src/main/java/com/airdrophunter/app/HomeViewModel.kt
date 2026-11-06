package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.CoinCreateRequest
import com.airdrophunter.app.data.CoinDto
import com.airdrophunter.app.data.MarketResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(val market: MarketResponse? = null, val coins: List<CoinDto> = emptyList(), val isLoading: Boolean = true, val isRefreshing: Boolean = false, val hasError: Boolean = false)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun load(isGuest: Boolean = false) {
        if (isGuest) _uiState.value = _uiState.value.copy(coins = emptyList())
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CacheStore.read("market")?.let { runCatching { Gson().fromJson(it, MarketResponse::class.java) }.getOrNull() }?.let { _uiState.value = _uiState.value.copy(market = it, isLoading = false) }
                if (!isGuest) CacheStore.read("coins")?.let { runCatching { Gson().fromJson<List<CoinDto>>(it, object : TypeToken<List<CoinDto>>() {}.type) }.getOrNull() }?.let { _uiState.value = _uiState.value.copy(coins = it) }
            }
            refresh(isGuest)
        }
    }

    fun refresh(isGuest: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.market == null, isRefreshing = true, hasError = false)
            try {
                val market = ApiClient.service.getMarketPrices()
                val coins = if (isGuest) emptyList() else ApiClient.service.getCoins()
                _uiState.value = HomeUiState(market = market, coins = coins, isLoading = false, isRefreshing = false)
                withContext(Dispatchers.IO) {
                    CacheStore.write("market", Gson().toJson(market))
                    if (!isGuest) CacheStore.write("coins", Gson().toJson(coins))
                }
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false, hasError = true) }
        }
    }

    fun addCoin(request: CoinCreateRequest) {
        viewModelScope.launch {
            try { ApiClient.service.addCoin(request); refresh() }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(hasError = true) }
        }
    }

    fun removeCoin(coin: CoinDto) {
        viewModelScope.launch {
            try { ApiClient.service.removeCoin(coin.id); refresh() }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(hasError = true) }
        }
    }
}
