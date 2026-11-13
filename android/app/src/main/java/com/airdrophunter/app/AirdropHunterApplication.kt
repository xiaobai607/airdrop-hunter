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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect

class AirdropHunterApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer() }
    private val processLifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var walletResponseReconciliationStarted = false

    /**
     * Keeps transaction-hash recovery alive independently of BrowserActivity.
     * This is started only after the user enters the browser or returns through
     * the wallet redirect, so normal app startup does not initialize AppKit.
     */
    @Synchronized
    fun startWalletResponseReconciliation() {
        if (walletResponseReconciliationStarted) return
        walletResponseReconciliationStarted = true
        processLifecycleScope.launch {
            try {
                WalletConnectInitializer.ready.first { it }
                WalletConnectSessionDelegate.requestResponses.collect { response ->
                    if (!response.method.equals("eth_sendTransaction", ignoreCase = true)) {
                        return@collect
                    }
                    val rpcResult = response.result as? Modal.Model.JsonRpcResponse.JsonRpcResult
                        ?: return@collect
                    val txHash = rpcResult.result as? String ?: return@collect
                    val chainId = response.chainId?.removePrefix("eip155:")?.toLongOrNull()
                        ?: return@collect
                    val draftId = runCatching {
                        appContainer.web3TransactionRepository.attachWalletConnectResponse(
                            requestId = rpcResult.id,
                            method = response.method,
                            sessionTopic = response.topic ?: return@collect,
                            chainId = chainId,
                            hash = txHash
                        )
                    }.onFailure {
                        Log.w("AirdropWallet", "Could not reconcile WalletConnect response to a durable draft", it)
                    }.getOrNull()
                    if (draftId != null) {
                        com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(
                            applicationContext, draftId
                        )
                    }
                }
            } catch (error: Exception) {
                walletResponseReconciliationStarted = false
                Log.e("AirdropWallet", "WalletConnect response reconciliation stopped", error)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // One coordinator serializes all background sync pipelines. Individual
        // tasks keep their own cadence, account boundary, and retry semantics.
        WalletConnectSessionDelegate.attachApplicationContext(this)\n        ApiClient.initialize(this)
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
