package com.airdrophunter.app

import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.client.models.request.Request
import com.reown.appkit.client.models.request.SentRequestResult
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object DappRpcCoordinator {
    private data class Pending(
        val origin: String,
        val reply: androidx.webkit.JavaScriptReplyProxy,
        val pageRequestId: Long,
        val sessionGeneration: Long,
        val originEpoch: Long
    )

    private val pendingByWalletRequest = ConcurrentHashMap<Long, Pending>()
    private val originEpochs = ConcurrentHashMap<String, AtomicLong>()

    private fun originEpoch(origin: String): AtomicLong =
        originEpochs.computeIfAbsent(origin) { AtomicLong(0L) }

    private fun isRequestCurrent(origin: String, epoch: Long, sessionGeneration: Long): Boolean =
        originEpoch(origin).get() == epoch &&
            WalletConnectSessionDelegate.sessionGeneration.value == sessionGeneration

    fun send(
        origin: String,
        pageRequestId: Long,
        method: String,
        paramsJson: String,
        sessionGeneration: Long,
        reply: androidx.webkit.JavaScriptReplyProxy
    ) {
        val requestOriginEpoch = originEpoch(origin).get()
        if (!isRequestCurrent(origin, requestOriginEpoch, sessionGeneration)) {
            DappProviderBridge.error(reply, pageRequestId, 4001, "Wallet session or page changed before the request was sent")
            return
        }
        try {
            AppKit.request(
                Request(method = method, params = paramsJson),
                onSuccess = { sent ->
                    // Navigation can happen while AppKit is opening the external wallet.
                    // Never post to a JavaScriptReplyProxy belonging to an old document.
                    if (!isRequestCurrent(origin, requestOriginEpoch, sessionGeneration)) return@request
                    when (sent) {
                        is SentRequestResult.WalletConnect -> {
                            val pending = Pending(origin, reply, pageRequestId, sessionGeneration, requestOriginEpoch)
                            pendingByWalletRequest[sent.requestId] = pending
                            if (!isRequestCurrent(origin, requestOriginEpoch, sessionGeneration)) {
                                pendingByWalletRequest.remove(sent.requestId, pending)
                            }
                        }
                        is SentRequestResult.Coinbase -> {
                            if (!isRequestCurrent(origin, requestOriginEpoch, sessionGeneration)) return@request
                            when (val result = sent.results.firstOrNull()) {
                                is com.reown.appkit.engine.coinbase.CoinbaseResult.Result ->
                                    DappProviderBridge.success(reply, pageRequestId, parseJsonValue(result.value))
                                is com.reown.appkit.engine.coinbase.CoinbaseResult.Error ->
                                    DappProviderBridge.error(reply, pageRequestId, result.code.toInt(), result.message)
                                null -> DappProviderBridge.error(reply, pageRequestId, -32603, "Wallet returned an empty response")
                            }
                        }
                    }
                },
                onError = { error ->
                    if (isRequestCurrent(origin, requestOriginEpoch, sessionGeneration)) {
                        DappProviderBridge.error(reply, pageRequestId, 4001, error.message ?: "External wallet request failed")
                    }
                }
            )
        } catch (error: Throwable) {
            if (isRequestCurrent(origin, requestOriginEpoch, sessionGeneration)) {
                DappProviderBridge.error(reply, pageRequestId, -32603, error.message ?: "Wallet SDK is unavailable")
            }
        }
    }

    fun onWalletResponse(response: Modal.Model.SessionRequestResponse) {
        val pending = pendingByWalletRequest.remove(response.result.id) ?: return
        if (!isRequestCurrent(pending.origin, pending.originEpoch, pending.sessionGeneration)) return
        when (val result = response.result) {
            is Modal.Model.JsonRpcResponse.JsonRpcResult ->
                DappProviderBridge.success(pending.reply, pending.pageRequestId, parseJsonValue(result.result))
            is Modal.Model.JsonRpcResponse.JsonRpcError ->
                DappProviderBridge.error(pending.reply, pending.pageRequestId, result.code, result.message)
        }
    }

    fun cancelOrigin(origin: String) {
        // Invalidate callbacks that AppKit has not yet returned with a WalletConnect request ID.
        originEpoch(origin).incrementAndGet()
        pendingByWalletRequest.entries
            .filter { it.value.origin == origin }
            .forEach { entry -> pendingByWalletRequest.remove(entry.key, entry.value) }
    }

    fun cancelAllForSessionChange() {
        pendingByWalletRequest.clear()
    }

    fun onRequestExpired(requestId: Long) {
        val pending = pendingByWalletRequest.remove(requestId) ?: return
        if (!isRequestCurrent(pending.origin, pending.originEpoch, pending.sessionGeneration)) return
        DappProviderBridge.error(pending.reply, pending.pageRequestId, 4001, "The external wallet request expired")
    }

    private fun parseJsonValue(raw: String): Any? {
        val trimmed = raw.trim()
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed)
                trimmed.startsWith("[") -> org.json.JSONArray(trimmed)
                trimmed == "null" -> JSONObject.NULL
                trimmed == "true" -> true
                trimmed == "false" -> false
                trimmed.startsWith("\"") -> org.json.JSONTokener(trimmed).nextValue()
                else -> trimmed
            }
        } catch (_: Exception) {
            raw
        }
    }
}
