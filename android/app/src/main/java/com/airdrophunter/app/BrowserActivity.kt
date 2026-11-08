package com.airdrophunter.app

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
import androidx.webkit.WebViewCompat
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
import com.reown.appkit.ui.appKitGraph
import com.reown.appkit.ui.openAppKit
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.ui.theme.AirdropHunterTheme
import com.airdrophunter.app.ui.theme.AppColors
import org.json.JSONArray

private data class BrowserWallet(
    val id: String,
    val chain: String,
    val networkIcon: String,
    val walletName: String,
    val address: String
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
    var browser by remember { mutableStateOf<WebView?>(null) }
    var selectedWallet by remember(wallets, selectedWalletId) {
        mutableStateOf(wallets.firstOrNull { it.id == selectedWalletId } ?: wallets.firstOrNull())
    }
    var showWalletPicker by remember { mutableStateOf(false) }
    var pendingDappConnection by remember { mutableStateOf<PendingDappConnection?>(null) }
    var pendingWalletAction by remember { mutableStateOf<PendingDappWalletAction?>(null) }
    var waitingDappWalletApproval by remember { mutableStateOf(false) }
    val authorizedDappOrigins = remember { mutableStateMapOf<String, String>() }
    val connectedDappAccounts by WalletConnectSessionDelegate.accounts.collectAsState()
    val connectedSessionGeneration by WalletConnectSessionDelegate.sessionGeneration.collectAsState()
    val dappAuthorizationStamp = remember(connectedSessionGeneration, connectedDappAccounts) {
        connectedSessionGeneration.toString() + ":" +
            connectedDappAccounts.map { it.address.lowercase() }.sorted().joinToString(",")
    }
    var pageState by remember(url) { mutableStateOf(BrowserPageState.Loading) }
    var loadAttempt by remember(url) { mutableIntStateOf(0) }
    var currentPageUrl by remember(url) { mutableStateOf(url) }
    var approvedExternalEntry by remember(url) { mutableStateOf(false) }
    var approvedHost by remember(url) { mutableStateOf(Uri.parse(url).host.orEmpty().lowercase()) }
    var pendingNavigationUrl by remember { mutableStateOf<String?>(null) }
    val webAlpha by animateFloatAsState(targetValue = if (pageState == BrowserPageState.Success) 1f else 0f, animationSpec = tween(300), label = "webview-fade-in")
    BackHandler { val web = browser; if (web != null && web.canGoBack()) web.goBack() else onExit() }

    DisposableEffect(browser) {
        onDispose { browser?.apply { stopLoading(); loadUrl("about:blank"); removeAllViews(); destroy() } }
    }
    LaunchedEffect(approvedExternalEntry, browser) {
        if (approvedExternalEntry) browser?.loadUrl(url)
    }

    LaunchedEffect(pageState, loadAttempt, approvedExternalEntry) {
        if (approvedExternalEntry && pageState == BrowserPageState.Loading) {
            kotlinx.coroutines.delay(3_000)
            if (pageState == BrowserPageState.Loading) {
                val web = browser
                if (loadAttempt == 0 && web != null) {
                    loadAttempt = 1
                    web.settings.userAgentString = IOS_SAFARI_USER_AGENT
                    web.reload()
                } else if (loadAttempt >= 1) {
                    pageState = BrowserPageState.Error
                }
            }        }
    }

    val bottomSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden, skipHalfExpanded = true)
    val bottomSheetNavigator = remember(bottomSheetState) { BottomSheetNavigator(bottomSheetState) }
    val walletNavController = rememberNavController(bottomSheetNavigator)

    LaunchedEffect(waitingDappWalletApproval, connectedDappAccounts, connectedSessionGeneration) {
        if (waitingDappWalletApproval && connectedDappAccounts.isNotEmpty()) {
            val pending = pendingDappConnection
            if (pending != null) {
                authorizedDappOrigins[pending.origin] = dappAuthorizationStamp
                val addresses = JSONArray(connectedDappAccounts.map { it.address }.distinct())
                DappProviderBridge.success(pending.replyProxy, pending.request.id, addresses)
            }
            pendingDappConnection = null
            waitingDappWalletApproval = false
        }
    }

    LaunchedEffect(connectedSessionGeneration) {
        DappRpcCoordinator.cancelAllForSessionChange()
        pendingWalletAction?.let { action ->
            DappProviderBridge.error(
                action.replyProxy,
                action.request.id,
                4001,
                "Wallet account or session changed; please request approval again"
            )
            pendingWalletAction = null
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
        containerColor = Color.White,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.Default.ArrowBack, "返回", tint = AppColors.TextPrimary) } },
                    actions = {
                        TextButton(onClick = { walletNavController.openAppKit() }) { Text("连接外部钱包", color = AppColors.Primary) }
                        if (selectedWallet != null) TextButton(onClick = { showWalletPicker = true }) {
                            Text("钱包 · ${selectedWallet!!.walletName.ifBlank { selectedWallet!!.chain }}", color = AppColors.Primary, maxLines = 1)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
                )
                Surface(Modifier.fillMaxWidth(), color = Color(0xFF9F2525)) {
                    Text("⚠️ 安全提醒：本 App 不保存私钥。当前为第三方网页，签名将在外部钱包完成，请仔细核对！",
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Surface(Modifier.fillMaxWidth(), color = Color(0xFFFFF4E5)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("当前主机", color = Color(0xFF8A4B08), style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(displayHost(currentPageUrl), color = Color(0xFF7A271A),
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Color.White)) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
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
                                    when (request.method) {
                                        "eth_requestAccounts" -> {
                                            if (pendingDappConnection != null) {
                                                DappProviderBridge.error(replyProxy, request.id, -32002, "A wallet connection request is already pending")
                                            } else if (authorizedDappOrigins[origin] == dappAuthorizationStamp && connectedDappAccounts.isNotEmpty()) {
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
                                            val addresses = if (authorizedDappOrigins[origin] == dappAuthorizationStamp) {
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
                                        "personal_sign", "eth_signTypedData_v4", "eth_sendTransaction" -> {
                                            if (authorizedDappOrigins[origin] != dappAuthorizationStamp) {
                                                DappProviderBridge.error(replyProxy, request.id, 4100, "Connect this website to the wallet first")
                                            } else if (pendingWalletAction != null) {
                                                DappProviderBridge.error(replyProxy, request.id, -32002, "Another wallet confirmation is pending")
                                            } else {
                                                pendingWalletAction = PendingDappWalletAction(request, origin, replyProxy)
                                            }
                                        }
                                        "eth_sign" -> DappProviderBridge.error(
                                            replyProxy, request.id, 4200, "eth_sign is disabled; use a safer typed-data signing method"
                                        )
                                        "wallet_switchEthereumChain" -> DappProviderBridge.error(
                                            replyProxy, request.id, 4200, "Switch networks in the connected wallet, then reconnect this site"
                                        )
                                        else -> DappProviderBridge.error(
                                            replyProxy, request.id, 4200, "Unsupported wallet method: ${request.method}"
                                        )
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
                                if (!target.scheme.equals("https", true) || target.host.isNullOrBlank() || target.userInfo != null) return true
                                val targetHost = target.host.orEmpty().lowercase()
                                if (targetHost != approvedHost) {
                                    pendingNavigationUrl = target.toString()
                                    return true
                                }
                                return false
                            }
                            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                                pageUrl?.let {
                                    val previousOrigin = runCatching { DappProviderBridge.origin(Uri.parse(currentPageUrl)) }.getOrNull()
                                    val currentOrigin = runCatching { DappProviderBridge.origin(Uri.parse(it)) }.getOrNull()
                                    if (previousOrigin != null && currentOrigin != previousOrigin) {
                                        authorizedDappOrigins.clear()
                                        DappRpcCoordinator.cancelOrigin(previousOrigin)
                                    }
                                    currentPageUrl = it
                                    val pending = pendingDappConnection
                                    if (pending != null && currentOrigin != pending.origin) {
                                        DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "The page changed before wallet access was approved")
                                        pendingDappConnection = null
                                        waitingDappWalletApproval = false
                                    }
                                    val action = pendingWalletAction
                                    if (action != null && currentOrigin != action.origin) {
                                        DappProviderBridge.error(action.replyProxy, action.request.id, 4001, "The page changed before wallet approval")
                                        pendingWalletAction = null
                                        DappRpcCoordinator.cancelOrigin(action.origin)
                                    }
                                }
                                pageState = BrowserPageState.Loading
                                Log.d("AirdropBrowser", "Page started: $pageUrl")
                            }

                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                pageUrl?.let { currentPageUrl = it }
                                pageState = BrowserPageState.Success
                                loadAttempt = 0
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
                                    Log.e("AirdropBrowser", "Main-frame HTTP error: ${errorResponse?.statusCode}")
                                }
                            }

                            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                // Never allow a WebView page to bypass TLS certificate validation.
                                handler?.cancel()
                                Log.e("AirdropBrowser", "Blocked page because TLS certificate validation failed: $error")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().background(Color.White).alpha(webAlpha),
                update = { view -> if (browser !== view) browser = view }
            )
            if (pageState != BrowserPageState.Success) {
                BrowserPageOverlay(
                    state = pageState,
                    onBack = onExit,
                    onRetry = {
                        loadAttempt = 0
                        pageState = BrowserPageState.Loading
                        browser?.settings?.userAgentString = CHROME_MOBILE_USER_AGENT
                        browser?.reload()
                    },
                    onOpenExternal = {
                        val uri = Uri.parse(currentPageUrl)
                        val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.android.chrome") }
                        try {
                            context.startActivity(chromeIntent)
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } catch (_: Exception) {
                                Toast.makeText(context, "未找到可用的外部浏览器", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onCopyUrl = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Page URL", currentPageUrl))
                        Toast.makeText(context, "网址已复制", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            }
            BrowserAccountingToolbar(
                walletAddress = quickCopyAddress.ifBlank { selectedWallet?.address.orEmpty() },
                onCopyAddress = {
                    val address = quickCopyAddress.ifBlank { selectedWallet?.address.orEmpty() }
                    if (address.isBlank()) Toast.makeText(context, "当前没有可复制的钱包地址", Toast.LENGTH_SHORT).show()
                    else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Wallet address", address))
                        Toast.makeText(context, "钱包地址已复制", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding().padding(bottom = 8.dp)
            )
        }
    }

    if (!approvedExternalEntry) {
        Dialog(onDismissRequest = onExit, properties = DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false
        )) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("⚠️ 第三方网站安全提醒", color = Color(0xFFB42318),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("您即将离开 Airdrop Hunter，进入第三方网站。本 App 无法控制该网站安全，请核对域名，切勿泄露私钥。",
                        color = AppColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("目标域名：${displayHost(url)}", color = Color(0xFFB42318),
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { approvedExternalEntry = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))) {
                        Text("已了解，继续", color = Color.White)
                    }
                    TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("取消并返回") }
                }
            }
        }
    }

    pendingNavigationUrl?.let { targetUrl ->
        val targetHost = displayHost(targetUrl)
        AlertDialog(
            onDismissRequest = { pendingNavigationUrl = null },
            title = { Text("⚠️ 即将切换第三方网站", color = Color(0xFFB42318), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前网页：${displayHost(currentPageUrl)}", color = AppColors.TextSecondary)
                    Text("即将访问：$targetHost", color = Color(0xFFB42318), fontWeight = FontWeight.Bold)
                    Text("这是不同的网站主机。请确认域名完全正确；不要输入助记词、私钥或验证码。", color = AppColors.TextPrimary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    approvedHost = Uri.parse(targetUrl).host.orEmpty().lowercase()
                    pendingNavigationUrl = null
                    browser?.loadUrl(targetUrl)
                }) { Text("确认访问", color = Color(0xFFB42318)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingNavigationUrl = null }) { Text("留在当前网站") }
            }
        )
    }

    pendingWalletAction?.let { pending ->
        val host = runCatching { Uri.parse(pending.origin).host.orEmpty() }.getOrDefault("unknown site")
        val paramsText = pending.request.params.toString()
        AlertDialog(
            onDismissRequest = {
                DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "User rejected wallet request")
                pendingWalletAction = null
            },
            title = { Text("确认钱包操作", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("来源网站：$host", color = Color(0xFFB42318), fontWeight = FontWeight.Bold)
                    Text("操作类型：${pending.request.method}", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("请求参数：$paramsText", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("确认后将交由外部钱包再次核对并完成最终签名/交易确认。请勿确认不认识的网站或不理解的请求。", color = Color(0xFFB42318))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingWalletAction = null
                    DappRpcCoordinator.send(
                        pending.origin, pending.request.id, pending.request.method,
                        pending.request.params.toString(), connectedSessionGeneration, pending.replyProxy
                    )
                }) { Text("交由外部钱包确认", color = AppColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "User rejected wallet request")
                    pendingWalletAction = null
                }) { Text("拒绝") }
            }
        )
    }

    pendingDappConnection?.takeIf { !waitingDappWalletApproval }?.let { pending ->
        val requestedHost = runCatching { Uri.parse(pending.origin).host.orEmpty() }.getOrDefault("unknown site")
        AlertDialog(
            onDismissRequest = {
                DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "User rejected wallet connection")
                pendingDappConnection = null
            },
            title = { Text("网站请求连接钱包", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("来源网站：$requestedHost", color = Color(0xFFB42318), fontWeight = FontWeight.Bold)
                    Text("该网站请求读取已连接钱包的公开地址。只有您确认后才会授权；本次不会签名消息、发送交易或读取助记词。", color = AppColors.TextPrimary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val addresses = connectedDappAccounts.map { it.address }.distinct()
                    if (addresses.isNotEmpty()) {
                        authorizedDappOrigins[pending.origin] = dappAuthorizationStamp
                        DappProviderBridge.success(pending.replyProxy, pending.request.id, JSONArray(addresses))
                        pendingDappConnection = null
                    } else {
                        waitingDappWalletApproval = true
                        walletNavController.openAppKit()
                    }
                }) { Text("确认并连接", color = AppColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = {
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
                    Text("选择钱包", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (wallets.isEmpty()) Text("尚未保存钱包地址", color = AppColors.TextSecondary)
                    wallets.forEach { wallet ->
                        TextButton(onClick = { selectedWallet = wallet; showWalletPicker = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("${wallet.walletName.ifBlank { wallet.chain }}  ·  ${shortAddress(wallet.address)}")
                        }
                    }
                    TextButton(onClick = { showWalletPicker = false }, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
                }
            }
        }
    }
}


private fun displayHost(rawUrl: String): String {
    return runCatching {
        Uri.parse(rawUrl).host.orEmpty().lowercase().ifBlank { "未知主机" }
    }.getOrDefault("未知主机")
}

private enum class BrowserPageState { Loading, Success, Error }

private const val CHROME_MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
private const val IOS_SAFARI_USER_AGENT =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

@Composable
private fun BrowserPageOverlay(
    state: BrowserPageState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit,
    onCopyUrl: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "loading-pulse")
    val pulse by infinite.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.fillMaxSize().background(Color(0xFFF8F9FA)), contentAlignment = Alignment.Center) {
        if (state == BrowserPageState.Loading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(76.dp).scale(pulse).background(Color(0xFFE8F3F1), CircleShape), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(42.dp), color = AppColors.Primary, strokeWidth = 3.dp)
                    Text("⚡", style = MaterialTheme.typography.titleLarge)
                }
                Text("正在连接网页，请稍候...", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("超时后将自动切换浏览器标识重试一次", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("☁️", style = MaterialTheme.typography.displaySmall)
                Text("网页加载失败", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "网页加载失败，可能是网络限制或网站防爬拦截。",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                    Text("重新加载")
                }
                OutlinedButton(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth()) {
                    Text("在外部浏览器打开")
                }
                TextButton(onClick = onCopyUrl, modifier = Modifier.fillMaxWidth()) { Text("复制网址") }
                TextButton(onClick = onBack) { Text("返回") }
            }
        }
    }
}