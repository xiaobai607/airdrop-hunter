package com.airdrophunter.app

import android.app.Application
import android.util.Log
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.GlobalSyncScheduler
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.presets.AppKitChainsPresets

class AirdropHunterApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer() }

    override fun onCreate() {
        super.onCreate()

        // One coordinator serializes all background sync pipelines. Individual
        // tasks keep their own cadence, account boundary, and retry semantics.
        ApiClient.initialize(this)
        GlobalSyncScheduler.ensurePeriodic(this)
        GlobalSyncScheduler.enqueueNow(this)

        val metadata = Core.Model.AppMetaData(
            name = "Airdrop Hunter",
            description = "Connect to external wallets for dApp interactions",
            url = "https://github.com/xiaobai607/airdrop-hunter",
            icons = emptyList(),
            redirect = "airdrophunter://request"
        )
        CoreClient.initialize(
            application = this,
            projectId = BuildConfig.REOWN_PROJECT_ID,
            metaData = metadata
        ) { error ->
            Log.e("AirdropWallet", "WalletConnect core initialization failed", error.throwable)
        }
        AppKit.initialize(
            init = Modal.Params.Init(core = CoreClient),
            onSuccess = { AppKit.setDelegate(WalletConnectSessionDelegate) },
            onError = { error ->
                Log.e("AirdropWallet", "AppKit initialization failed", error.throwable)
            }
        )
        AppKit.setChains(AppKitChainsPresets.ethChains.values.toList())
    }
}
