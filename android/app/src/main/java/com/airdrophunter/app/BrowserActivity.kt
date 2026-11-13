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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.ui.theme.AirdropHunterTheme
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.components.AppTextButton
import kotlinx.coroutines.launch
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
        Log.d(TAG, "BrowserActivity received URL: $receivedUrl")
        val url = normalizeHttpsUrl(receivedUrl)
        val site = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (url == null) {
            Log.e(TAG, "Rejected invalid browser URL: $receivedUrl")
            Toast.makeText(this, "网址无效，请检查链接后重试", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val wallets = parseWallets(intent.getStringExtra(EXTRA_WALLETS_JSON).orEmpty())
        val selectedId = intent.getStringExtra(EXTRA_SELECTED_WALLET_ID).orEmpty()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.rgb(248, 249, 250)
        window.navigationBarColor = AndroidColor.rgb(248, 249, 250)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            AirdropHunterTheme {
                BrowserScreen(site.ifBlank { Uri.parse(url).host.orEmpty() }, url, wallets, selectedId, intent.getStringExtra(EXTRA_QUICK_COPY_ADDRESS).orEmpty()) { finish() }
            }
        }
    }

    private fun normalizeHttpsUrl(value: String): String? {
        return try {
            val trimmed = value.trim()
            if (trimmed.isBlank()) null
            else {
                val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
                val parsed = Uri.parse(withScheme)
                if (!parsed.scheme.equals("https", true) || parsed.host.isNullOrBlank() || parsed.userInfo != null) null
                else parsed.toString()
            }
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
            kotlinx.coroutines.delay(15_000)
            if (pageState == BrowserPageState.Loading) {
                pageState = BrowserPageState.Error
                pageErrorMessage = "网页在 15 秒内未返回首屏内容。可能是网络连接、网站响应或登录跳转失败；请检查网络后重试。"
                Log.w("AirdropBrowser", "Main-frame load timed out after 15 seconds")
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
            DappProviderBridge.error(pending.replyProxy, requestId, 4001, "外部钱包请求超时或已取消；链上状态仍需同步核对")
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
                    DappProviderBridge.error(pending.replyProxy, pending.request.id, 4100, "外部钱包会话已变化；交易草稿保留并等待同步核对")
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
                        AppIconButton(Icons.Default.ArrowBack, "返回", onExit)
                    },
                    actions = {
                        AppButton(
                            text = "连接外部钱包",
                            onClick = { walletNavController.openAppKit() },
                            style = AppButtonStyle.SECONDARY,
                            enabled = walletConnectReady,
                            fullWidth = false,
                            heightDp = 44,
                            fontSizeSp = 13
                        )
                        if (selectedWallet != null) {
                            AppButton(
                                text = "钱包 · ${selectedWallet!!.walletName.ifBlank { selectedWallet!!.chain }}",
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
                        Modifier.fillMaxWidth(),
                        color = if (androidx.compose.foundation.isSystemInDarkTheme())
                            AppDesignTokens.Dark.WarningSoft else AppDesignTokens.Light.WarningSoft
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                        "即将切换网站：$targetHost。请核对域名，不要输入助记词或私钥。"
                                    else
                                        "安全提醒：本 App 不保存私钥。签名和交易由外部钱包确认；请核对网站域名。",
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
                                    }) { Text("确认访问") }
                                    AppTextButton(onClick = { pendingNavigationUrl = null }) {
                                        Text("留在当前网站")
                                    }
                                }
                            } else {
                                AppTextButton(onClick = {
                                    markBrowserSecurityBannerShown(context)
                                    showSecurityBanner = false
                                }) {
                                    Text("知道了")
                                }
                            }
                        }
                    }
                }
                Surface(Modifier.fillMaxWidth().height(40.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("当前主机", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        Text(displayHost(currentPageUrl), color = MaterialTheme.colorScheme.onSurface,
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
                                                            DappProviderBridge.error(replyProxy, request.id, 4100, "请先在当前网站授权连接外部钱包")
                                                        sessionTopic.isNullOrBlank() ->
                                                            DappProviderBridge.error(replyProxy, request.id, 4200, "当前钱包连接方式暂不支持 DApp 签名转发，请使用 WalletConnect 外部钱包连接")
                                                        pendingDappExternalRequest != null ->
                                                            DappProviderBridge.error(replyProxy, request.id, -32002, "已有一笔外部钱包请求正在等待确认")
                                                        !DappProviderBridge.isAuthorizedAccountRequest(
                                                            request.method, firstRequestParam, secondRequestParam,
                                                            transactionFrom, connectedAddresses
                                                        ) ->
                                                            DappProviderBridge.error(replyProxy, request.id, 4100, "请求使用的钱包地址未在当前外部钱包会话中授权")
                                                        account == null ->
                                                            DappProviderBridge.error(replyProxy, request.id, 4901, "请求账户未在当前外部钱包的活动网络中授权，请切换网络或重新连接")
                                                        !DappProviderBridge.isTransactionChainCompatible(
                                                            request.method,
                                                            request.params.optJSONObject(0)?.optString("chainId"),
                                                            account.chainId
                                                        ) ->
                                                            DappProviderBridge.error(replyProxy, request.id, 4901, "交易参数指定的网络与当前授权网络不一致，已阻止转发")
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
                                                                        DappProviderBridge.error(replyProxy, request.id, -32602, "交易参数或链 ID 无效")
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
                                                                        DappProviderBridge.error(replyProxy, request.id, -32000, "无法安全保存交易草稿，本次交易请求已阻止")
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
                                                                                            "无法确认外部钱包请求编号，已安全取消本次请求"
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
                                                                                                "交易草稿与钱包请求关联失败；请通过链上同步核对"
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
                                                                                        error.localizedMessage ?: "外部钱包请求失败或被拒绝"
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
                                                                            error.localizedMessage ?: "无法打开外部钱包请求"
                                                                        )
                                                                        pendingDappExternalRequest = null
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                DappProviderBridge.isBlockedRawSubmissionMethod(request.method) ->
                                                    DappProviderBridge.error(replyProxy, request.id, 4200, "不支持原始已签名交易或批量智能账户提交；请使用由外部钱包审核的标准签名/交易请求")
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
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadsImagesAutomatically = true
                            userAgentString = CHROME_MOBILE_USER_AGENT
                            allowFileAccess = false
                            allowContentAccess = false
                            allowUniversalAccessFromFileURLs = false
                            allowFileAccessFromFileURLs = false
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            safeBrowsingEnabled = true
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        // Keep third-party cookies disabled by default; do not weaken cross-site privacy globally.
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webChromeClient = object : WebChromeClient() {
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
                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean = false
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
                                            Toast.makeText(context, "钱包连接服务尚未就绪，请稍后重试", Toast.LENGTH_LONG).show()
                                        } else {
                                            runCatching {
                                                AppKit.handleDeepLink(target.toString()) { error ->
                                                    Log.e("AirdropWallet", "WalletConnect deep link failed", error.throwable)
                                                }
                                            }.onFailure {
                                                Toast.makeText(context, "无法处理 WalletConnect 链接", Toast.LENGTH_LONG).show()
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
                                            Toast.makeText(context, "该网络暂未接入安全 RPC 自动核验，钱包仍可打开；请使用手动记账", Toast.LENGTH_LONG).show()
                                        }
                                        draftId?.let {
                                            com.airdrophunter.app.data.Web3DraftTimeoutScheduler.enqueue(
                                                context.applicationContext, it
                                            )
                                        }
                                        val walletIntent = Intent(Intent.ACTION_VIEW, target)
                                        if (walletIntent.resolveActivity(context.packageManager) == null) {
                                            draftId?.let { web3Repository.markWalletUnavailable(it) }
                                            Toast.makeText(context, "未找到可处理此钱包链接的应用", Toast.LENGTH_LONG).show()
                                        } else {
                                            runCatching {
                                                context.startActivity(Intent.createChooser(walletIntent, "选择外部钱包"))
                                            }.onFailure {
                                                draftId?.let { id -> web3Repository.markWalletTimedOut(id) }
                                                Toast.makeText(context, "无法打开外部钱包；交易草稿已保留待核对", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    return true
                                }
                                if (!target.scheme.equals("https", true) || target.host.isNullOrBlank() || target.userInfo != null) return true
                                val targetOrigin = runCatching { DappProviderBridge.origin(target) }.getOrNull() ?: return true
                                if (targetOrigin != approvedOrigin) {
                                    pendingNavigationUrl = target.toString()
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
                                            DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "网页已跳转；交易草稿保留并等待链上同步核对")
                                        }
                                        pendingDappConnection = null
                                        pendingDappExternalRequest = null
                                        waitingDappWalletApproval = false
                                    }
                                    currentPageUrl = it
                                }
                                pageState = BrowserPageState.Loading
                                pageErrorMessage = null
                                Log.d("AirdropBrowser", "Page started: $pageUrl")
                            }

                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                pageUrl?.let { currentPageUrl = it }
                                pageState = BrowserPageState.Success
                                pageErrorMessage = null
                                view?.settings?.userAgentString = CHROME_MOBILE_USER_AGENT
                                Log.d("AirdropBrowser", "Page finished: $pageUrl")
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    pageState = BrowserPageState.Error
                                    pageErrorMessage = "网络加载错误（${error?.errorCode ?: "未知"}）：${error?.description ?: "未提供错误详情"}"
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
                                    pageErrorMessage = "网站返回 HTTP ${errorResponse?.statusCode ?: "错误"}：${errorResponse?.reasonPhrase ?: "服务器拒绝或无法处理请求"}"
                                    Log.e("AirdropBrowser", "Main-frame HTTP error: ${errorResponse?.statusCode}")
                                }
                            }

                            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                // Never allow a WebView page to bypass TLS certificate validation.
                                handler?.cancel()
                                pageState = BrowserPageState.Error
                                pageErrorMessage = "HTTPS 证书验证失败，已阻止连接。请检查设备时间、网络代理或网站证书。"
                                Log.e("AirdropBrowser", "Blocked page because TLS certificate validation failed: $error")
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
                                            "网页渲染进程已退出，请重新加载页面"
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
                                            "网页渲染进程已退出；交易草稿保留并等待链上同步核对"
                                        )
                                    }
                                }
                                pendingDappConnection = null
                                pendingDappExternalRequest = null
                                waitingDappWalletApproval = false
                                DappOriginPermissionStore.revokeAll()
                                browser = null
                                pageState = BrowserPageState.Error
                                pageErrorMessage = "网页渲染进程意外退出，已安全重建浏览器页面。请点击重新加载。"
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
                    onBack = onExit,
                    onRetry = {
                        pageState = BrowserPageState.Loading
                        pageErrorMessage = null
                        browser?.settings?.userAgentString = CHROME_MOBILE_USER_AGENT
                        browser?.reload()
                    }
                )
            }
            BrowserAccountingToolbar(
                walletAddress = quickCopyAddress.ifBlank { selectedWallet?.address.orEmpty() },
                chainName = selectedWallet?.chain.orEmpty(),
                onCopyAddress = {
                    val address = quickCopyAddress.ifBlank { selectedWallet?.address.orEmpty() }
                    if (address.isBlank()) Toast.makeText(context, "当前没有可复制的钱包地址", Toast.LENGTH_SHORT).show()
                    else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Wallet address", address))
                        Toast.makeText(context, "钱包地址已复制", Toast.LENGTH_SHORT).show()
                    }
                },
                onRefreshPage = { browser?.reload() },
                onCopyPageUrl = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Page URL", currentPageUrl))
                    Toast.makeText(context, "网址已复制", Toast.LENGTH_SHORT).show()
                },
                onOpenPageExternally = {
                    val uri = Uri.parse(currentPageUrl)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                        Toast.makeText(context, "未找到可用的外部浏览器", Toast.LENGTH_SHORT).show()
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
            title = { Text("网站请求连接钱包", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("来源网站：$requestedHost", color = Color(0xFFB42318), fontWeight = FontWeight.Bold)
                    Text("该网站请求读取已连接钱包的公开地址。只有您确认后才会授权；本次不会签名消息、发送交易或读取助记词。", color = MaterialTheme.colorScheme.onSurface)
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
                }) { Text(if (connectedDappAccounts.isEmpty() && !walletConnectReady) "钱包连接服务初始化中…" else "确认并连接", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "User rejected wallet connection")
                    pendingDappConnection = null
                }) { Text("拒绝") }
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
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择钱包", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (wallets.isEmpty()) Text("尚未保存钱包地址", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    wallets.forEach { wallet ->
                        AppTextButton(onClick = { selectedWallet = wallet; showWalletPicker = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("${wallet.walletName.ifBlank { wallet.chain }}  ·  ${shortAddress(wallet.address)}")
                        }
                    }
                    AppTextButton(onClick = { showWalletPicker = false }, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
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

private fun displayHost(rawUrl: String): String {
    return runCatching {
        Uri.parse(rawUrl).host.orEmpty().lowercase().ifBlank { "未知主机" }
    }.getOrDefault("未知主机")
}

private enum class BrowserPageState { Loading, Success, Error }

private const val CHROME_MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

@Composable
private fun BrowserPageOverlay(
    state: BrowserPageState,
    errorDetail: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center) {
        if (state == BrowserPageState.Loading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp)
                Text("正在连接网页，请稍候…", color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium)
                Text("正在等待网页返回首屏内容", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.CloudOff, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
                Text("网页加载超时", color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge)
                Text(errorDetail ?: "网站未提供具体错误原因。请检查网络后重试。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
                AppButton("重新加载", onClick = onRetry)
                AppButton("返回", onClick = onBack, style = AppButtonStyle.TEXT)
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
