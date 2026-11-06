package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.TronTokenBalance
import com.airdrophunter.app.data.TronscanRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    /**
     * Call when opening a wallet detail page, or when the user pulls to refresh.
     * A new address cancels the previous in-flight request.
     */
    fun loadWallet(address: String) {
        val normalized = address.trim()
        if (normalized.isBlank()) {
            _uiState.value = WalletDetailUiState(
                errorMessage = "钱包地址为空，无法查询链上资产。"
            )
            return
        }

        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _uiState.value = WalletDetailUiState(
                address = normalized,
                isLoading = true
            )

            try {
                val result = repository.fetchWalletAssets(normalized)
                _uiState.value = WalletDetailUiState(
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
                _uiState.value = WalletDetailUiState(
                    address = normalized,
                    isLoading = false,
                    errorMessage = error.message ?: "查询 TRON 链上资产失败，请检查网络后重试。"
                )
            }
        }
    }

    fun refresh() {
        val address = _uiState.value.address
        if (address.isNotBlank()) loadWallet(address)
    }

    fun clear() {
        requestJob?.cancel()
        requestJob = null
        _uiState.value = WalletDetailUiState()
    }

    override fun onCleared() {
        requestJob?.cancel()
        super.onCleared()
    }
}
