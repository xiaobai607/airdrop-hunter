package com.airdrophunter.app

import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Read-only public accounts approved by an external WalletConnect session. */
data class WalletConnectAccount(
    val address: String,
    val chainId: String,
    val walletLabel: String
)

object WalletConnectSessionDelegate : AppKit.ModalDelegate {
    private val _accounts = MutableStateFlow<List<WalletConnectAccount>>(emptyList())
    val accounts = _accounts.asStateFlow()
    private val _sessionGeneration = MutableStateFlow(0L)
    val sessionGeneration = _sessionGeneration.asStateFlow()
    private val _sessionTopic = MutableStateFlow<String?>(null)
    val sessionTopic = _sessionTopic.asStateFlow()
    private val _requestResponses = MutableSharedFlow<Modal.Model.SessionRequestResponse>(extraBufferCapacity = 32)
    val requestResponses = _requestResponses.asSharedFlow()

    private fun publishAccounts(accounts: List<WalletConnectAccount>) {
        DappOriginPermissionStore.revokeAll()
        _accounts.value = accounts
        _sessionGeneration.value += 1L
    }

    override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
        _sessionTopic.value = when (approvedSession) {
            is Modal.Model.ApprovedSession.WalletConnectSession -> approvedSession.topic
            is Modal.Model.ApprovedSession.CoinbaseSession -> null
        }
        publishAccounts(when (approvedSession) {
            is Modal.Model.ApprovedSession.WalletConnectSession ->
                parseAccounts(approvedSession.accounts, approvedSession.metaData?.name.orEmpty())
            is Modal.Model.ApprovedSession.CoinbaseSession ->
                parseAccounts(listOf("${approvedSession.chain}:${approvedSession.address}"), "Coinbase Wallet")
        })
    }

    override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) = Unit

    override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {
        val currentLabel = _accounts.value.firstOrNull()?.walletLabel.orEmpty()
        publishAccounts(parseAccounts(updatedSession.namespaces.values.flatMap { it.accounts }, currentLabel))
    }

    override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) = Unit
    override fun onSessionExtend(session: Modal.Model.Session) = Unit

    override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
        _sessionTopic.value = null
        publishAccounts(emptyList())
    }

    override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
        _requestResponses.tryEmit(response)
    }
    override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) = Unit
    override fun onRequestExpired(request: Modal.Model.ExpiredRequest) = Unit
    override fun onConnectionStateChange(state: Modal.Model.ConnectionState) = Unit
    override fun onError(error: Modal.Model.Error) = Unit

    fun clearSession() {
        _sessionTopic.value = null
        publishAccounts(emptyList())
    }

    private fun parseAccounts(rawAccounts: List<String>, walletLabel: String): List<WalletConnectAccount> =
        rawAccounts.mapNotNull { raw ->
            val parts = raw.split(":")
            if (parts.size < 3 || !parts[0].equals("eip155", ignoreCase = true)) return@mapNotNull null
            val address = parts.last().trim()
            if (!Regex("^0x[a-fA-F0-9]{40}$").matches(address)) return@mapNotNull null
            WalletConnectAccount(
                address = address,
                chainId = parts[1],
                walletLabel = walletLabel.ifBlank { "External Wallet" }
            )
        }.distinctBy { it.address.lowercase() }
}
