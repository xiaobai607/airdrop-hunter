package com.airdrophunter.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local DApp origin grants, scoped to the active WalletConnect session stamp.
 * Grants are deliberately not persisted and are cleared whenever the session changes.
 */
object DappOriginPermissionStore {
    private val _authorizedOrigins = MutableStateFlow<Map<String, String>>(emptyMap())
    val authorizedOrigins = _authorizedOrigins.asStateFlow()

    @Synchronized
    fun grant(origin: String, authorizationStamp: String) {
        if (origin.isBlank() || authorizationStamp.isBlank()) return
        _authorizedOrigins.value = _authorizedOrigins.value + (origin to authorizationStamp)
    }

    fun isAuthorized(origin: String, authorizationStamp: String): Boolean =
        origin.isNotBlank() && _authorizedOrigins.value[origin] == authorizationStamp

    @Synchronized
    fun revoke(origin: String) {
        _authorizedOrigins.value = _authorizedOrigins.value - origin
    }

    @Synchronized
    fun revokeAll() {
        _authorizedOrigins.value = emptyMap()
    }
}
