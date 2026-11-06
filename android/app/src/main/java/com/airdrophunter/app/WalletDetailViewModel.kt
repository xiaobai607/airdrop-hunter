package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.TronTokenBalance
import com.airdrophunter.app.data.TronscanRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class WalletDetailUiState(
    val address: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val trxBalance: BigDecimal? = null,
    val totalUsd: BigDecimal? = null,
    val tokenList: List<TronTokenBalance> = emptyList(),
    val lastUpdatedEpochSeconds: Long? = null
)

class WalletDetailViewModel(
    private val repository: TronscanRepository = TronscanRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(WalletDetailUiState())
    val uiState = _uiState.asStateFlow()

    private var requestJob: Job? = null
    private var queuedRefreshJob: Job? = null
    private var refreshQueued = false
    private var lastRequestAtMillis = 0L

    private companion object {
        const val MIN_REQUEST_INTERVAL_MILLIS = 5_000L
    }

    fun loadWallet(address: String) {
        val normalized = address.trim()
        if (normalized.isBlank()) {
            clear()
            _uiState.value = WalletDetailUiState(
                errorMessage = "钱包地址为空，无法查询链上资产。"
            )
            return
        }
        if (_uiState.value.address == normalized && requestJob?.isActive == true) return

        queuedRefreshJob?.cancel()
        queuedRefreshJob = null
        refreshQueued = false
        requestJob?.cancel()
        _uiState.value = WalletDetailUiState(address = normalized, isLoading = true)
        startRequest(normalized)
    }

    private fun startRequest(address: String) {
        requestJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                address = address,
                isLoading = true,
                errorMessage = null
            )
            lastRequestAtMillis = System.currentTimeMillis()

            try {
                val result = repository.fetchWalletAssets(address)
                if (_uiState.value.address != address) return@launch
                _uiState.value = _uiState.value.copy(
                    address = result.address,
                    isLoading = false,
                    errorMessage = null,
                    trxBalance = result.trxBalance,
                    totalUsd = result.totalUsd,
                    tokenList = result.tokenList,
                    lastUpdatedEpochSeconds = result.lastUpdatedEpochSeconds
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_uiState.value.address != address) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "查询 TRON 链上资产失败，请稍后重试。"
                )
            } finally {
                if (refreshQueued && _uiState.value.address == address) {
                    scheduleQueuedRefresh(address)
                }
            }
        }
    }

    /**
     * Repeated taps are coalesced into at most one trailing refresh.
     * The first page load is immediate; user refreshes never create parallel calls.
     */
    fun refresh() {
        val address = _uiState.value.address
        if (address.isBlank()) return

        refreshQueued = true
        if (requestJob?.isActive == true || queuedRefreshJob?.isActive == true) return
        scheduleQueuedRefresh(address)
    }

    private fun scheduleQueuedRefresh(address: String) {
        if (queuedRefreshJob?.isActive == true) return

        queuedRefreshJob = viewModelScope.launch {
            val elapsed = System.currentTimeMillis() - lastRequestAtMillis
            val waitMillis = (MIN_REQUEST_INTERVAL_MILLIS - elapsed).coerceAtLeast(0L)
            if (waitMillis > 0L) delay(waitMillis)

            if (refreshQueued && _uiState.value.address == address) {
                refreshQueued = false
                startRequest(address)
            }
            queuedRefreshJob = null
        }
    }

    fun clear() {
        requestJob?.cancel()
        queuedRefreshJob?.cancel()
        requestJob = null
        queuedRefreshJob = null
        refreshQueued = false
        _uiState.value = WalletDetailUiState()
    }

    override fun onCleared() {
        requestJob?.cancel()
        queuedRefreshJob?.cancel()
        super.onCleared()
    }
}
