package com.airdrophunter.app

import android.net.Uri
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class DappRpcRequest(
    val id: Long,
    val method: String,
    val params: JSONArray
)

internal fun interface DappReplyChannel {
    fun postMessage(message: String)
}

internal object DappProviderBridge {
    const val JS_OBJECT_NAME = "ahWalletBridge"
    val HTTPS_ORIGIN_RULES = setOf("*")

    const val X5_PROMPT_PREFIX = "AH_WALLET_RPC_V1"

    /**
     * X5 prompt callbacks provide the requesting frame URL but not an isMainFrame flag.
     * Only HTTPS requests whose origin exactly matches the currently displayed page are accepted.
     * Cross-origin frames, insecure URLs, user-info URLs, and mismatched ports fail closed.
     */
    fun isTrustedX5Prompt(sourceUrl: String?, pageUrl: String?): Boolean {
        if (sourceUrl.isNullOrBlank() || pageUrl.isNullOrBlank()) return false
        return runCatching {
            val source = Uri.parse(sourceUrl)
            val page = Uri.parse(pageUrl)
            source.scheme.equals("https", ignoreCase = true) &&
                page.scheme.equals("https", ignoreCase = true) &&
                !source.host.isNullOrBlank() &&
                !page.host.isNullOrBlank() &&
                source.userInfo == null &&
                page.userInfo == null &&
                source.host.equals(page.host, ignoreCase = true) &&
                normalizedPort(source) == normalizedPort(page)
        }.getOrDefault(false)
    }

    fun parseX5Prompt(message: String?, defaultValue: String?): DappRpcRequest? {
        if (message != X5_PROMPT_PREFIX || defaultValue.isNullOrBlank()) return null
        return parseRequest(defaultValue)
    }

    /**
     * X5 transport uses WebChromeClient.onJsPrompt. The script is installed after the
     * page finishes loading; it announces ethereum#initialized, but cannot guarantee
     * detection by dApps that only inspect window.ethereum during their initial boot.
     */
    const val X5_PROVIDER_SCRIPT = """
(function () {
  if (window.top !== window || window.ethereum) return;
  var listeners = new Map();
  var state = { accounts: [], chainId: null, connected: false };
  var nextId = 1;
  function emit(name, value) {
    (listeners.get(name) || []).slice().forEach(function (fn) {
      try { fn(value); } catch (_) {}
    });
  }
  var provider = {
    isMetaMask: false,
    isConnected: function () { return state.connected; },
    request: function (args) {
      if (!args || typeof args.method !== "string" || !args.method) {
        return Promise.reject({ code: -32600, message: "Invalid request" });
      }
      var id = nextId++;
      var payload = JSON.stringify({
        id: id,
        method: args.method,
        params: args.params === undefined ? [] : args.params
      });
      var raw;
      try { raw = window.prompt("AH_WALLET_RPC_V1", payload); }
      catch (error) {
        return Promise.reject({ code: -32603, message: error && error.message || "Wallet bridge unavailable" });
      }
      if (raw === null || raw === undefined || raw === "") {
        return Promise.reject({ code: 4001, message: "Wallet request cancelled" });
      }
      var response;
      try { response = JSON.parse(raw); }
      catch (_) { return Promise.reject({ code: -32603, message: "Invalid wallet bridge response" }); }
      if (response.error) {
        var failure = new Error(response.error.message || "Wallet request failed");
        failure.code = response.error.code;
        return Promise.reject(failure);
      }
      if (args.method === "eth_requestAccounts" || args.method === "eth_accounts") {
        state.accounts = response.result || [];
      }
      if (args.method === "eth_chainId") {
        state.chainId = response.result;
        state.connected = true;
      }
      return Promise.resolve(response.result);
    },
    on: function (name, fn) {
      if (typeof fn !== "function") return provider;
      var items = listeners.get(name) || [];
      items.push(fn);
      listeners.set(name, items);
      return provider;
    },
    removeListener: function (name, fn) {
      listeners.set(name, (listeners.get(name) || []).filter(function (item) { return item !== fn; }));
      return provider;
    },
    addListener: function (name, fn) { return provider.on(name, fn); },
    off: function (name, fn) { return provider.removeListener(name, fn); }
  };
  Object.defineProperty(provider, "selectedAddress", {
    enumerable: true, get: function () { return state.accounts[0] || null; }
  });
  Object.defineProperty(provider, "chainId", {
    enumerable: true, get: function () { return state.chainId; }
  });
  Object.defineProperty(window, "ethereum", {
    value: provider, configurable: false, enumerable: true, writable: false
  });
  window.dispatchEvent(new Event("ethereum#initialized"));
})();
"""
    const val PROVIDER_SCRIPT = """
(function () {
  // The bridge is intended for the trusted top-level page only; do not install it in iframes.
  if (window.top !== window || window.ethereum || !window.ahWalletBridge) return;
  var pending = new Map();
  var listeners = new Map();
  var nextId = 1;
  var state = { accounts: [], chainId: null, connected: false };
  var bridge = window.ahWalletBridge;

  function emit(eventName, value) {
    var handlers = listeners.get(eventName) || [];
    handlers.slice().forEach(function (handler) {
      try { handler(value); } catch (_) {}
    });
  }

  bridge.onmessage = function (event) {
    var message;
    try { message = JSON.parse(event.data); } catch (_) { return; }
    if (message.type === "event") {
      if (message.event === "accountsChanged") state.accounts = message.data || [];
      if (message.event === "chainChanged") state.chainId = message.data || null;
      emit(message.event, message.data);
      return;
    }
    var task = pending.get(message.id);
    if (!task) return;
    pending.delete(message.id);
    if (message.error) {
      var error = new Error(message.error.message || "Wallet request failed");
      error.code = message.error.code;
      task.reject(error);
    } else {
      if (task.method === "eth_requestAccounts" || task.method === "eth_accounts") {
        state.accounts = message.result || [];
      }
      if (task.method === "eth_chainId") { state.chainId = message.result; state.connected = true; }
      task.resolve(message.result);
    }
  };

  var provider = {
    isMetaMask: false,
    isConnected: function () { return state.connected; },
    request: function (args) {
      if (!args || typeof args.method !== "string" || !args.method) {
        return Promise.reject({ code: -32600, message: "Invalid request" });
      }
      return new Promise(function (resolve, reject) {
        var id = nextId++;
        pending.set(id, { resolve: resolve, reject: reject, method: args.method });
        try {
          bridge.postMessage(JSON.stringify({
            id: id,
            method: args.method,
            params: args.params === undefined ? [] : args.params
          }));
        } catch (error) {
          pending.delete(id);
          reject({ code: -32603, message: error && error.message ? error.message : "Bridge unavailable" });
        }
      });
    },
    on: function (eventName, handler) {
      if (typeof handler !== "function") return provider;
      var handlers = listeners.get(eventName) || [];
      handlers.push(handler);
      listeners.set(eventName, handlers);
      return provider;
    },
    removeListener: function (eventName, handler) {
      var handlers = listeners.get(eventName) || [];
      listeners.set(eventName, handlers.filter(function (item) { return item !== handler; }));
      return provider;
    },
    addListener: function (eventName, handler) { return provider.on(eventName, handler); },
    off: function (eventName, handler) { return provider.removeListener(eventName, handler); }
  };

  Object.defineProperty(provider, "selectedAddress", {
    enumerable: true,
    get: function () { return state.accounts[0] || null; }
  });
  Object.defineProperty(provider, "chainId", {
    enumerable: true,
    get: function () { return state.chainId; }
  });
  Object.defineProperty(window, "ethereum", {
    value: provider,
    configurable: false,
    enumerable: true,
    writable: false
  });
  window.dispatchEvent(new Event("ethereum#initialized"));
})();
"""

