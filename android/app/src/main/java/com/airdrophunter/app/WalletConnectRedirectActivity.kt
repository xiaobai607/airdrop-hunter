package com.airdrophunter.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.reown.appkit.client.AppKit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class WalletConnectRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchWalletReturn(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchWalletReturn(intent)
    }

    private fun dispatchWalletReturn(intent: android.content.Intent?) {
        val callbackUri = intent?.data
        if (callbackUri == null ||
            !callbackUri.scheme.equals("airdrophunter", ignoreCase = true) ||
            !callbackUri.host.equals("request", ignoreCase = true)
        ) {
            Log.w("AirdropWallet", "Rejected unexpected WalletConnect redirect URI")
            finish()
            return
        }

        // This Activity can be the cold-start entry after Android killed the app
        // while an external wallet was foregrounded. Reinitialize AppKit before
        // forwarding the redirect envelope; BrowserActivity may not exist yet.
        lifecycleScope.launch {
            val app = application as? android.app.Application
            if (app == null) {
                finish()
                return@launch
            }
            WalletConnectInitializer.ensureInitialized(app)
            val initialized = withTimeoutOrNull(15_000) {
                WalletConnectInitializer.ready.first { it }
            } == true
            if (!initialized) {
                Log.e("AirdropWallet", "AppKit was not ready to process the wallet redirect")
                finish()
                return@launch
            }

            runCatching {
                AppKit.handleDeepLink(callbackUri.toString()) { error ->
                    Log.e("AirdropWallet", "WalletConnect callback dispatch failed", error.throwable)
                }
            }.onFailure {
                Log.e("AirdropWallet", "WalletConnect callback failed", it)
            }
            finish()
        }
    }
}
