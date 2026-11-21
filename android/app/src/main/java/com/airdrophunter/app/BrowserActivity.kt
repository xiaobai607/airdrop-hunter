package com.airdrophunter.app
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.webkit.WebViewCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewFeature
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import com.google.accompanist.navigation.material.BottomSheetNavigator
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.client.models.request.Request as ExternalWalletRequest
import com.reown.appkit.client.models.request.SentRequestResult
import com.reown.appkit.ui.appKitGraph
import com.reown.appkit.ui.openAppKit
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.ui.theme.AirdropHunterTheme
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AppIconButton
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.theme.AppDesignTokens
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

private data class BrowserWallet(
    val id: String,
    val chain: String,
    val networkIcon: String,
    val walletName: String,
    val address: String
)

private data class PendingDappExternalRequest(
    val request: DappRpcRequest,
    val origin: String,
    val replyProxy: JavaScriptReplyProxy,
    val sessionTopic: String,
    val chainId: String,
    val walletConnectRequestId: Long? = null,
    val web3DraftId: String? = null
)

class BrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(applicationContext)
        val receivedUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        Log.d(TAG, "BrowserActivity received host: ${displayHost(receivedUrl)}")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val provider = WebView.getCurrentWebViewPackage()
            Log.i(TAG, "WebView provider: ${provider?.packageName ?: "unknown"} version=${provider?.versionName ?: "unknown"}")
        }
        val url = normalizeHttpsUrl(receivedUrl)
        val site = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (url == null) {
            Log.e(TAG, "Rejected invalid browser URL host: ${displayHost(receivedUrl)}")
            Toast.makeText(this, getString(R.string.browser_invalid_url), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val wallets = parseWallets(intent.getStringExtra(EXTRA_WALLETS_JSON).orEmpty())
        val selectedId = intent.getStringExtra(EXTRA_SELECTED_WALLET_ID).orEmpty()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isDarkSystemTheme =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val systemBarColor = if (isDarkSystemTheme) AndroidColor.rgb(18, 18, 20) else AndroidColor.rgb(248, 248, 250)
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (isDarkSystemTheme) {
            0
        } else {
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        setContent {
            AirdropHunterTheme {
                BrowserScreen(site.ifBlank { Uri.parse(url).host.orEmpty() }, url, wallets, selectedId, intent.getStringExtra(EXTRA_QUICK_COPY_ADDRESS).orEmpty()) { finish() }
            }
        }
    }

    private fun normalizeHttpsUrl(value: String): String? {
        return try {
            val trimmed = value.trim()
            if (trimmed.isBlank()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val parsed = Uri.parse(withScheme)
            val secure = when {
                parsed.scheme.equals("https", true) -> parsed
                parsed.scheme.equals("http", true) -> parsed.buildUpon().scheme("https").build()
                else -> return null
            }
            if (secure.host.isNullOrBlank() || secure.userInfo != null) null else secure.toString()
        } catch (_: Exception) { null }
    }

    private fun parseWallets(raw: String): List<BrowserWallet> = try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val address = item.optString("address")
            if (address.isBlank()) return@mapNotNull null
            BrowserWallet(item.optString("id"), item.optString("chain"),
                item.optString("network_icon"), item.optString("wallet_name"), address)
        }
    } catch (_: Exception) { emptyList() }

    companion object {
        private const val TAG = "AirdropBrowser"
        const val EXTRA_URL = "com.airdrophunter.app.extra.BROWSER_URL"
        const val EXTRA_TITLE = "com.airdrophunter.app.extra.BROWSER_TITLE"
        const val EXTRA_WALLETS_JSON = "com.airdrophunter.app.extra.BROWSER_WALLETS_JSON"
        const val EXTRA_SELECTED_WALLET_ID = "com.airdrophunter.app.extra.BROWSER_SELECTED_WALLET_ID"
        const val EXTRA_QUICK_COPY_ADDRESS = "com.airdrophunter.app.extra.BROWSER_QUICK_COPY_ADDRESS"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalMaterialNavigationApi::class)
@Composable
private fun BrowserScreen(
    title: String,
    url: String,
    wallets: List<BrowserWallet>,
    selectedWalletId: String,
    quickCopyAddress: String,
    onExit: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as AirdropHunterApplication
    val web3Repository = remember(app) { app.appContainer.web3TransactionRepository }
    val walletConnectReady by WalletConnectInitializer.ready.collectAsState()
    LaunchedEffect(Unit) {
        WalletConnectInitializer.ensureInitialized(context.applicationContext as android.app.Application)
        app.startWalletResponseReconciliation()
    }
    var browser by remember { mutableStateOf<WebView?>(null) }
    val bridgeReplyProxies = remember { mutableMapOf<String, JavaScriptReplyProxy>() }
    val popupWebViews = remember { mutableStateListOf<WebView>() }
    var selectedWallet by remember(wallets, selectedWalletId) {
        mutableStateOf(wallets.firstOrNull { it.id == selectedWalletId } ?: wallets.firstOrNull())
    }
    var showWalletPicker by remember { mutableStateOf(false) }
    var pendingDappConnection by remember { mutableStateOf<PendingDappConnection?>(null) }
    var pendingDappExternalRequest by remember { mutableStateOf<PendingDappExternalRequest?>(null) }
    var waitingDappWalletApproval by remember { mutableStateOf(false) }
    val authorizedDappOrigins by DappOriginPermissionStore.authorizedOrigins.collectAsState()
    val connectedDappAccounts by WalletConnectSessionDelegate.accounts.collectAsState()
    val connectedSessionGeneration by WalletConnectSessionDelegate.sessionGeneration.collectAsState()
    val activeWalletConnectTopic by WalletConnectSessionDelegate.sessionTopic.collectAsState()
    val dappRequestScope = rememberCoroutineScope()
    var pageState by remember(url) { mutableStateOf(BrowserPageState.Loading) }
    var pageErrorMessage by remember(url) { mutableStateOf<String?>(null) }
    var isSlowLoading by remember(url) { mutableStateOf(false) }
    var webViewGeneration by remember(url) { mutableIntStateOf(0) }
    var currentPageUrl by remember(url) { mutableStateOf(url) }
    var pageStartCount by remember(url) { mutableIntStateOf(0) }
    var showSecurityBanner by remember(url) { mutableStateOf(shouldShowBrowserSecurityBanner(context)) }
    var approvedOrigin by remember(url) { mutableStateOf(DappProviderBridge.origin(Uri.parse(url))) }
    var pendingNavigationUrl by remember { mutableStateOf<String?>(null) }
    val webAlpha by animateFloatAsState(targetValue = if (pageState == BrowserPageState.Success) 1f else 0f, animationSpec = tween(300), label = "webview-fade-in")
    BackHandler { val web = browser; if (web != null && web.canGoBack()) web.goBack() else onExit() }

    DisposableEffect(browser) {
        onDispose {
            bridgeReplyProxies.clear()
            browser?.let { web ->
                runCatching { web.stopLoading() }
                runCatching { web.loadUrl("about:blank") }
                runCatching { web.removeAllViews() }
                runCatching { web.destroy() }
            }
            popupWebViews.toList().forEach { popup ->
                runCatching { popup.stopLoading() }
                runCatching { popup.loadUrl("about:blank") }
                runCatching { popup.removeAllViews() }
                runCatching { popup.destroy() }
            }
            popupWebViews.clear()
        }
    }
    LaunchedEffect(browser) {
        // Load immediately; the safety notice is informational and must never block browsing.
        browser?.loadUrl(url)
    }
    LaunchedEffect(showSecurityBanner) {
        if (showSecurityBanner) {
            kotlinx.coroutines.delay(3_000)
            markBrowserSecurityBannerShown(context)
            showSecurityBanner = false
        }
    }
    LaunchedEffect(pageState, browser) {
        if (pageState == BrowserPageState.Loading) {
            isSlowLoading = false
            kotlinx.coroutines.delay(5_000)
            if (pageState == BrowserPageState.Loading) {
                isSlowLoading = true
                kotlinx.coroutines.delay(25_000)
            }
            if (pageState == BrowserPageState.Loading) {
                pageState = BrowserPageState.Error
                pageErrorMessage = context.getString(R.string.browser_page_timeout_description)
                Log.w("AirdropBrowser", "Main-frame load timed out after 30 seconds")
            }
        }
    }

    val bottomSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden, skipHalfExpanded = true)
    val bottomSheetNavigator = remember(bottomSheetState) { BottomSheetNavigator(bottomSheetState) }
    val walletNavController = rememberNavController(bottomSheetNavigator)

    var lastObservedSessionGeneration by remember {
        mutableStateOf(connectedSessionGeneration)
    }

    LaunchedEffect(Unit) {
        WalletConnectSessionDelegate.requestResponses.collect { response ->
            val pending = pendingDappExternalRequest
            if (pending != null && DappProviderBridge.isMatchingExternalWalletResponse(
                    pending.walletConnectRequestId,
                    response.result.id,
                    pending.sessionTopic,
                    response.topic,
                    pending.request.method,
                    response.method,
                    pending.chainId,
                    response.chainId
                )
            ) {
                when (val result = response.result) {
                    is Modal.Model.JsonRpcResponse.JsonRpcResult -> {
                        if (pending.request.method.equals("eth_sendTransaction", ignoreCase = true)) {
                            val txHash = result.result as? String
                            val draftId = pending.web3DraftId
                            val walletRequestId = pending.walletConnectRequestId
                            val chainNumber = pending.chainId.removePrefix("eip155:").toLongOrNull()
                            if (draftId != null && walletRequestId != null && chainNumber != null &&
                                txHash != null && com.airdrophunter.app.data.Web3TransactionRepository.isValidTxHash(txHash)
                            ) {
                                // Persist the wallet's broadcast hash before acknowledging success
                                // to the DApp. If Android kills this process, the Room draft must
                                // already contain the only reliable recovery handle.
                                val accepted = runCatching {
                                    web3Repository.attachWalletConnectHash(
                                        draftId, walletRequestId, chainNumber, txHash
                                    )
                                }.onFailure {
                                    Log.e("AirdropBrowser", "Could not persist WalletConnect transaction hash", it)
                                }.getOrDefault(false)
                                if (accepted) {
                                    com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(
                                        context.applicationContext, draftId
                                    )
                                } else {
                                    // The wallet has already returned a successful broadcast result.
                                    // Never turn that into a false DApp rejection; keep the draft
                                    // recoverable and let the user reconcile the hash manually.
                                    runCatching { web3Repository.markWalletTimedOut(draftId) }
                                    Log.e("AirdropBrowser", "WalletConnect hash was not attached; draft requires manual chain sync")
                                }
                            } else if (draftId != null) {
                                runCatching { web3Repository.markWalletTimedOut(draftId) }
                                Log.e("AirdropBrowser", "WalletConnect returned an invalid or incomplete transaction hash")
                            }
                        }
                        // A wallet success means the wallet accepted/broadcast the request;
                        // receipt confirmation and ledger accounting remain separate steps.
                        DappProviderBridge.success(pending.replyProxy, pending.request.id, result.result)
                    }
                    is Modal.Model.JsonRpcResponse.JsonRpcError -> {
                        pending.web3DraftId?.let { draftId ->
                            dappRequestScope.launch {
                                if (result.code == 4001) web3Repository.markWalletRejected(draftId)
                                else web3Repository.markWalletTimedOut(draftId)
                            }
                        }
                        DappProviderBridge.error(pending.replyProxy, pending.request.id, result.code, result.message)
                    }
                }
                pendingDappExternalRequest = null
            }
        }
    }

    LaunchedEffect(pendingDappExternalRequest?.request?.id) {
        val requestId = pendingDappExternalRequest?.request?.id ?: return@LaunchedEffect
        kotlinx.coroutines.delay(120_000)
        val pending = pendingDappExternalRequest
        if (pending?.request?.id == requestId) {
            pending.web3DraftId?.let { draftId ->
                web3Repository.markWalletTimedOut(draftId)
            }
            DappProviderBridge.error(pending.replyProxy, requestId, 4001, context.getString(R.string.browser_wallet_timeout))
            pendingDappExternalRequest = null
        }
    }

    LaunchedEffect(waitingDappWalletApproval, connectedDappAccounts, connectedSessionGeneration) {
        if (connectedSessionGeneration != lastObservedSessionGeneration) {
            lastObservedSessionGeneration = connectedSessionGeneration

            // A newly approved session is the expected result of this page's
            // explicit connection prompt. Preserve that one pending origin so
            // the approval below can grant it; all other session changes revoke
            // previously granted site permissions.
            val completingUserApprovedConnection =
                waitingDappWalletApproval &&
                    pendingDappConnection != null &&
                    connectedDappAccounts.isNotEmpty()

            if (!completingUserApprovedConnection) {
                DappOriginPermissionStore.revokeAll()
                pendingDappExternalRequest?.let { pending ->
                    pending.web3DraftId?.let { draftId ->
                        dappRequestScope.launch { web3Repository.markWalletTimedOut(draftId) }
                    }
                    DappProviderBridge.error(pending.replyProxy, pending.request.id, 4100, context.getString(R.string.browser_wallet_session_changed))
                    pendingDappExternalRequest = null
                }
                val accountsChanged = JSONObject()
                    .put("type", "event")
                    .put("event", "accountsChanged")
                    .put("data", JSONArray())
                val chainChanged = JSONObject()
                    .put("type", "event")
                    .put("event", "chainChanged")
                    .put("data", JSONObject.NULL)
                bridgeReplyProxies.values.toList().forEach { proxy ->
                    runCatching { proxy.postMessage(accountsChanged.toString()) }
                    runCatching { proxy.postMessage(chainChanged.toString()) }
                }
            }
        }

        if (waitingDappWalletApproval && connectedDappAccounts.isNotEmpty()) {
            val pending = pendingDappConnection
            if (pending != null) {
                DappOriginPermissionStore.grant(pending.origin, currentDappAuthorizationStamp())
                val addresses = JSONArray(connectedDappAccounts.map { it.address }.distinct())
                DappProviderBridge.success(pending.replyProxy, pending.request.id, addresses)
            }
            pendingDappConnection = null
            waitingDappWalletApproval = false
        }
    }

    LaunchedEffect(waitingDappWalletApproval) {
        if (waitingDappWalletApproval) {
            kotlinx.coroutines.delay(120_000)
            if (waitingDappWalletApproval) {
                pendingDappConnection?.let {
                    DappProviderBridge.error(it.replyProxy, it.request.id, 4001, "Wallet connection timed out or was cancelled")
                }
                pendingDappConnection = null
                waitingDappWalletApproval = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(56.dp),
                    title = { Text(title, color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge, maxLines = 1) },
                    navigationIcon = {
                        AppIconButton(Icons.Default.ArrowBack, context.getString(R.string.browser_back), onExit)
                    },
                    actions = {
                        AppButton(
                            text = context.getString(R.string.browser_connect_wallet),
                            onClick = { walletNavController.openAppKit() },
                            style = AppButtonStyle.SECONDARY,
                            enabled = walletConnectReady,
                            fullWidth = false,
                            heightDp = 44,
                            fontSizeSp = 13
                        )
                        if (selectedWallet != null) {
                            AppButton(
                                text = context.getString(R.string.browser_wallet_button_title, selectedWallet!!.walletName.ifBlank { selectedWallet!!.chain }),
                                onClick = { showWalletPicker = true },
                                modifier = Modifier.widthIn(max = 124.dp),
                                style = AppButtonStyle.TEXT,
                                fullWidth = false,
                                heightDp = 44,
                                fontSizeSp = 12
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = showSecurityBanner || pendingNavigationUrl != null,
                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(tween(250)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
                ) {
                    val targetUrl = pendingNavigationUrl
                    val targetHost = targetUrl?.let(::displayHost)
                    Surface(
                        Modifier.fillMaxWidth().pointerInput(pendingNavigationUrl, showSecurityBanner) {
                            var dragDistance = 0f
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, amount ->
                                    dragDistance += amount
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (dragDistance < -size.height * 0.25f) {
                                        if (pendingNavigationUrl != null) {
                                            pendingNavigationUrl = null
                                        } else {
                                            markBrowserSecurityBannerShown(context)
                                            showSecurityBanner = false
                                        }
                                    }
                                }
                            )
                        },
                        color = if (androidx.compose.foundation.isSystemInDarkTheme())
                            AppDesignTokens.Dark.WarningSoft else AppDesignTokens.Light.WarningSoft
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null,
                                    tint = if (androidx.compose.foundation.isSystemInDarkTheme())
                                        AppDesignTokens.Dark.Warning else AppDesignTokens.Light.Warning,
                                    modifier = Modifier.size(18.dp))
                                Text(
                                    if (targetHost != null)
                                        context.getString(R.string.browser_switch_website_warning, targetHost)
                                    else
                                        context.getString(R.string.browser_safety_notice),
                                    Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2
                                )
                            }
                            if (targetUrl != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppTextButton(onClick = {
                                        approvedOrigin = DappProviderBridge.origin(Uri.parse(targetUrl))
                                        pendingNavigationUrl = null
                                        browser?.loadUrl(targetUrl)
                                    }) { Text(context.getString(R.string.browser_confirm_visit)) }
                                    AppTextButton(onClick = { pendingNavigationUrl = null }) {
                                        Text(context.getString(R.string.browser_stay))
                                    }
                                }
                            } else {
                                AppTextButton(onClick = {
                                    markBrowserSecurityBannerShown(context)
                                    showSecurityBanner = false
                                }) {
                                    Text(context.getString(R.string.browser_got_it))
                                }
                            }
                        }
                    }
                }
                Surface(Modifier.fillMaxWidth().height(40.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(context.getString(R.string.browser_current_host), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        Text(displayHost(currentPageUrl, context.getString(R.string.browser_unknown_host)), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            maxLines = 1)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Color.White)) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
            key(webViewGeneration) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        browser = this
                        val supportsSecureBridge =
                            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                        if (supportsSecureBridge) {
                            WebViewCompat.addWebMessageListener(
                                this,
                                DappProviderBridge.JS_OBJECT_NAME,
                                DappProviderBridge.HTTPS_ORIGIN_RULES
                            ) { sourceView, message, sourceOrigin, isMainFrame, replyProxy ->
                                val request = DappProviderBridge.parseRequest(message.data.orEmpty())
                                if (request == null) {
                                    Log.w("AirdropBrowser", "Ignored malformed DApp provider message")
                                } else if (!isMainFrame || !DappProviderBridge.isTrustedMainFrame(sourceOrigin, sourceView.url)) {
                                    DappProviderBridge.error(replyProxy, request.id, -32000, "Wallet bridge is only available to the trusted top-level HTTPS page")
                                } else {
                                    val origin = DappProviderBridge.origin(sourceOrigin)
                                    bridgeReplyProxies[origin] = replyProxy
                                    when (request.method) {
                                        "eth_requestAccounts" -> {
                                            if (pendingDappConnection != null) {
                                                DappProviderBridge.error(replyProxy, request.id, -32002, "A wallet connection request is already pending")
                                            } else if (authorizedDappOrigins[origin] == currentDappAuthorizationStamp() && connectedDappAccounts.isNotEmpty()) {
                                                DappProviderBridge.success(
                                                    replyProxy,
                                                    request.id,
                                                    JSONArray(connectedDappAccounts.map { it.address }.distinct())
                                                )
                                            } else {
                                                pendingDappConnection = PendingDappConnection(request, origin, replyProxy)
                                            }
                                        }
                                        "eth_accounts" -> {
                                            val addresses = if (authorizedDappOrigins[origin] == currentDappAuthorizationStamp()) {
                                                connectedDappAccounts.map { it.address }.distinct()
                                            } else emptyList()
                                            DappProviderBridge.success(replyProxy, request.id, JSONArray(addresses))
                                        }
                                        "eth_chainId", "net_version" -> {
                                            val rawChainReference = connectedDappAccounts.firstOrNull()?.chainId
                                                ?: runCatching { AppKit.getSelectedChain()?.chainReference }.getOrNull()
                                            val chainReference = rawChainReference
                                                ?.removePrefix("eip155:")
                                                ?.trim()
                                            val chainNumber = when {
                                                chainReference.isNullOrBlank() -> null
                                                chainReference.startsWith("0x", ignoreCase = true) ->
                                                    chainReference.substring(2).toLongOrNull(16)
                                                else -> chainReference.toLongOrNull()
                                            }
                                            if (chainNumber == null || chainNumber <= 0L) {
                                                DappProviderBridge.error(
                                                    replyProxy, request.id, -32000,
                                                    "The active EVM chain is unavailable; connect a wallet or select a supported network"
                                                )
                                            } else {
                                                val result = if (request.method == "eth_chainId") {
                                                    "0x" + chainNumber.toString(16)
                                                } else chainNumber.toString()
                                                DappProviderBridge.success(replyProxy, request.id, result)
                                            }
                                        }
                                        "wallet_switchEthereumChain" -> DappProviderBridge.error(
                                            replyProxy, request.id, 4200, "Switch networks in the connected wallet, then reconnect this site"
                                        )
                                        else -> {
                                            when {
                                                DappProviderBridge.isSupportedExternalWalletMethod(request.method) -> {
                                                    val sessionTopic = activeWalletConnectTopic
                                                    val authorized = authorizedDappOrigins[origin] == currentDappAuthorizationStamp()
                                                    val connectedAddresses = connectedDappAccounts.map { it.address }.toSet()
                                                    val firstRequestParam = request.params.opt(0) as? String
                                                    val secondRequestParam = request.params.opt(1) as? String
                                                    val transactionFrom = request.params.optJSONObject(0)?.optString("from")
                                                    val requestedAddress = DappProviderBridge.requestedAccountAddress(
                                                        request.method, firstRequestParam, secondRequestParam, transactionFrom
                                                    )
                                                    val selectedChainReference = AppKit.getSelectedChain()?.chainReference
                                                        ?.removePrefix("eip155:")
                                                    val account = connectedDappAccounts.firstOrNull { candidate ->
                                                        !requestedAddress.isNullOrBlank() &&
                                                            candidate.address.equals(requestedAddress, ignoreCase = true) &&
                                                            !selectedChainReference.isNullOrBlank() &&
                                                            candidate.chainId.removePrefix("eip155:") == selectedChainReference
                                                    }
                                                    when {
                                                        !authorized || connectedDappAccounts.isEmpty() ->
                                                            DappProviderBridge.error(replyProxy, request.id, 4100, context.getString(R.string.browser_authorize_wallet))
                                                        sessionTopic.isNullOrBlank() ->
                                                            DappProviderBridge.error(replyProxy, request.id, 4200, context.getString(R.string.browser_signing_unsupported))
                                                        pendingDappExternalRequest != null ->
                                                            DappProviderBridge.error(replyProxy, request.id, -32002, context.getString(R.string.browser_pending_request))
                                                        !DappProviderBridge.isAuthorizedAccountRequest(
                                                            request.method, firstRequestParam, secondRequestParam,
                                                            transactionFrom, connectedAddresses
                                                        ) ->
                                                            DappProviderBridge.error(replyProxy, request.id, 4100, context.getString(R.string.browser_wallet_not_authorized))
                                                        account == null ->
                                                            DappProviderBridge.error(replyProxy, request.id, 4901, context.getString(R.string.browser_account_not_authorized))
                                                        !DappProviderBridge.isTransactionChainCompatible(
                                                            request.method,
                                                            request.params.optJSONObject(0)?.optString("chainId"),
                                                            account.chainId
                                                        ) ->
                                                            DappProviderBridge.error(replyProxy, request.id, 4901, context.getString(R.string.browser_chain_mismatch))
                                                        else -> {
                                                            val chainReference = account.chainId.let {
                                                                if (it.startsWith("eip155:")) it else "eip155:$it"
                                                            }
                                                            val pending = PendingDappExternalRequest(
                                                                request = request,
                                                                origin = origin,
                                                                replyProxy = replyProxy,
                                                                sessionTopic = sessionTopic,
                                                                chainId = chainReference
                                                            )
                                                            pendingDappExternalRequest = pending
                                                            dappRequestScope.launch {
                                                                var draftId: String? = null
                                                                if (request.method.equals("eth_sendTransaction", ignoreCase = true)) {
                                                                    val transaction = request.params.optJSONObject(0)
                                                                    val chainNumber = chainReference.removePrefix("eip155:").toLongOrNull()
                                                                    if (transaction == null || chainNumber == null) {
                                                                        DappProviderBridge.error(replyProxy, request.id, -32602, context.getString(R.string.browser_invalid_tx_params))
                                                                        pendingDappExternalRequest = null
                                                                        return@launch
                                                                    }
                                                                    draftId = runCatching {
                                                                        web3Repository.createDappDraft(
                                                                            chainId = chainNumber,
                                                                            walletAddress = account.address,
                                                                            origin = origin,
                                                                            transaction = transaction
                                                                        ).id
                                                                    }.getOrElse {
                                                                        Log.e("AirdropBrowser", "Failed to persist Web3 transaction draft", it)
                                                                        DappProviderBridge.error(replyProxy, request.id, -32000, context.getString(R.string.browser_draft_save_blocked))
                                                                        pendingDappExternalRequest = null
                                                                        return@launch
                                                                    }
                                                                }
                                                                val current = pendingDappExternalRequest
                                                                if (current?.request?.id != request.id) {
                                                                    draftId?.let { web3Repository.markWalletTimedOut(it) }
                                                                    return@launch
                                                                }
                                                                pendingDappExternalRequest = current.copy(web3DraftId = draftId)
                                                                runCatching {
                                                                    AppKit.request(
                                                                        request = ExternalWalletRequest(
                                                                            method = request.method,
                                                                            params = request.params.toString()
                                                                        ),
                                                                        onSuccess = { sentRequest ->
                                                                            dappRequestScope.launch {
                                                                                val currentPending = pendingDappExternalRequest
                                                                                if (currentPending?.request?.id == request.id) {
                                                                                    val walletConnectRequest = sentRequest as? SentRequestResult.WalletConnect
                                                                                    if (walletConnectRequest == null ||
                                                                                        walletConnectRequest.sessionTopic != currentPending.sessionTopic
                                                                                    ) {
                                                                                        currentPending.web3DraftId?.let { id ->
                                                                                            web3Repository.markWalletTimedOut(id)
                                                                                        }
                                                                                        DappProviderBridge.error(
                                                                                            replyProxy, request.id, 4200,
                                                                                            context.getString(R.string.browser_request_id_invalid)
                                                                                        )
                                                                                        pendingDappExternalRequest = null
                                                                                    } else {
                                                                                        val draftBound = currentPending.web3DraftId?.let { id ->
                                                                                            web3Repository.bindWalletConnectRequest(
                                                                                                id,
                                                                                                walletConnectRequest.requestId,
                                                                                                walletConnectRequest.sessionTopic
                                                                                            )
                                                                                        } ?: true
                                                                                        if (!draftBound) {
                                                                                            currentPending.web3DraftId?.let { id ->
                                                                                                web3Repository.markWalletTimedOut(id)
                                                                                            }
                                                                                            DappProviderBridge.error(
                                                                                                replyProxy, request.id, 4200,
                                                                                                context.getString(R.string.browser_draft_link_failed)
                                                                                            )
                                                                                            pendingDappExternalRequest = null
                                                                                        } else {
                                                                                            currentPending.web3DraftId?.let { id ->
                                                                                                com.airdrophunter.app.data.Web3DraftTimeoutScheduler.enqueue(
                                                                                                    context.applicationContext, id
                                                                                                )
                                                                                            }
                                                                                            pendingDappExternalRequest = currentPending.copy(
                                                                                                walletConnectRequestId = walletConnectRequest.requestId
                                                                                            )
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        },
                                                                        onError = { error: Throwable ->
                                                                            dappRequestScope.launch {
                                                                                if (pendingDappExternalRequest?.request?.id == request.id) {
                                                                                    pendingDappExternalRequest?.web3DraftId?.let { id ->
                                                                                        web3Repository.markWalletTimedOut(id)
                                                                                    }
                                                                                    DappProviderBridge.error(
                                                                                        replyProxy, request.id, 4001,
                                                                                        error.localizedMessage ?: context.getString(R.string.browser_request_failed)
                                                                                    )
                                                                                    pendingDappExternalRequest = null
                                                                                }
                                                                            }
                                                                        }
                                                                    )
                                                                }.onFailure { error ->
                                                                    // AppKit.request can fail after navigation/session changes.
                                                                    // Never let a stale launch failure clear or mutate a newer request.
                                                                    val active = pendingDappExternalRequest
                                                                    if (active?.request?.id == request.id &&
                                                                        active.sessionTopic == sessionTopic &&
                                                                        active.origin == origin
                                                                    ) {
                                                                        active.web3DraftId?.let { id ->
                                                                            web3Repository.markWalletTimedOut(id)
                                                                        }
                                                                        DappProviderBridge.error(
                                                                            replyProxy, request.id, 4001,
                                                                            error.localizedMessage ?: context.getString(R.string.browser_open_wallet_request_failed)
                                                                        )
                                                                        pendingDappExternalRequest = null
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                DappProviderBridge.isBlockedRawSubmissionMethod(request.method) ->
                                                    DappProviderBridge.error(replyProxy, request.id, 4200, context.getString(R.string.browser_unsupported_raw_tx))
                                                else ->
                                                    DappProviderBridge.error(replyProxy, request.id, 4200, "Unsupported wallet method: ${request.method}")
                                            }
                                        }
                                    }
                                }
                            }
                            WebViewCompat.addDocumentStartJavaScript(
                                this,
                                DappProviderBridge.PROVIDER_SCRIPT,
                                DappProviderBridge.HTTPS_ORIGIN_RULES
                            )
                        } else {
                            Log.e("AirdropBrowser", "Secure WebView message bridge is not supported by this WebView provider")
                        }
                        setBackgroundColor(AndroidColor.WHITE)
                        settings.apply {
                            // Present a Chrome-compatible mobile UA while retaining the WebView's
                            // actual Chromium version; do not hardcode a stale Chrome release.
                            userAgentString = chromeCompatibleUserAgent(userAgentString)
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadsImagesAutomatically = true
                            allowFileAccess = false
                            allowContentAccess = false
                            allowUniversalAccessFromFileURLs = false
                            allowFileAccessFromFileURLs = false
                            // Allow window.open/target=_blank only when WebChromeClient confirms a user gesture.
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(true)
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            safeBrowsingEnabled = true
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        // This setting is scoped to this embedded browser WebView only. It improves
                        // OAuth/SSO and cross-site login compatibility; it does not alter other apps.
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                val message = consoleMessage ?: return false
                                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR ||
                                    message.messageLevel() == ConsoleMessage.MessageLevel.WARNING
                                ) {
                                    // Never log console text or full source URLs: either may contain secrets.
                                    val sourceHost = runCatching {
                                        Uri.parse(message.sourceId()).host
                                    }.getOrNull().orEmpty().ifBlank { "unknown" }
                                    Log.w(
                                        "AirdropBrowser",
                                        "Page console: level=${message.messageLevel()} host=$sourceHost line=${message.lineNumber()}"
                                    )
                                }
                                return false
                            }
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.deny()
                            }
                            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback?) {
                                callback?.invoke(origin, false, false)
                            }
                            override fun onShowFileChooser(webView: WebView?, filePathCallback: android.webkit.ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                                filePathCallback?.onReceiveValue(null)
                                return true
                            }
                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                // Reject scripted popups without a real user gesture.
                                if (!isUserGesture || view == null || resultMsg == null) return false
                                val popup = WebView(context)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true)
                                popup.settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    userAgentString = view.settings.userAgentString
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    safeBrowsingEnabled = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    allowUniversalAccessFromFileURLs = false
                                    allowFileAccessFromFileURLs = false
                                    javaScriptCanOpenWindowsAutomatically = false
                                    setSupportMultipleWindows(false)
                                }
                                popup.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(child: WebView?, request: WebResourceRequest?): Boolean {
                                        val target = request?.url ?: return true
                                        val scheme = target.scheme?.lowercase()
                                        if (scheme != "https" && scheme != "http") return true
                                        val secureTarget = if (scheme == "http") target.buildUpon().scheme("https").build() else target
                                        if (secureTarget.host.isNullOrBlank() || secureTarget.userInfo != null) return true
                                        val targetOrigin = runCatching { DappProviderBridge.origin(secureTarget) }.getOrNull() ?: return true
                                        if (targetOrigin == approvedOrigin) {
                                            view.loadUrl(secureTarget.toString())
                                        } else {
                                            pendingNavigationUrl = secureTarget.toString()
                                        }
                                        return true
                                    }
                                }
                                popupWebViews.add(popup)
                                val transport = resultMsg.obj as? WebView.WebViewTransport ?: run {
                                    popupWebViews.remove(popup)
                                    popup.destroy()
                                    return false
                                }
                                transport.webView = popup
                                resultMsg.sendToTarget()
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url ?: return true
                                val scheme = target.scheme?.lowercase()
                                if (scheme in setOf("metamask", "trust", "trustwallet", "ethereum", "wc")) {
                                    if (request?.hasGesture() != true) {
                                        Log.w("AirdropBrowser", "Blocked wallet deep link without a user gesture")
                                        return true
                                    }
                                    if (scheme == "wc") {
                                        if (!walletConnectReady) {
                                            Toast.makeText(context, context.getString(R.string.browser_wallet_service_not_ready), Toast.LENGTH_LONG).show()
                                        } else {
                                            runCatching {
                                                AppKit.handleDeepLink(target.toString()) { error ->
                                                    Log.e("AirdropWallet", "WalletConnect deep link failed", error.throwable)
                                                }
                                            }.onFailure {
                                                Toast.makeText(context, context.getString(R.string.browser_invalid_wc_link), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        return true
                                    }
                                    val parsed = com.airdrophunter.app.data.WalletTransactionLinkParser.parse(target)
                                    dappRequestScope.launch {
                                        var draftId: String? = null
                                        if (parsed != null && com.airdrophunter.app.data.Web3RpcRegistry.endpoint(parsed.chainId) != null) {
                                            draftId = runCatching {
                                                web3Repository.createExternalLinkDraft(
                                                    parsed = parsed,
                                                    origin = runCatching { DappProviderBridge.origin(Uri.parse(currentPageUrl)) }.getOrDefault("unknown"),
                                                    walletAddress = selectedWallet?.address
                                                ).id
                                            }.onFailure {
                                                Log.e("AirdropBrowser", "Could not persist wallet transaction draft", it)
                                            }.getOrNull()
                                        } else if (parsed != null) {
                                            Toast.makeText(context, context.getString(R.string.browser_rpc_unsupported), Toast.LENGTH_LONG).show()
                                        }
                                        draftId?.let {
                                            com.airdrophunter.app.data.Web3DraftTimeoutScheduler.enqueue(
                                                context.applicationContext, it
                                            )
                                        }
                                        val walletIntent = Intent(Intent.ACTION_VIEW, target)
                                        if (walletIntent.resolveActivity(context.packageManager) == null) {
                                            draftId?.let { web3Repository.markWalletUnavailable(it) }
                                            Toast.makeText(context, context.getString(R.string.browser_wallet_app_not_found), Toast.LENGTH_LONG).show()
                                        } else {
                                            runCatching {
                                                context.startActivity(Intent.createChooser(walletIntent, context.getString(R.string.browser_choose_wallet)))
                                            }.onFailure {
                                                draftId?.let { id -> web3Repository.markWalletTimedOut(id) }
                                                Toast.makeText(context, context.getString(R.string.browser_open_wallet_failed), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    return true
                                }
                                val secureTarget = when {
                                    target.scheme.equals("https", true) -> target
                                    target.scheme.equals("http", true) -> target.buildUpon().scheme("https").build()
                                    else -> return true
                                }
                                if (secureTarget.host.isNullOrBlank() || secureTarget.userInfo != null) return true
                                val targetOrigin = runCatching { DappProviderBridge.origin(secureTarget) }.getOrNull() ?: return true
                                val needsHttpsUpgrade = !target.scheme.equals("https", true)
                                if (targetOrigin != approvedOrigin) {
                                    pendingNavigationUrl = secureTarget.toString()
                                    return true
                                }
                                if (needsHttpsUpgrade) {
                                    view?.loadUrl(secureTarget.toString())
                                    return true
                                }
                                return false
                            }
                            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                                pageUrl?.let {
                                    val isNewDocument = pageStartCount > 0
                                    pageStartCount += 1
                                    bridgeReplyProxies.clear()
                                    val previousOrigin = runCatching { DappProviderBridge.origin(Uri.parse(currentPageUrl)) }.getOrNull()
                                    val currentOrigin = runCatching { DappProviderBridge.origin(Uri.parse(it)) }.getOrNull()
                                    if (previousOrigin != null && currentOrigin != previousOrigin) {
                                        DappOriginPermissionStore.revokeAll()
                                    }
                                    if (isNewDocument) {
                                        pendingDappConnection?.let { pending ->
                                            DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "The page navigated before wallet access was approved")
                                        }
                                        pendingDappExternalRequest?.let { pending ->
                                            pending.web3DraftId?.let { draftId ->
                                                dappRequestScope.launch { web3Repository.markWalletTimedOut(draftId) }
                                            }
                                            DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, context.getString(R.string.browser_redirected_pending_sync))
                                        }
                                        pendingDappConnection = null
                                        pendingDappExternalRequest = null
                                        waitingDappWalletApproval = false
                                    }
                                    currentPageUrl = it
                                }
                                pageState = BrowserPageState.Loading
                                pageErrorMessage = null
                                Log.d("AirdropBrowser", "Page started: ${displayHost(pageUrl.orEmpty())}")
                            }

                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                pageUrl?.let { currentPageUrl = it }
                                pageState = BrowserPageState.Success
                                pageErrorMessage = null
                                Log.d("AirdropBrowser", "Page finished: ${displayHost(pageUrl.orEmpty())}")
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    pageState = BrowserPageState.Error
                                    pageErrorMessage = context.getString(R.string.browser_network_error, error?.errorCode?.toString() ?: context.getString(R.string.browser_unknown), error?.description ?: context.getString(R.string.browser_no_error_details))
                                    Log.e("AirdropBrowser", "Main-frame load error: ${error?.errorCode} ${error?.description}")
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                    pageState = BrowserPageState.Error
                                    pageErrorMessage = context.getString(R.string.browser_http_error, errorResponse?.statusCode?.toString() ?: context.getString(R.string.browser_unknown), errorResponse?.reasonPhrase ?: context.getString(R.string.browser_server_error))
                                    Log.e("AirdropBrowser", "Main-frame HTTP error: ${errorResponse?.statusCode}")
                                }
                            }

                            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                // Never allow a WebView page to bypass TLS certificate validation.
                                handler?.cancel()
                                pageState = BrowserPageState.Error
                                pageErrorMessage = context.getString(R.string.browser_tls_error)
                                Log.e("AirdropBrowser", "Blocked page because TLS certificate validation failed; primaryError=${error?.primaryError}")
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: android.webkit.RenderProcessGoneDetail?
                            ): Boolean {
                                Log.e(
                                    "AirdropBrowser",
                                    "WebView renderer process exited; crashed=${detail?.didCrash()}, priorityAtExit=${detail?.rendererPriorityAtExit()}"
                                )
                                bridgeReplyProxies.clear()
                                pendingDappConnection?.let { pending ->
                                    runCatching {
                                        DappProviderBridge.error(
                                            pending.replyProxy,
                                            pending.request.id,
                                            4001,
                                            context.getString(R.string.browser_renderer_gone_reload)
                                        )
                                    }
                                }
                                pendingDappExternalRequest?.let { pending ->
                                    pending.web3DraftId?.let { draftId ->
                                        dappRequestScope.launch { web3Repository.markWalletTimedOut(draftId) }
                                    }
                                    runCatching {
                                        DappProviderBridge.error(
                                            pending.replyProxy,
                                            pending.request.id,
                                            4001,
                                            context.getString(R.string.browser_renderer_gone_pending)
                                        )
                                    }
                                }
                                pendingDappConnection = null
                                pendingDappExternalRequest = null
                                waitingDappWalletApproval = false
                                DappOriginPermissionStore.revokeAll()
                                browser = null
                                pageState = BrowserPageState.Error
                                pageErrorMessage = context.getString(R.string.browser_renderer_crashed)
                                webViewGeneration += 1
                                return true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().background(Color.White).alpha(webAlpha),
                update = { view -> if (browser !== view) browser = view }
            )
            }
            if (pageState != BrowserPageState.Success) {
                BrowserPageOverlay(
                    state = pageState,
                    errorDetail = pageErrorMessage,
                    isSlowLoading = isSlowLoading,
                    onBack = onExit,
                    onOpenExternally = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentPageUrl)))
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.browser_no_external_browser), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRetry = {
                        pageState = BrowserPageState.Loading
                        isSlowLoading = false
                        pageErrorMessage = null
                        browser?.reload()
                    }
                )
            }
            BrowserAccountingToolbar(
                walletAddress = quickCopyAddress.ifBlank { selectedWallet?.address.orEmpty() },
                chainName = selectedWallet?.chain.orEmpty(),
                onCopyAddress = {
                    val address = quickCopyAddress.ifBlank { selectedWallet?.address.orEmpty() }
                    if (address.isBlank()) Toast.makeText(context, context.getString(R.string.browser_no_wallet_address), Toast.LENGTH_SHORT).show()
                    else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Wallet address", address))
                        Toast.makeText(context, context.getString(R.string.browser_wallet_copied), Toast.LENGTH_SHORT).show()
                    }
                },
                onRefreshPage = { browser?.reload() },
                onCopyPageUrl = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Page URL", currentPageUrl))
                    Toast.makeText(context, context.getString(R.string.browser_url_copied), Toast.LENGTH_SHORT).show()
                },
                onOpenPageExternally = {
                    val uri = Uri.parse(currentPageUrl)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                        Toast.makeText(context, context.getString(R.string.browser_no_external_browser), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp)
            )
            }

        }
    }

    pendingDappConnection?.takeIf { !waitingDappWalletApproval }?.let { pending ->
        val requestedHost = runCatching { Uri.parse(pending.origin).host.orEmpty() }.getOrDefault("unknown site")
        AlertDialog(
            onDismissRequest = {
                DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "User rejected wallet connection")
                pendingDappConnection = null
            },
            title = { Text(context.getString(R.string.browser_connect_request_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.browser_source_website, requestedHost), color = Color(0xFFB42318), fontWeight = FontWeight.Bold)
                    Text(context.getString(R.string.browser_connect_explanation), color = MaterialTheme.colorScheme.onSurface)
                }
            },
            confirmButton = {
                AppTextButton(
                    enabled = connectedDappAccounts.isNotEmpty() || walletConnectReady,
                    onClick = {
                    val addresses = connectedDappAccounts.map { it.address }.distinct()
                    if (addresses.isNotEmpty()) {
                        DappOriginPermissionStore.grant(pending.origin, currentDappAuthorizationStamp())
                        DappProviderBridge.success(pending.replyProxy, pending.request.id, JSONArray(addresses))
                        pendingDappConnection = null
                    } else {
                        waitingDappWalletApproval = true
                        if (walletConnectReady) walletNavController.openAppKit()
                    }
                }) { Text(if (connectedDappAccounts.isEmpty() && !walletConnectReady) context.getString(R.string.browser_wallet_service_starting) else context.getString(R.string.browser_confirm_connect), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "User rejected wallet connection")
                    pendingDappConnection = null
                }) { Text(context.getString(R.string.browser_reject)) }
            }
        )
    }

    NavHost(walletNavController, startDestination = "browser_wallet_host") {
        composable("browser_wallet_host") { }
        appKitGraph(walletNavController)
    }
    }

    if (showWalletPicker) {
        Dialog(onDismissRequest = { showWalletPicker = false }) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(context.getString(R.string.browser_select_wallet), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (wallets.isEmpty()) Text(context.getString(R.string.browser_no_saved_wallet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    wallets.forEach { wallet ->
                        AppTextButton(onClick = { selectedWallet = wallet; showWalletPicker = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("${wallet.walletName.ifBlank { wallet.chain }}  ·  ${shortAddress(wallet.address)}")
                        }
                    }
                    AppTextButton(onClick = { showWalletPicker = false }, modifier = Modifier.align(Alignment.End)) { Text(context.getString(R.string.common_close)) }
                }
            }
        }
    }
}


