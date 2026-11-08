package com.airdrophunter.app

import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import kotlinx.coroutines.flow.MutableStateFlow
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

    override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
        _accounts.value = when (approvedSession) {
            is Modal.Model.ApprovedSession.WalletConnectSession ->
                parseAccounts(approvedSession.accounts, approvedSession.metaData?.name.orEmpty())
            is Modal.Model.ApprovedSession.CoinbaseSession ->
                parseAccounts(listOf("${approvedSession.chain}:${approvedSession.address}"), "Coinbase Wallet")
        }
    }

    override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) = Unit

    override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {
        val currentLabel = _accounts.value.firstOrNull()?.walletLabel.orEmpty()
        _accounts.value = parseAccounts(updatedSession.namespaces.values.flatMap { it.accounts }, currentLabel)
    }

    override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) = Unit
    override fun onSessionExtend(session: Modal.Model.Session) = Unit

    override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
        _accounts.value = emptyList()
    }

    override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) { DappRpcCoordinator.onWalletResponse(response) }
    override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) = Unit
    override fun onRequestExpired(request: Modal.Model.ExpiredRequest) = Unit
    override fun onConnectionStateChange(state: Modal.Model.ConnectionState) = Unit
    override fun onError(error: Modal.Model.Error) = Unit

    fun clearSession() {
        _accounts.value = emptyList()
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
