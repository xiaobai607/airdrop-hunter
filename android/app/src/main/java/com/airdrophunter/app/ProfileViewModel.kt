package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.ProfileRepository
import com.airdrophunter.app.data.UserProfileDto
import com.airdrophunter.app.data.UserStatsDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val stats: UserStatsDto = UserStatsDto(),
    val profile: UserProfileDto? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false
)

class ProfileViewModel(
    private val repository: ProfileRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun load(isGuest: Boolean) {
        if (isGuest) {
            _uiState.value = ProfileUiState()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, hasError = false)
            try {
                val snapshot = repository.load()
                _uiState.value = ProfileUiState(
                    stats = snapshot.stats,
                    profile = snapshot.profile,
                    isLoading = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
            }
        }
    }
}

class ProfileViewModelFactory(
    private val repository: ProfileRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