private const val BROWSER_SECURITY_PREFS = "browser_security_notice_v1"
private const val BROWSER_SECURITY_DATE_KEY = "last_banner_date"
private const val BROWSER_SECURITY_COUNT_KEY = "banner_show_count"

private fun shouldShowBrowserSecurityBanner(context: Context): Boolean {
    val preferences = context.getSharedPreferences(BROWSER_SECURITY_PREFS, Context.MODE_PRIVATE)
    val lastShown = preferences.getString(BROWSER_SECURITY_DATE_KEY, null)
    return lastShown != LocalDate.now().toString()
}

private fun markBrowserSecurityBannerShown(context: Context) {
    val preferences = context.getSharedPreferences(BROWSER_SECURITY_PREFS, Context.MODE_PRIVATE)
    preferences.edit()
        .putString(BROWSER_SECURITY_DATE_KEY, LocalDate.now().toString())
        .putInt(BROWSER_SECURITY_COUNT_KEY, preferences.getInt(BROWSER_SECURITY_COUNT_KEY, 0) + 1)
        .apply()
}

private fun chromeCompatibleUserAgent(defaultUserAgent: String): String {
    // Android WebView normally appends "; wv" and "Version/4.0" to its Chromium UA.
    // Removing those WebView markers improves site compatibility without inventing a
    // Chrome version or pretending that WebView has Chrome's full browser capabilities.
    if (!defaultUserAgent.contains("Chrome/", ignoreCase = true)) return defaultUserAgent
    return defaultUserAgent
        .replace("; wv)", ")")
        .replace("Version/4.0 ", "")
}