    /**
     * Only these EVM requests may be relayed to the connected external wallet.
     * The wallet itself must display the request and perform user approval/signing.
     * Raw signed transactions and smart-account batch methods remain unsupported.
     */
    private val externalWalletMethods = setOf(
        "personal_sign",
        "eth_sign",
        "eth_signtypeddata",
        "eth_signtypeddata_v3",
        "eth_signtypeddata_v4",
        "eth_signtransaction",
        "eth_sendtransaction"
    )

    fun isSupportedExternalWalletMethod(method: String): Boolean =
        method.trim().lowercase() in externalWalletMethods

    /** Reject transaction payloads that explicitly target a different EVM chain. */
    fun isTransactionChainCompatible(method: String, requestedChainId: String?, activeChainId: String): Boolean {
        if (method.trim().lowercase() !in setOf("eth_sendtransaction", "eth_signtransaction")) return true
        if (requestedChainId.isNullOrBlank()) return true
        val requested = runCatching {
            if (requestedChainId.startsWith("0x", ignoreCase = true)) {
                requestedChainId.substring(2).toLong(16)
            } else {
                requestedChainId.toLong()
            }
        }.getOrNull() ?: return false
        val active = activeChainId.removePrefix("eip155:").removePrefix("0x").let { raw ->
            runCatching {
                if (activeChainId.removePrefix("eip155:").startsWith("0x", ignoreCase = true)) raw.toLong(16)
                else raw.toLong()
            }.getOrNull()
        } ?: return false
        return requested == active
    }

    /** Extract the exact account requested by a signing/transaction method. */
    fun requestedAccountAddress(
        method: String,
        firstParam: String?,
        secondParam: String?,
        transactionFrom: String?
    ): String? {
        val addressPattern = Regex("^0x[a-fA-F0-9]{40}$")
        return when (method.trim().lowercase()) {
            "personal_sign" -> {
                val firstLooksLikeAddress = firstParam?.let(addressPattern::matches) == true
                val secondLooksLikeAddress = secondParam?.let(addressPattern::matches) == true
                when {
                    firstLooksLikeAddress && !secondLooksLikeAddress -> firstParam
                    secondLooksLikeAddress && !firstLooksLikeAddress -> secondParam
                    else -> null
                }
            }
            "eth_sign", "eth_signtypeddata", "eth_signtypeddata_v3",
            "eth_signtypeddata_v4" -> firstParam?.takeIf(addressPattern::matches)
            "eth_signtransaction", "eth_sendtransaction" -> transactionFrom?.takeIf(addressPattern::matches)
            else -> null
        }
    }

