package com.airdrophunter.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.airdrophunter.app.data.ApiClient
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.presets.AppKitChainsPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AirdropHunterApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer() }
    private val processLifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // One coordinator serializes all background sync pipelines. Individual
        // tasks keep their own cadence, account boundary, and retry semantics.
        ApiClient.initialize(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    processLifecycleScope.launch {
                        runCatching {
                            val repository = appContainer.web3TransactionRepository
                            repository.markOldWalletRequestsForSync()
                            repository.pendingReceiptDraftIds().forEach { draftId ->
                                com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(
                                    applicationContext, draftId
                                )
                            }
                        }.onFailure {
                            Log.w("AirdropWallet", "Wallet-return reconciliation failed", it)
                        }
                    }
                }
            }
        )
    }
}

object WalletConnectInitializer {
    @Volatile private var initializationStarted = false
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    @Synchronized
    fun ensureInitialized(application: Application) {
        if (initializationStarted) return
        initializationStarted = true
        try {
            val metadata = Core.Model.AppMetaData(
                name = "Airdrop Hunter",
                description = "Connect to external wallets for dApp interactions",
                url = "https://github.com/xiaobai607/airdrop-hunter",
                icons = emptyList(),
                redirect = "airdrophunter://request"
            )
            CoreClient.initialize(
                application = application,
                projectId = BuildConfig.REOWN_PROJECT_ID,
                metaData = metadata
            ) { error ->
                Log.e("AirdropWallet", "WalletConnect core initialization failed", error.throwable)
            }
            AppKit.initialize(
                init = Modal.Params.Init(core = CoreClient),
                onSuccess = {
                    AppKit.setDelegate(WalletConnectSessionDelegate)
                    _ready.value = true
                },
                onError = { error ->
                    initializationStarted = false
                    Log.e("AirdropWallet", "AppKit initialization failed", error.throwable)
                }
            )
            AppKit.setChains(AppKitChainsPresets.ethChains.values.toList())
        } catch (error: Exception) {
            initializationStarted = false
            Log.e("AirdropWallet", "WalletConnect initialization failed", error)
        }
    }
}
