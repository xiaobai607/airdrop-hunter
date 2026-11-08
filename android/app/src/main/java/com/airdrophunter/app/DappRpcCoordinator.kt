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
        val sessionGeneration: Long
    )

    private val pendingByWalletRequest = ConcurrentHashMap<Long, Pending>()
    private val originEpochs = ConcurrentHashMap<String, AtomicLong>()

    private fun originEpoch(origin: String): AtomicLong =
        originEpochs.computeIfAbsent(origin) { AtomicLong(0L) }

    fun send(
        origin: String,
        pageRequestId: Long,
        method: String,
        paramsJson: String,
        sessionGeneration: Long,
        reply: androidx.webkit.JavaScriptReplyProxy
    ) {
        val requestOriginEpoch = originEpoch(origin).get()
        if (WalletConnectSessionDelegate.sessionGeneration.value != sessionGeneration) {
            DappProviderBridge.error(reply, pageRequestId, 4001, "Wallet session changed before the request was sent")
            return
        }
        try {
            AppKit.request(
                Request(method = method, params = paramsJson),
                onSuccess = { sent ->
                    when (sent) {
                        is SentRequestResult.WalletConnect -> {
                            if (WalletConnectSessionDelegate.sessionGeneration.value != sessionGeneration ||
                                originEpoch(origin).get() != requestOriginEpoch) {
                                DappProviderBridge.error(reply, pageRequestId, 4001, "Wallet session or page changed before the external wallet responded")
                            } else {
                                pendingByWalletRequest[sent.requestId] =
                                    Pending(origin, reply, pageRequestId, sessionGeneration)
                            }
                        }
                        is SentRequestResult.Coinbase -> {
                            if (WalletConnectSessionDelegate.sessionGeneration.value != sessionGeneration ||
                                originEpoch(origin).get() != requestOriginEpoch) {
                                DappProviderBridge.error(reply, pageRequestId, 4001, "Wallet session or page changed before the external wallet responded")
                            } else {
                                val result = sent.results.firstOrNull()
                                when (result) {
                                    is com.reown.appkit.engine.coinbase.CoinbaseResult.Result ->
                                        DappProviderBridge.success(reply, pageRequestId, parseJsonValue(result.value))
                                    is com.reown.appkit.engine.coinbase.CoinbaseResult.Error ->
                                        DappProviderBridge.error(reply, pageRequestId, result.code.toInt(), result.message)
                                    null -> DappProviderBridge.error(reply, pageRequestId, -32603, "Wallet returned an empty response")
                                }
                            }
                        }
                    }
                },
                onError = { error ->
                    DappProviderBridge.error(reply, pageRequestId, 4001, error.message ?: "External wallet request failed")
                }
            )
        } catch (error: Throwable) {
            DappProviderBridge.error(reply, pageRequestId, -32603, error.message ?: "Wallet SDK is unavailable")
        }
    }

    fun onWalletResponse(response: Modal.Model.SessionRequestResponse) {
        val pending = pendingByWalletRequest.remove(response.result.id) ?: return
        if (pending.sessionGeneration != WalletConnectSessionDelegate.sessionGeneration.value) {
            DappProviderBridge.error(pending.reply, pending.pageRequestId, 4001, "Wallet session changed before the response was received")
            return
        }
        when (val result = response.result) {
            is Modal.Model.JsonRpcResponse.JsonRpcResult ->
                DappProviderBridge.success(pending.reply, pending.pageRequestId, parseJsonValue(result.result))
            is Modal.Model.JsonRpcResponse.JsonRpcError ->
                DappProviderBridge.error(pending.reply, pending.pageRequestId, result.code, result.message)
        }
    }

    fun cancelOrigin(origin: String) {
        // Invalidate callbacks that have been sent to AppKit but have not yet
        // returned a WalletConnect request ID and entered the pending map.
        originEpoch(origin).incrementAndGet()
        val entries = pendingByWalletRequest.entries.filter { it.value.origin == origin }
        entries.forEach { entry ->
            if (pendingByWalletRequest.remove(entry.key, entry.value)) {
                DappProviderBridge.error(entry.value.reply, entry.value.pageRequestId, 4001, "The page navigated before the wallet responded")
            }
        }
    }

    fun cancelAllForSessionChange() {
        val entries = pendingByWalletRequest.entries.toList()
        entries.forEach { entry ->
            if (pendingByWalletRequest.remove(entry.key, entry.value)) {
                DappProviderBridge.error(
                    entry.value.reply,
                    entry.value.pageRequestId,
                    4001,
                    "Wallet account or session changed before the response was received"
                )
            }
        }
    }

    fun onRequestExpired(requestId: Long) {
        val pending = pendingByWalletRequest.remove(requestId) ?: return
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