private fun displayHost(rawUrl: String, unknownHost: String = "Unknown host"): String {
    return runCatching {
        Uri.parse(rawUrl).host.orEmpty().lowercase().ifBlank { unknownHost }
    }.getOrDefault(unknownHost)
}

private enum class BrowserPageState { Loading, Success, Error }

@Composable
private fun BrowserPageOverlay(
    state: BrowserPageState,
    errorDetail: String?,
    isSlowLoading: Boolean,
    onBack: () -> Unit,
    onOpenExternally: () -> Unit,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center) {
        if (state == BrowserPageState.Loading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp)
                Text(stringResource(R.string.browser_loading), color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isSlowLoading) stringResource(R.string.browser_slow_loading) else stringResource(R.string.browser_waiting_first_paint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.CloudOff, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
                Text(
                    if (errorDetail?.contains("30 秒") == true || errorDetail?.contains("30 seconds") == true) stringResource(R.string.browser_load_timeout) else stringResource(R.string.browser_cannot_open),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(errorDetail ?: stringResource(R.string.browser_error_detail_fallback),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
                AppButton(stringResource(R.string.browser_reload), onClick = onRetry)
                AppButton(stringResource(R.string.browser_open_external_browser), onClick = onOpenExternally, style = AppButtonStyle.SECONDARY)
                AppButton(stringResource(R.string.browser_back), onClick = onBack, style = AppButtonStyle.TEXT)
            }
        }
    }
}

private fun currentDappAuthorizationStamp(): String {
    val generation = WalletConnectSessionDelegate.sessionGeneration.value
    val addresses = WalletConnectSessionDelegate.accounts.value
        .map { it.address.lowercase() }
        .sorted()
    return generation.toString() + ":" + addresses.joinToString(",")
}
