package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.AssetRepository
import androidx.lifecycle.ViewModelProvider
import com.airdrophunter.app.data.UnifiedAssetData
import com.airdrophunter.app.data.ApiService
import com.airdrophunter.app.data.WalletEcosystem
import com.airdrophunter.app.data.WalletTransactionDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

data class WalletDetailUiState(
    val address: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val assets: UnifiedAssetData? = null,
    val transactions: List<WalletTransactionDto> = emptyList(),
    val transactionsLoading: Boolean = false,
    val transactionsError: String? = null,
    val transactionSupportMessage: String? = null
)

class WalletDetailViewModel(
    private val repository: AssetRepository,
    private val apiService: ApiService
) : ViewModel() {
    private val _uiState = MutableStateFlow(WalletDetailUiState())
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var queuedRefreshJob: Job? = null
    private var refreshQueued = false
    private var lastRequestAtMillis = 0L
    private var currentActivityChain: String? = null

    private companion object { const val MIN_REQUEST_INTERVAL_MILLIS = 5_000L }

    fun loadWallet(address: String, ecosystem: String) {
        val normalized = address.trim()
        if (normalized.isBlank()) {
            clear()
            _uiState.value = WalletDetailUiState(errorMessage = "钱包地址为空，无法查询链上资产。")
            return
        }
        val activityChain = when (ecosystem) {
            WalletEcosystem.EVM -> "ethereum"
            WalletEcosystem.TRON -> "tron"
            else -> null
        }
        if (_uiState.value.address == normalized && currentActivityChain == activityChain && requestJob?.isActive == true) return
        currentActivityChain = activityChain
        queuedRefreshJob?.cancel()
        queuedRefreshJob = null
        refreshQueued = false
        requestJob?.cancel()
        _uiState.value = WalletDetailUiState(
            address = normalized,
            isLoading = true,
            transactionsLoading = activityChain != null,
            transactionSupportMessage = if (activityChain == null) "该网络的真实交易费用采集暂未接入" else null
        )
        startRequest(normalized, activityChain)
    }

    private fun startRequest(address: String, activityChain: String?) {
        requestJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                address = address,
                isLoading = true,
                errorMessage = null,
                transactionsLoading = activityChain != null,
                transactionsError = null,
                transactionSupportMessage = if (activityChain == null) "该网络的真实交易费用采集暂未接入" else null
            )
            lastRequestAtMillis = System.currentTimeMillis()
            try {
                coroutineScope {
                    val assetsTask = async { repository.fetchAssets(address) }
                    val activityTask = async {
                        if (activityChain == null) {
                            Result.success(emptyList<WalletTransactionDto>())
                        } else {
                            try {
                                Result.success(apiService.getWalletTransactions(activityChain, address, 50))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Result.failure(error)
                            }
                        }
                    }
                    var assetsDone = false
                    var activityDone = false
                    while (!assetsDone || !activityDone) {
                        select<Unit> {
                            if (!assetsDone) {
                                assetsTask.onAwait { result ->
                                    if (_uiState.value.address == address) {
                                        _uiState.value = _uiState.value.copy(
                                            assets = result.getOrNull(),
                                            isLoading = false,
                                            errorMessage = result.exceptionOrNull()?.message
                                        )
                                    }
                                    assetsDone = true
                                }
                            }
                            if (!activityDone) {
                                activityTask.onAwait { result ->
                                    if (_uiState.value.address == address) {
                                        _uiState.value = _uiState.value.copy(
                                            transactions = result.getOrNull().orEmpty(),
                                            transactionsLoading = false,
                                            transactionsError = result.exceptionOrNull()?.message
                                        )
                                    }
                                    activityDone = true
                                }
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_uiState.value.address != address) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    transactionsLoading = false,
                    errorMessage = error.message ?: "查询链上资产失败，请稍后重试。"
                )
            } finally {
                if (refreshQueued && _uiState.value.address == address) scheduleQueuedRefresh(address, activityChain)
            }
        }
    }

    fun refresh() {
        val address = _uiState.value.address
        if (address.isBlank()) return
        refreshQueued = true
        if (requestJob?.isActive == true || queuedRefreshJob?.isActive == true) return
        scheduleQueuedRefresh(address, currentActivityChain)
    }

    private fun scheduleQueuedRefresh(address: String, activityChain: String?) {
        if (queuedRefreshJob?.isActive == true) return
        queuedRefreshJob = viewModelScope.launch {
            val wait = (MIN_REQUEST_INTERVAL_MILLIS - (System.currentTimeMillis() - lastRequestAtMillis)).coerceAtLeast(0L)
            if (wait > 0) delay(wait)
            if (refreshQueued && _uiState.value.address == address) {
                refreshQueued = false
                startRequest(address, activityChain)
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
        currentActivityChain = null
        _uiState.value = WalletDetailUiState()
    }

    override fun onCleared() {
        requestJob?.cancel()
        queuedRefreshJob?.cancel()
        super.onCleared()
    }
}

class WalletDetailViewModelFactory(
    private val repository: AssetRepository,
    private val apiService: ApiService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletDetailViewModel::class.java)) return WalletDetailViewModel(repository, apiService) as T
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
