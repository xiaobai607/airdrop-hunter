package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.StatsDto
import com.airdrophunter.app.data.WalletSyncResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val stats: StatsDto? = null,
    val syncResult: WalletSyncResponse? = null,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val hasError: Boolean = false
)

class ProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CacheStore.read("stats")?.let { runCatching { Gson().fromJson(it, StatsDto::class.java) }.getOrNull() }?.let { cached ->
                    _uiState.value = _uiState.value.copy(stats = cached, isLoading = false)
                }
            }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val stats = ApiClient.service.getStats()
                _uiState.value = _uiState.value.copy(stats = stats, isLoading = false, hasError = false)
                withContext(Dispatchers.IO) { CacheStore.write("stats", Gson().toJson(stats)) }
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(isLoading = false, hasError = true) }
        }
    }

    fun syncWallet(publicKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, hasError = false)
            try {
                val result = ApiClient.service.syncWallet(publicKey.trim())
                val stats = ApiClient.service.getStats()
                _uiState.value = _uiState.value.copy(syncResult = result, stats = stats, isSyncing = false, hasError = false)
                withContext(Dispatchers.IO) { CacheStore.write("stats", Gson().toJson(stats)) }
            } catch (_: Exception) { _uiState.value = _uiState.value.copy(isSyncing = false, hasError = true) }
        }
    }
}
