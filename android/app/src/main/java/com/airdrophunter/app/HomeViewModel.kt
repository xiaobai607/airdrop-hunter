package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.OkxMarketRepository
import com.airdrophunter.app.data.OkxTicker
import com.airdrophunter.app.data.MarketSearchRepository
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.CacheRefreshPolicy
import com.airdrophunter.app.data.CacheDisplayStatus
import kotlinx.coroutines.CancellationException
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
    val marketError: String? = null,
    val lastSuccessfulUpdateAt: Long? = null,
    val marketCacheStatus: CacheDisplayStatus = CacheDisplayStatus.UNKNOWN
)

internal fun homeMarketShouldShowInitialLoading(cachedRows: List<OkxTicker>): Boolean =
    cachedRows.isEmpty()

internal fun resolveHomeMarketRows(
    cachedRows: List<OkxTicker>,
    refreshedRows: List<OkxTicker>?
): List<OkxTicker> = refreshedRows ?: cachedRows

internal fun homeMarketRefreshWarning(errorMessage: String?, hasCachedRows: Boolean): String? = when {
    errorMessage?.startsWith("刚刚刷新过") == true -> "刚刚刷新过，请30秒后再试"
    hasCachedRows -> "行情暂时不可用，正在显示上次缓存"
    else -> errorMessage
}

class HomeViewModel(
    private val repository: OkxMarketRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun load(isGuest: Boolean = false) {
        refreshInternal(isGuest, manual = false)
    }

    fun refresh(isGuest: Boolean = true) {
        refreshInternal(isGuest, manual = true)
    }

    private fun refreshInternal(isGuest: Boolean, manual: Boolean) {
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            val marketRepository = MarketSearchRepository(repository)
            if (!manual) {
                // Publish Room data before asking the shared coordinator to revalidate it.
                val cachedCoins = try {
                    marketRepository.readCachedHotCoins()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
                if (cachedCoins.isNotEmpty()) {
                    val cachedTickers = cachedCoins.map { coin ->
                        OkxTicker("${coin.symbol}-USDT", coin.lastPrice, coin.change24hPercent)
                    }.filter { it.symbol in setOf("BTC", "ETH") }
                    val metadata = try {
                        CacheStore.readCacheMetadata("global-search-hot-coins", "okx-spot")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    val status = CacheRefreshPolicy.displayStatus(metadata, System.currentTimeMillis())
                    val warning = if (status == CacheDisplayStatus.ERROR ||
                        (status == CacheDisplayStatus.STALE && metadata?.lastError != null)
                    ) "行情暂时不可用，正在显示上次缓存" else null
                    _uiState.value = _uiState.value.copy(
                        market = resolveHomeMarketRows(_uiState.value.market, cachedTickers),
                        isLoading = homeMarketShouldShowInitialLoading(cachedTickers),
                        isRefreshing = true,
                        hasError = false,
                        marketError = warning,
                        lastSuccessfulUpdateAt = metadata?.fetchedAt
                            ?: cachedCoins.maxOfOrNull { it.fetchedAt }
                            ?: _uiState.value.lastSuccessfulUpdateAt,
                        marketCacheStatus = status
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = true,
                        isRefreshing = true,
                        hasError = false,
                        marketError = null
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = _uiState.value.market.isEmpty(),
                    isRefreshing = true,
                    hasError = false,
                    marketError = null
                )
            }

            try {
                val cachedCoins = marketRepository.loadHotCoins(manual = manual)
                val tickers = cachedCoins.map { coin ->
                    OkxTicker("${coin.symbol}-USDT", coin.lastPrice, coin.change24hPercent)
                }.filter { it.symbol in setOf("BTC", "ETH") }
                val cacheMetadata = CacheStore.readCacheMetadata("global-search-hot-coins", "okx-spot")
                val cacheStatus = CacheRefreshPolicy.displayStatus(cacheMetadata, System.currentTimeMillis())
                val cacheWarning = if (cacheStatus == CacheDisplayStatus.ERROR ||
                    (cacheStatus == CacheDisplayStatus.STALE && cacheMetadata?.lastError != null)
                ) "行情暂时不可用，正在显示上次缓存" else null
                _uiState.value = _uiState.value.copy(
                    market = resolveHomeMarketRows(_uiState.value.market, tickers),
                    isLoading = false,
                    isRefreshing = false,
                    hasError = false,
                    marketError = cacheWarning,
                    lastSuccessfulUpdateAt = cacheMetadata?.fetchedAt
                        ?: cachedCoins.maxOfOrNull { it.fetchedAt }
                        ?: System.currentTimeMillis(),
                    marketCacheStatus = cacheStatus
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when (error) {
                    is UnknownHostException -> "网络连接失败，请检查网络或 VPN 后重试。"
                    is SocketTimeoutException -> "行情请求超时，请稍后点击重试。"
                    else -> error.message ?: "行情服务暂时不可用，请稍后重试。"
                }
                val retained = _uiState.value.market
                _uiState.value = _uiState.value.copy(
                    market = retained,
                    isLoading = false,
                    isRefreshing = false,
                    hasError = retained.isEmpty(),
                    marketError = homeMarketRefreshWarning(
                        error.message, hasCachedRows = retained.isNotEmpty()
                    ) ?: message,
                    marketCacheStatus = CacheDisplayStatus.ERROR
                )
            }
        }
    }
}

class HomeViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(appContainer.okxMarketRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
