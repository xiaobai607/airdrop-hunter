package com.airdrophunter.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

/**
 * Device-local wallet usage history. This is deliberately separate from the
 * shared public catalog cache and from wallet balances/transactions.
 *
 * A connection is recorded only from AppKit's approved-session callback.
 */
data class WalletUsageRecord(
    val identity: String,
    val displayName: String,
    val successfulConnections: Long,
    val lastConnectedAt: Long
)

object WalletUsageStore {
    private const val PREFS = "wallet_usage_v1"
    private const val RECORDS_KEY = "successful_connections"
    private val gson = Gson()
    private val recordsType = object : TypeToken<Map<String, WalletUsageRecord>>() {}.type

    @Synchronized
    fun recordSuccessfulConnection(
        context: Context,
        walletName: String?,
        walletUrl: String? = null,
        now: Long = System.currentTimeMillis()
    ) {
        val name = walletName?.trim().orEmpty().ifBlank { "External Wallet" }
        val identity = identityFor(name, walletUrl)
        val records = read(context).toMutableMap()
        val previous = records[identity]
        records[identity] = WalletUsageRecord(
            identity = identity,
            displayName = name,
            successfulConnections = (previous?.successfulConnections ?: 0L) + 1L,
            lastConnectedAt = now
        )
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(RECORDS_KEY, gson.toJson(records)).apply()
    }

    @Synchronized
    fun records(context: Context): List<WalletUsageRecord> =
        read(context).values.sortedWith(
            compareByDescending<WalletUsageRecord> { it.successfulConnections }
                .thenByDescending { it.lastConnectedAt }
        )

    private fun read(context: Context): Map<String, WalletUsageRecord> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(RECORDS_KEY, null) ?: return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, WalletUsageRecord>>(raw, recordsType).orEmpty()
        }.getOrDefault(emptyMap())
    }

    private fun identityFor(name: String, url: String?): String =
        canonicalWalletIdentity(name, url)
}

/**
 * Stable local identity for a connected wallet. WalletConnect metadata may
 * provide a bare domain or a URL; normalize both before using the host.
 */
internal fun canonicalWalletIdentity(name: String, url: String?): String {
    val host = runCatching {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return@runCatching null
        val withScheme = if ("://" in raw) raw else "https://$raw"
        val parsedHost = android.net.Uri.parse(withScheme).host
            ?.trimEnd('.')
            ?.lowercase(Locale.ROOT)
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val asciiHost = java.net.IDN.toASCII(
            parsedHost,
            java.net.IDN.USE_STD3_ASCII_RULES
        ).lowercase(Locale.ROOT)
        val labels = asciiHost.split('.')
        asciiHost.takeIf {
            it.length <= 253 &&
                labels.size >= 2 &&
                labels.all { label ->
                    label.length in 1..63 &&
                        label.first().isLetterOrDigit() &&
                        label.last().isLetterOrDigit() &&
                        label.all { character -> character.isLetterOrDigit() || character == '-' }
                }
        }
    }.getOrNull()
    return host?.let { "host:$it" }
        ?: "name:${name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")}"
}
