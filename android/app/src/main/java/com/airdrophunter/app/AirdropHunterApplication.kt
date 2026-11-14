package com.airdrophunter.app

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
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

class AirdropHunterApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.04)
                    .build()
            }
            .crossfade(false)
            .build()

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
        WalletConnectSessionDelegate.attachApplicationContext(this)
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
    private val initializationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _initializing = MutableStateFlow(false)
    val initializing = _initializing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    @Synchronized
    fun ensureInitialized(application: Application) {
        if (initializationStarted || _ready.value) return
        initializationStarted = true
        _initializing.value = true
        _error.value = null
        initializationScope.launch {
            try {
                // Resolve only the APP-owned shortlist from the official Explorer API.
                // The repository caches only these verified entries in the shared public cache.
                val catalog = ReownWalletCatalogRepository(application).loadCuratedWallets()
                val orderedCatalog = CuratedWalletCatalog.orderBySuccessfulConnections(
                    wallets = catalog,
                    usage = WalletUsageStore.records(application)
                )
                val walletIds = orderedCatalog.map { it.id }
                require(walletIds.size >= CuratedWalletCatalog.MINIMUM_VERIFIED_WALLETS) {
                    "Verified wallet shortlist is incomplete"
                }
                ReownWalletCatalogRepository(application).preloadWalletIcons(orderedCatalog)

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
                    init = Modal.Params.Init(
                        core = CoreClient,
                        includeWalletIds = walletIds
                    ),
                    onSuccess = {
                        AppKit.setDelegate(WalletConnectSessionDelegate)
                        _error.value = null
                        _initializing.value = false
                        _ready.value = true
                    },
                    onError = { error ->
                        initializationStarted = false
                        _initializing.value = false
                        _error.value = "钱包连接服务初始化失败，请重试"
                        Log.e("AirdropWallet", "AppKit initialization failed", error.throwable)
                    }
                )
                AppKit.setChains(AppKitChainsPresets.ethChains.values.toList())
            } catch (error: Exception) {
                initializationStarted = false
                _initializing.value = false
                _error.value = error.message ?: "精选钱包目录加载失败，请检查网络后重试"
                Log.e("AirdropWallet", "WalletConnect initialization failed", error)
            }
        }
    }
}
