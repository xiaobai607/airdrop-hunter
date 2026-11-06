package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.SourceDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SourcesUiState(val isLoading: Boolean = true, val sources: List<SourceDto> = emptyList(), val error: String? = null)

class SourcesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SourcesUiState())
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    fun loadSources() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CacheStore.read("sources")?.let { json ->
                    runCatching { Gson().fromJson<List<SourceDto>>(json, object : TypeToken<List<SourceDto>>() {}.type) }.getOrNull()
                }?.let { _uiState.value = SourcesUiState(isLoading = false, sources = it) }
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val sources = ApiClient.service.getSources()
                _uiState.value = SourcesUiState(isLoading = false, sources = sources)
                withContext(Dispatchers.IO) { CacheStore.write("sources", Gson().toJson(sources)) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "暂时无法读取网站列表")
            }
        }
    }
}
