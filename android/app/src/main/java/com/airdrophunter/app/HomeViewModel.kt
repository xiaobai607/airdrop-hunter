package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.UserStatsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class BinanceTicker(
    val symbol: String,
    val lastPrice: Double,
    val priceChangePercent: Double
) {
    val shortSymbol: String get() = symbol.removeSuffix("USDT")
    val fullName: String get() = when (shortSymbol) {
        "BTC" -> "Bitcoin"
        "ETH" -> "Ethereum"
        "SOL" -> "Solana"
        "BNB" -> "BNB"
        "XRP" -> "XRP"
        else -> shortSymbol
    }
}

data class HomeUiState(
    val market: List<BinanceTicker> = emptyList(),
    val stats: UserStatsDto? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasError: Boolean = false
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun load(isGuest: Boolean = false) {
        refresh(isGuest)
    }

    fun refresh(isGuest: Boolean = true) {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = _uiState.value.market.isEmpty(),
                isRefreshing = true,
                hasError = false
            )
            try {
                val tickers = withContext(Dispatchers.IO) { fetchBinanceTickers() }
                _uiState.value = _uiState.value.copy(
                    market = tickers,
                    stats = UserStatsDto(),
                    isLoading = false,
                    isRefreshing = false,
                    hasError = false
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasError = true
                )
            }
        }
    }

    private fun fetchBinanceTickers(): List<BinanceTicker> {
        val symbols = """["BTCUSDT","ETHUSDT","SOLUSDT","BNBUSDT","XRPUSDT"]"""
        val encoded = URLEncoder.encode(symbols, "UTF-8")
        val connection = (URL("https://api.binance.com/api/v3/ticker/24hr?symbols=$encoded")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AirdropHunter-Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Binance HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val array = JSONArray(body)
            val result = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val symbol = item.optString("symbol")
                val price = item.optString("lastPrice").toDoubleOrNull()
                val change = item.optString("priceChangePercent").toDoubleOrNull()
                if (symbol.isBlank() || price == null || change == null) null
                else BinanceTicker(symbol, price, change)
            }
            val order = listOf("BTC", "ETH", "SOL", "BNB", "XRP")
            return result.sortedBy { order.indexOf(it.shortSymbol).let { rank -> if (rank < 0) Int.MAX_VALUE else rank } }
        } finally {
            connection.disconnect()
        }
    }
}
