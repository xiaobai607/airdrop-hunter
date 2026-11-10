package com.airdrophunter.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.reown.appkit.client.AppKit

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
        val callbackUrl = intent?.dataString
        if (callbackUrl.isNullOrBlank()) {
            finish()
            return
        }
        runCatching {
            AppKit.handleDeepLink(callbackUrl) { error ->
                Log.e("AirdropWallet", "WalletConnect callback dispatch failed", error.throwable)
            }
        }.onFailure {
            Log.e("AirdropWallet", "WalletConnect callback failed", it)
        }
        finish()
    }
}