    fun isAuthorizedAccountRequest(
        method: String,
        firstParam: String?,
        secondParam: String?,
        transactionFrom: String?,
        accounts: Set<String>
    ): Boolean {
        if (accounts.isEmpty()) return false
        val normalizedAccounts = accounts.map { it.lowercase() }.toSet()
        fun isAuthorizedAddress(value: String?): Boolean =
            !value.isNullOrBlank() &&
                Regex("^0x[a-fA-F0-9]{40}$").matches(value) &&
                value.lowercase() in normalizedAccounts

        return when (method.trim().lowercase()) {
            "personal_sign" -> {
                val addressPattern = Regex("^0x[a-fA-F0-9]{40}$")
                val firstLooksLikeAddress = firstParam?.let(addressPattern::matches) == true
                val secondLooksLikeAddress = secondParam?.let(addressPattern::matches) == true
                when {
                    firstLooksLikeAddress && !secondLooksLikeAddress -> isAuthorizedAddress(firstParam)
                    secondLooksLikeAddress && !firstLooksLikeAddress -> isAuthorizedAddress(secondParam)
                    else -> false
                }
            }
            "eth_sign",
            "eth_signtypeddata",
            "eth_signtypeddata_v3",
            "eth_signtypeddata_v4" -> isAuthorizedAddress(firstParam)
            "eth_signtransaction",
            "eth_sendtransaction" -> isAuthorizedAddress(transactionFrom)
            else -> false
        }
    }

    fun isBlockedRawSubmissionMethod(method: String): Boolean =
        method.trim().lowercase() in setOf(
            "eth_sendrawtransaction",
            "eth_senduseroperation",
            "wallet_sendcalls",
            "wallet_sendtransaction",
            "eth_submittransaction"
        )

    fun parseRequest(raw: String): DappRpcRequest? = try {
        val json = JSONObject(raw)
        val id = json.optLong("id", -1L)
        val method = json.optString("method").trim()
        if (id < 0L || method.isBlank()) {
            null
        } else {
            val rawParams = json.opt("params")
            val params = when (rawParams) {
                is JSONArray -> rawParams
                null, JSONObject.NULL -> JSONArray()
                else -> JSONArray().put(rawParams)
            }
            DappRpcRequest(id, method, params)
        }
    } catch (_: JSONException) {
        null
    }

    fun isMatchingExternalWalletResponse(
        expectedRequestId: Long?,
        responseRequestId: Long,
        expectedTopic: String,
        responseTopic: String,
        expectedMethod: String,
        responseMethod: String,
        expectedChainId: String,
        responseChainId: String?
    ): Boolean =
        expectedRequestId != null &&
            responseRequestId == expectedRequestId &&
            responseTopic == expectedTopic &&
            responseMethod.equals(expectedMethod, ignoreCase = true) &&
            !responseChainId.isNullOrBlank() && responseChainId == expectedChainId

    fun isTrustedMainFrame(sourceOrigin: Uri, pageUrl: String?): Boolean {
        if (pageUrl.isNullOrBlank()) return false
        return runCatching {
            val page = Uri.parse(pageUrl)
            sourceOrigin.scheme.equals("https", ignoreCase = true) &&
                page.scheme.equals("https", ignoreCase = true) &&
                !sourceOrigin.host.isNullOrBlank() &&
                sourceOrigin.host.equals(page.host, ignoreCase = true) &&
                normalizedPort(sourceOrigin) == normalizedPort(page)
        }.getOrDefault(false)
    }

    fun origin(sourceOrigin: Uri): String =
        "https://${sourceOrigin.host.orEmpty().lowercase()}" +
            if (normalizedPort(sourceOrigin) == 443) "" else ":${normalizedPort(sourceOrigin)}"

    fun success(replyProxy: DappReplyChannel, id: Long, result: Any?) {
        replyProxy.postMessage(JSONObject().put("id", id).put("result", result ?: JSONObject.NULL).toString())
    }

    fun error(replyProxy: DappReplyChannel, id: Long, code: Int, message: String) {
        val error = JSONObject().put("code", code).put("message", message)
        replyProxy.postMessage(JSONObject().put("id", id).put("error", error).toString())
    }

    private fun normalizedPort(uri: Uri): Int = if (uri.port >= 0) uri.port else 443
}

internal data class PendingDappConnection(
    val request: DappRpcRequest,
    val origin: String,
    val replyProxy: DappReplyChannel
)

