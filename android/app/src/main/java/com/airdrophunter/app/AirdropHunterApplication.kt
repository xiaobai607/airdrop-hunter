package com.airdrophunter.app

import android.app.Application
import android.content.Context
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
import com.tencent.smtt.sdk.QbSdk
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

        // X5 pre-initialization is asynchronous; the existing system WebView remains active
        // until the separate X5 browser surface is integrated and explicitly selected.
        X5KernelInitializer.initialize(applicationContext)

        // Start WalletConnect/AppKit initialization as soon as the app process starts.
        // The initializer owns a process-wide scope and guards against duplicate starts.
        WalletConnectInitializer.ensureInitialized(this)

        // Prewarm a small set of verified wallet icons for faster initial rendering.
        // This cache-only optimization does not filter or restrict AppKit's wallet directory.
        processLifecycleScope.launch {
            try {
                val repository = ReownWalletCatalogRepository(applicationContext)
                val curatedWallets = repository.loadCuratedWallets()
                repository.preloadWalletIcons(curatedWallets)
            } catch (error: Exception) {
                Log.w("AirdropWallet", "Curated wallet prewarm skipped", error)
            }
        }

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
                // Keep AppKit's complete WalletConnect Explorer directory.
                // Do not pass includeWalletIds: that parameter restricts the picker
                // to an app-maintained subset and silently hides other supported wallets.
                AppKit.initialize(
                    init = Modal.Params.Init(core = CoreClient),
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
                _error.value = error.message ?: "钱包连接服务初始化失败，请检查网络后重试"
                Log.e("AirdropWallet", "WalletConnect initialization failed", error)
            }
        }
    }
}


/**
 * Process-wide, asynchronous Tencent X5 initialization.
 *
 * This phase deliberately does not replace android.webkit.WebView. The current browser's
 * AndroidX WebKit message bridge is required by WalletConnect and remains on the system
 * WebView until an X5-specific bridge has been separately implemented and security-reviewed.
 */
enum class X5KernelState {
    NOT_STARTED,
    INITIALIZING,
    AVAILABLE,
    UNAVAILABLE
}

object X5KernelInitializer {
    private const val TAG = "AirdropX5"

    @Volatile
    private var initializationStarted = false

    private val _state = MutableStateFlow(X5KernelState.NOT_STARTED)
    val state = _state.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        if (initializationStarted) return
        initializationStarted = true
        _state.value = X5KernelState.INITIALIZING

        val appContext = context.applicationContext
        try {
            QbSdk.initX5Environment(
                appContext,
                object : QbSdk.PreInitCallback {
                    override fun onCoreInitFinished() {
                        Log.i(TAG, "TBS core pre-initialization callback completed")
                    }

                    override fun onViewInitFinished(isX5Available: Boolean) {
                        val actuallyUsingX5 = isX5Available && runCatching {
                            QbSdk.isX5Core()
                        }.getOrDefault(false)

                        if (actuallyUsingX5) {
                            _state.value = X5KernelState.AVAILABLE
                            Log.i(
                                TAG,
                                "X5 ready; sdkVersion=${QbSdk.getTbsSdkVersion()}, " +
                                    "coreVersion=${QbSdk.getTbsVersion(appContext)}"
                            )
                        } else {
                            _state.value = X5KernelState.UNAVAILABLE
                            val help = runCatching {
                                QbSdk.getX5CoreLoadHelp(appContext)
                            }.getOrNull()
                            Log.w(
                                TAG,
                                "X5 unavailable; keep system WebView active. " +
                                    "callbackAvailable=$isX5Available; help=$help"
                            )
                        }
                    }
                }
            )
        } catch (error: Exception) {
            _state.value = X5KernelState.UNAVAILABLE
            Log.e(TAG, "X5 initialization failed; keep system WebView active", error)
        }
    }
}
