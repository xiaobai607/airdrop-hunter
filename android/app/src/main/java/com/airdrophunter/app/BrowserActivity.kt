package com.airdrophunter.app

import androidx.compose.ui.res.stringResource
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
import android.os.Build
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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.UserAgentMetadata
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
import com.google.accompanist.navigation.material.ModalBottomSheetLayout
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.client.models.request.Request as ExternalWalletRequest
import com.reown.appkit.client.models.request.SentRequestResult
import com.reown.appkit.ui.appKitGraph
import com.reown.appkit.ui.openAppKit
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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

private fun normalizeBrowserHttpsUrl(value: String): String? {
    return try {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val parsed = Uri.parse(withScheme)
        val normalized = when {
            parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true) -> parsed
            else -> return null
        }
        if (normalized.host.isNullOrBlank() || normalized.userInfo != null) null else normalized.toString()
    } catch (_: Exception) { null }
}

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
    val replyProxy: DappReplyChannel,
    val sessionTopic: String,
    val chainId: String,
    val walletConnectRequestId: Long? = null,
    val web3DraftId: String? = null
)

class BrowserActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

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
            Toast.makeText(this, getString(R.string.loc_browser_01), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!intent.getBooleanExtra(EXTRA_FORCE_SYSTEM_WEBVIEW, false)) {
            // Ordinary browsing prefers Tencent X5 and automatically falls back to Android
            // System WebView if the vendor kernel is unavailable. The legacy wallet bridge
            // remains on its separate, explicitly selected system-WebView route.
            startActivity(
                Intent(this, X5BrowserActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, site)
            )
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
            val normalized = when {
                parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true) -> parsed
                else -> return null
            }
            if (normalized.host.isNullOrBlank() || normalized.userInfo != null) null else normalized.toString()
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
        const val EXTRA_FORCE_SYSTEM_WEBVIEW = "com.airdrophunter.app.extra.BROWSER_FORCE_SYSTEM_WEBVIEW"
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
    val bridgeReplyProxies = remember { mutableMapOf<String, DappReplyChannel>() }
    val popupWebViews = remember { mutableStateListOf<WebView>() }
    var selectedWallet by remember(wallets, selectedWalletId) {
        mutableStateOf(wallets.firstOrNull { it.id == selectedWalletId } ?: wallets.firstOrNull())
    }
    var showWalletPicker by remember { mutableStateOf(false) }
    var showManualAccounting by remember { mutableStateOf(false) }
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
    var addressInput by remember(url) { mutableStateOf(url) }
    var isAddressEditing by remember { mutableStateOf(false) }
    var pageStartCount by remember(url) { mutableIntStateOf(0) }
    var showSecurityBanner by remember(url) { mutableStateOf(shouldShowBrowserSecurityBanner(context)) }
    var approvedOrigin by remember(url) { mutableStateOf(DappProviderBridge.origin(Uri.parse(url))) }
    var pendingNavigationUrl by remember { mutableStateOf<String?>(null) }
    // Keep one WebView instance across Compose recompositions; recreate only after renderer recovery.
    val rememberedWebView = remember(context, webViewGeneration) { WebView(context) }
    LaunchedEffect(currentPageUrl, isAddressEditing) {
        // Do not overwrite text while the user is editing; page callbacks may arrive late.
        if (!isAddressEditing) addressInput = currentPageUrl
    }
    val navigateToAddress: () -> Unit = {
        val targetUrl = normalizeBrowserHttpsUrl(addressInput.trim())
        if (targetUrl == null) {
            Toast.makeText(context, context.getString(R.string.loc_browser_02), Toast.LENGTH_SHORT).show()
        } else {
            isAddressEditing = false
            addressInput = targetUrl
            pendingNavigationUrl = null
            pageState = BrowserPageState.Loading
            pageErrorMessage = null
            val activeWebView = browser
            if (activeWebView != null) {
                activeWebView.stopLoading()
                activeWebView.loadUrl(targetUrl)
                Log.i("AirdropBrowser", "User address navigation requested: " + displayHost(targetUrl))
            } else {
                Toast.makeText(context, context.getString(R.string.loc_browser_03), Toast.LENGTH_SHORT).show()
            }
        }
    }
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
            kotlinx.coroutines.delay(30_000)
            if (pageState == BrowserPageState.Loading) {
                pageState = BrowserPageState.Error
                pageErrorMessage = context.getString(R.string.loc_browser_timeout_detail, displayHost(currentPageUrl))
                Log.w("AirdropBrowser", "Main-frame load timed out after 30 seconds; host=${displayHost(currentPageUrl)}")
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
            DappProviderBridge.error(pending.replyProxy, requestId, 4001, context.getString(R.string.loc_browser_network_error_detail, context.getString(R.string.loc_browser_21), requestId, context.getString(R.string.loc_browser_22), "WalletConnect", currentPageUrl))
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
                    DappProviderBridge.error(pending.replyProxy, pending.request.id, 4100, context.getString(R.string.loc_browser_network_error_detail, context.getString(R.string.loc_browser_23), pending.request.id, context.getString(R.string.loc_browser_24), "WalletConnect", currentPageUrl))
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

    ModalBottomSheetLayout(bottomSheetNavigator) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Edge-to-edge is enabled by the Activity; keep the entire browser header
            // below the status bar so the security notice text cannot be clipped.
            Column(Modifier.statusBarsPadding()) {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(56.dp),
                    title = { Text(title, color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge, maxLines = 1) },
                    navigationIcon = {
                        AppIconButton(Icons.Default.ArrowBack, context.getString(R.string.loc_browser_46), onExit)
                    },
                    actions = {
                        AppButton(
                            text = if (walletConnectReady) context.getString(R.string.loc_browser_05) else context.getString(R.string.loc_browser_06),
                            onClick = {
                                if (walletConnectReady) {
                                    walletNavController.openAppKit()
                                } else {
                                    WalletConnectInitializer.ensureInitialized(context.applicationContext as android.app.Application)
                                    Toast.makeText(context, context.getString(R.string.loc_browser_07), Toast.LENGTH_SHORT).show()
                                }
                            },
                            style = AppButtonStyle.SECONDARY,
                            enabled = true,
                            fullWidth = false,
                            heightDp = 44,
                            fontSizeSp = 14
                        )
                        if (selectedWallet != null) {
                            AppButton(
                                text = context.getString(R.string.loc_browser_wallet_title, selectedWallet!!.walletName.ifBlank { selectedWallet!!.chain }),
                                onClick = { showWalletPicker = true },
                                modifier = Modifier.widthIn(max = 124.dp),
                                style = AppButtonStyle.TEXT,
                                fullWidth = false,
                                heightDp = 44,
                                fontSizeSp = 14
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            modifier = Modifier.weight(1f).onFocusChanged { focusState ->
                                isAddressEditing = focusState.isFocused
                            },
                            singleLine = true,
                            placeholder = { Text(context.getString(R.string.loc_browser_08)) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(onGo = { navigateToAddress() })
                        )
                        IconButton(onClick = navigateToAddress) {
                            Icon(Icons.Default.ArrowForward, contentDescription = context.getString(R.string.loc_browser_09),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
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
                                        context.getString(R.string.loc_browser_switch_site_notice, targetHost)
                                    else
                                        context.getString(R.string.loc_browser_security_notice),
                                    Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2
                                )
                            }
                            if (targetUrl != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppTextButton(onClick = {
                                        pendingNavigationUrl = null
                                        browser?.loadUrl(targetUrl)
                                    }) { Text(context.getString(R.string.loc_browser_10)) }
                                    AppTextButton(onClick = { pendingNavigationUrl = null }) {
                                        Text(context.getString(R.string.loc_browser_11))
                                    }
                                }
                            } else {
                                AppTextButton(onClick = {
                                    markBrowserSecurityBannerShown(context)
                                    showSecurityBanner = false
                                }) {
                                    Text(context.getString(R.string.loc_browser_12))
                                }
                            }
                        }
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
                    rememberedWebView.apply {
                        browser = this
                        val supportsSecureBridge =
                            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
                                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                        if (supportsSecureBridge) {
                            WebViewCompat.addWebMessageListener(
                                this,
                                DappProviderBridge.JS_OBJECT_NAME,
                                DappProviderBridge.HTTPS_ORIGIN_RULES
                            ) { sourceView, message, sourceOrigin, isMainFrame, nativeReplyProxy ->
                                val replyChannel = DappReplyChannel { response -> nativeReplyProxy.postMessage(response) }
                                val request = DappProviderBridge.parseRequest(message.data.orEmpty())
                                if (request == null) {
                                    Log.w("AirdropBrowser", "Ignored malformed DApp provider message")
                                } else if (!isMainFrame || !DappProviderBridge.isTrustedMainFrame(sourceOrigin, sourceView.url)) {
                                    DappProviderBridge.error(replyChannel, request.id, -32000, "Wallet bridge is only available to the trusted top-level HTTPS page")
                                } else {
                                    val origin = DappProviderBridge.origin(sourceOrigin)
                                    bridgeReplyProxies[origin] = replyChannel
                                    when (request.method) {
                                        "eth_requestAccounts" -> {
                                            if (pendingDappConnection != null) {
                                                DappProviderBridge.error(replyChannel, request.id, -32002, "A wallet connection request is already pending")
                                            } else if (authorizedDappOrigins[origin] == currentDappAuthorizationStamp() && connectedDappAccounts.isNotEmpty()) {
                                                DappProviderBridge.success(
                                                    replyChannel,
                                                    request.id,
                                                    JSONArray(connectedDappAccounts.map { it.address }.distinct())
                                                )
                                            } else {
                                                pendingDappConnection = PendingDappConnection(request, origin, replyChannel)
                                            }
                                        }
                                        "eth_accounts" -> {
                                            val addresses = if (authorizedDappOrigins[origin] == currentDappAuthorizationStamp()) {
                                                connectedDappAccounts.map { it.address }.distinct()
                                            } else emptyList()
                                            DappProviderBridge.success(replyChannel, request.id, JSONArray(addresses))
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
                                                    replyChannel, request.id, -32000,
                                                    "The active EVM chain is unavailable; connect a wallet or select a supported network"
                                                )
                                            } else {
                                                val result = if (request.method == "eth_chainId") {
                                                    "0x" + chainNumber.toString(16)
                                                } else chainNumber.toString()
                                                DappProviderBridge.success(replyChannel, request.id, result)
                                            }
                                        }
                                        "wallet_switchEthereumChain" -> DappProviderBridge.error(
                                            replyChannel, request.id, 4200, "Switch networks in the connected wallet, then reconnect this site"
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
                                                            DappProviderBridge.error(replyChannel, request.id, 4100, context.getString(R.string.loc_browser_25))
                                                        sessionTopic.isNullOrBlank() ->
                                                            DappProviderBridge.error(replyChannel, request.id, 4200, context.getString(R.string.loc_browser_26))
                                                        pendingDappExternalRequest != null ->
                                                            DappProviderBridge.error(replyChannel, request.id, -32002, context.getString(R.string.loc_browser_27))
                                                        !DappProviderBridge.isAuthorizedAccountRequest(
                                                            request.method, firstRequestParam, secondRequestParam,
                                                            transactionFrom, connectedAddresses
                                                        ) ->
                                                            DappProviderBridge.error(replyChannel, request.id, 4100, context.getString(R.string.loc_browser_28))
                                                        account == null ->
                                                            DappProviderBridge.error(replyChannel, request.id, 4901, context.getString(R.string.loc_browser_29))
                                                        !DappProviderBridge.isTransactionChainCompatible(
                                                            request.method,
                                                            request.params.optJSONObject(0)?.optString("chainId"),
                                                            account.chainId
                                                        ) ->
                                                            DappProviderBridge.error(replyChannel, request.id, 4901, context.getString(R.string.loc_browser_30))
                                                        else -> {
                                                            val chainReference = account.chainId.let {
                                                                if (it.startsWith("eip155:")) it else "eip155:$it"
                                                            }
                                                            val pending = PendingDappExternalRequest(
                                                                request = request,
                                                                origin = origin,
                                                                replyProxy = replyChannel,
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
                                                                        DappProviderBridge.error(replyChannel, request.id, -32602, context.getString(R.string.loc_browser_31))
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
                                                                        DappProviderBridge.error(replyChannel, request.id, -32000, context.getString(R.string.loc_browser_32))
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
                                                                                            replyChannel, request.id, 4200,
                                                                                            context.getString(R.string.loc_browser_22)
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
                                                                                                replyChannel, request.id, 4200,
                                                                                                context.getString(R.string.loc_browser_23)
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
                                                                                        replyChannel, request.id, 4001,
                                                                                        error.localizedMessage ?: context.getString(R.string.loc_browser_21)
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
                                                                            replyChannel, request.id, 4001,
                                                                            error.localizedMessage ?: context.getString(R.string.loc_browser_20)
                                                                        )
                                                                        pendingDappExternalRequest = null
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                DappProviderBridge.isBlockedRawSubmissionMethod(request.method) ->
                                                    DappProviderBridge.error(replyChannel, request.id, 4200, context.getString(R.string.loc_browser_33))
                                                else ->
                                                    DappProviderBridge.error(replyChannel, request.id, 4200, "Unsupported wallet method: ${request.method}")
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
                            // Use the WebView provider default User-Agent and Client Hints.
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
                                    message.messageLevel() == ConsoleMessage.MessageLevel.WARNING                                ) {
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
                                    // Popup WebView keeps its provider default User-Agent and Client Hints.
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
                        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url ?: run {
                                    Log.e("AirdropBrowser", "Navigation callback had no request URL; blocking")
                                    return true
                                }
                                val scheme = target.scheme?.lowercase()
                                Log.d("AirdropBrowser", "Navigation request: url=$target, scheme=$scheme, mainFrame=${request.isForMainFrame}, gesture=${request.hasGesture()}")
                                if (scheme in setOf("metamask", "trust", "trustwallet", "ethereum", "wc")) {
                                    if (request?.hasGesture() != true) {
                                        Log.w("AirdropBrowser", "Blocked wallet deep link without a user gesture")
                                        return true
                                    }
                                    if (scheme == "wc") {
                                        if (!walletConnectReady) {
                                            Toast.makeText(context, context.getString(R.string.loc_browser_13), Toast.LENGTH_LONG).show()
                                        } else {
                                            runCatching {
                                                AppKit.handleDeepLink(target.toString()) { error ->
                                                    Log.e("AirdropWallet", "WalletConnect deep link failed", error.throwable)
                                                }
                                            }.onFailure {
                                                Toast.makeText(context, context.getString(R.string.loc_browser_14), Toast.LENGTH_LONG).show()
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
                                            Toast.makeText(context, context.getString(R.string.loc_browser_15), Toast.LENGTH_LONG).show()
                                        }
                                        draftId?.let {
                                            com.airdrophunter.app.data.Web3DraftTimeoutScheduler.enqueue(
                                                context.applicationContext, it
                                            )
                                        }
                                        val walletIntent = Intent(Intent.ACTION_VIEW, target)
                                        if (walletIntent.resolveActivity(context.packageManager) == null) {
                                            draftId?.let { web3Repository.markWalletUnavailable(it) }
                                            Toast.makeText(context, context.getString(R.string.loc_browser_16), Toast.LENGTH_LONG).show()
                                        } else {
                                            runCatching {
                                                context.startActivity(Intent.createChooser(walletIntent, context.getString(R.string.loc_browser_17)))
                                            }.onFailure {
                                                draftId?.let { id -> web3Repository.markWalletTimedOut(id) }
                                                Toast.makeText(context, context.getString(R.string.loc_browser_18), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    return true
                                }
                                // Ordinary web navigation belongs to WebView. Do not rewrite HTTP
                                // to HTTPS or intercept normal HTTP(S) links here.
                                if (scheme == "http" || scheme == "https") return false
                                Log.w("AirdropBrowser", "Blocked unsupported navigation scheme: $scheme, url=$target")
                                return true
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
                                    // Track the actual committed navigation origin, not the
                                    // address the user merely typed. This keeps popup checks
                                    // aligned with the page currently displayed.
                                    if (currentOrigin != null) approvedOrigin = currentOrigin
                                    if (isNewDocument) {
                                        pendingDappConnection?.let { pending ->
                                            DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "The page navigated before wallet access was approved")
                                        }
                                        pendingDappExternalRequest?.let { pending ->
                                            pending.web3DraftId?.let { draftId ->
                                                dappRequestScope.launch { web3Repository.markWalletTimedOut(draftId) }
                                            }
                                            DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, context.getString(R.string.loc_browser_19))
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

                            override fun onPageCommitVisible(view: WebView?, pageUrl: String?) {
                                // Reveal the first committed document as soon as WebView can draw it.
                                // Do not wait for every long-running script, analytics request, or stream to finish.
                                pageUrl?.let { currentPageUrl = it }
                                if (pageState != BrowserPageState.Error) {
                                    pageState = BrowserPageState.Success
                                    pageErrorMessage = null
                                }
                                isSlowLoading = false
                                Log.d("AirdropBrowser", "First page content committed: ${displayHost(pageUrl.orEmpty())}")
                            }

                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                pageUrl?.let { currentPageUrl = it }
                                if (pageState != BrowserPageState.Error) {
                                    pageState = BrowserPageState.Success
                                    pageErrorMessage = null
                                }
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
                                    val code = error?.errorCode
                                    val errorName = when (code) {
                                        WebViewClient.ERROR_HOST_LOOKUP -> context.getString(R.string.loc_browser_net_dns)
                                        WebViewClient.ERROR_CONNECT -> context.getString(R.string.loc_browser_net_connect)
                                        WebViewClient.ERROR_TIMEOUT -> context.getString(R.string.loc_browser_net_timeout)
                                        WebViewClient.ERROR_IO -> context.getString(R.string.loc_browser_net_io)
                                        WebViewClient.ERROR_UNSUPPORTED_SCHEME -> context.getString(R.string.loc_browser_net_scheme)
                                        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> context.getString(R.string.loc_browser_net_ssl_handshake)
                                        WebViewClient.ERROR_TOO_MANY_REQUESTS -> context.getString(R.string.loc_browser_net_rate_limit)
                                        WebViewClient.ERROR_UNKNOWN -> context.getString(R.string.loc_browser_net_unknown)
                                        else -> context.getString(R.string.loc_browser_net_webview)
                                    }
                                    pageErrorMessage = context.getString(R.string.loc_browser_network_error_detail, errorName, code ?: context.getString(R.string.loc_browser_unknown), error?.description ?: context.getString(R.string.loc_browser_system_not_provided), request?.method ?: context.getString(R.string.loc_browser_unknown), request?.url ?: view?.url ?: currentPageUrl)
                                    Log.e("AirdropBrowser", "Main-frame load error: code=$code, name=$errorName, description=${error?.description}, url=${request?.url}, method=${request?.method}")
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
                                    pageErrorMessage = context.getString(R.string.loc_browser_http_error_detail, errorResponse?.statusCode ?: context.getString(R.string.loc_browser_unknown), errorResponse?.reasonPhrase ?: context.getString(R.string.loc_browser_system_not_provided), request?.method ?: context.getString(R.string.loc_browser_unknown), request?.url ?: view?.url ?: currentPageUrl)
                                    Log.e("AirdropBrowser", "Main-frame HTTP error: status=${errorResponse?.statusCode}, reason=${errorResponse?.reasonPhrase}, url=${request?.url}, method=${request?.method}")
                                }
                            }

                            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                // Never allow a WebView page to bypass TLS certificate validation.
                                handler?.cancel()
                                pageState = BrowserPageState.Error
                                pageErrorMessage = context.getString(R.string.loc_browser_ssl_error_detail, error?.primaryError ?: context.getString(R.string.loc_browser_unknown), when (error?.primaryError) {
                                    android.net.http.SslError.SSL_NOTYETVALID -> context.getString(R.string.loc_browser_ssl_not_yet_valid)
                                    android.net.http.SslError.SSL_EXPIRED -> context.getString(R.string.loc_browser_ssl_expired)
                                    android.net.http.SslError.SSL_IDMISMATCH -> context.getString(R.string.loc_browser_ssl_domain_mismatch)
                                    android.net.http.SslError.SSL_UNTRUSTED -> context.getString(R.string.loc_browser_ssl_untrusted)
                                    android.net.http.SslError.SSL_DATE_INVALID -> context.getString(R.string.loc_browser_ssl_date_invalid)
                                    android.net.http.SslError.SSL_INVALID -> context.getString(R.string.loc_browser_ssl_invalid)
                                    else -> context.getString(R.string.loc_browser_ssl_unknown)
                                }, error?.url ?: currentPageUrl)
                                Log.e("AirdropBrowser", "Blocked page because TLS certificate validation failed; primaryError=${error?.primaryError}, url=${error?.url}")
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
                                            context.getString(R.string.loc_browser_24)
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
                                            context.getString(R.string.loc_browser_25)
                                        )
                                    }
                                }
                                pendingDappConnection = null
                                pendingDappExternalRequest = null
                                waitingDappWalletApproval = false
                                DappOriginPermissionStore.revokeAll()
                                browser = null
                                pageState = BrowserPageState.Error
                                pageErrorMessage = context.getString(R.string.loc_browser_renderer_error_detail, detail?.didCrash()?.toString() ?: context.getString(R.string.loc_browser_unknown), detail?.rendererPriorityAtExit()?.toString() ?: context.getString(R.string.loc_browser_unknown), displayHost(currentPageUrl))
                                webViewGeneration += 1
                                return true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().background(Color.White),
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
                            Toast.makeText(context, context.getString(R.string.loc_browser_26), Toast.LENGTH_SHORT).show()
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
                    if (address.isBlank()) Toast.makeText(context, context.getString(R.string.loc_browser_27), Toast.LENGTH_SHORT).show()
                    else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Wallet address", address))
                        Toast.makeText(context, context.getString(R.string.loc_browser_28), Toast.LENGTH_SHORT).show()
                    }
                },
                onRefreshPage = { browser?.reload() },
                onCopyPageUrl = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Page URL", currentPageUrl))
                    Toast.makeText(context, context.getString(R.string.loc_browser_29), Toast.LENGTH_SHORT).show()
                },
                onOpenPageExternally = {
                    val uri = Uri.parse(currentPageUrl)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                        Toast.makeText(context, context.getString(R.string.loc_browser_26), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
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
            title = { Text(context.getString(R.string.loc_browser_30), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.loc_browser_origin_label, requestedHost), color = Color(0xFFB42318), fontWeight = FontWeight.Bold)
                    Text(context.getString(R.string.loc_browser_31), color = MaterialTheme.colorScheme.onSurface)
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
                }) { Text(if (connectedDappAccounts.isEmpty() && !walletConnectReady) context.getString(R.string.loc_browser_32) else context.getString(R.string.loc_browser_33), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "User rejected wallet connection")
                    pendingDappConnection = null
                }) { Text(context.getString(R.string.loc_browser_34)) }
            }
        )
    }

    NavHost(walletNavController, startDestination = "browser_wallet_host") {
        composable("browser_wallet_host") { }
        appKitGraph(walletNavController)
    }
    }
    }

    if (showManualAccounting) {
        Dialog(onDismissRequest = { showManualAccounting = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f), shape = RoundedCornerShape(AppDesignTokens.Radius.Xl.dp), color = MaterialTheme.colorScheme.background) {
                ManualAccountingScreen(modifier = Modifier.fillMaxSize(), onBack = { showManualAccounting = false })
            }
        }
    }

    if (showWalletPicker) {
        Dialog(onDismissRequest = { showWalletPicker = false }) {
            Surface(shape = RoundedCornerShape(AppDesignTokens.Radius.Xl.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(context.getString(R.string.loc_browser_35), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (wallets.isEmpty()) Text(context.getString(R.string.loc_browser_36), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    wallets.forEach { wallet ->
                        AppTextButton(onClick = { selectedWallet = wallet; showWalletPicker = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("${wallet.walletName.ifBlank { wallet.chain }}  ·  ${shortAddress(wallet.address)}")
                        }
                    }
                    AppTextButton(onClick = { showWalletPicker = false }, modifier = Modifier.align(Alignment.End)) { Text(context.getString(R.string.loc_browser_37)) }
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
        Uri.parse(rawUrl).host.orEmpty().lowercase().ifBlank { "unknown host" }
    }.getOrDefault("unknown host")
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
                Text(stringResource(R.string.loc_browser_38), color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isSlowLoading) stringResource(R.string.loc_browser_39) else stringResource(R.string.loc_browser_40),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.CloudOff, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
                Text(
                    if ((errorDetail?.contains("超时") == true || errorDetail?.contains("timeout", ignoreCase = true) == true)) stringResource(R.string.loc_browser_41) else stringResource(R.string.loc_browser_42),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp)
                ) {
                    Text(
                        errorDetail ?: stringResource(R.string.loc_browser_43),
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                AppButton(stringResource(R.string.loc_browser_44), onClick = onRetry)
                AppButton(stringResource(R.string.loc_browser_45), onClick = onOpenExternally, style = AppButtonStyle.SECONDARY)
                AppButton(stringResource(R.string.loc_browser_46), onClick = onBack, style = AppButtonStyle.TEXT)
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