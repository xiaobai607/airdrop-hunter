package com.airdrophunter.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.ui.theme.AirdropHunterTheme
import com.tencent.smtt.export.external.interfaces.SslError
import com.tencent.smtt.export.external.interfaces.JsPromptResult
import com.tencent.smtt.export.external.interfaces.SslErrorHandler
import com.tencent.smtt.sdk.CookieManager
import com.tencent.smtt.sdk.WebChromeClient
import com.tencent.smtt.sdk.WebSettings
import com.tencent.smtt.sdk.WebView as X5WebView
import com.tencent.smtt.sdk.WebViewClient as X5WebViewClient
import org.json.JSONArray

private enum class X5PageState { Loading, Success, Error }

class X5BrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = normalizeX5BrowserUrl(intent.getStringExtra(BrowserActivity.EXTRA_URL).orEmpty())
        if (initialUrl == null) {
            Toast.makeText(this, "网址无效，请检查链接后重试", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val title = intent.getStringExtra(BrowserActivity.EXTRA_TITLE).orEmpty()
        setContent {
            AirdropHunterTheme {
                X5BrowserScreen(
                    title = title.ifBlank { Uri.parse(initialUrl).host.orEmpty() },
                    initialUrl = initialUrl,
                    walletJson = intent.getStringExtra(BrowserActivity.EXTRA_WALLETS_JSON).orEmpty(),
                    selectedWalletId = intent.getStringExtra(BrowserActivity.EXTRA_SELECTED_WALLET_ID).orEmpty(),
                    onConnectWallet = { _ ->
                        // Keep the active website in X5. WalletConnect gets its own
                        // focused surface and must never reopen this URL in BrowserActivity.
                        startActivity(Intent(this, WalletConnectActivity::class.java))
                    },
                    onExit = { finish() }
                )
            }
        }
    }
}

@Composable
private fun X5BrowserScreen(
    title: String,
    initialUrl: String,
    walletJson: String,
    selectedWalletId: String,
    onConnectWallet: (String) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val kernelState by X5KernelInitializer.state.collectAsStateWithLifecycle()
    var browser by remember { mutableStateOf<X5WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var currentUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    // Track the last document URL independently of the editable address bar/current navigation target.
    // This lets origin changes revoke permissions even when the user typed a new URL before load starts.
    var lastCommittedDocumentUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var addressInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    var isAddressEditing by remember { mutableStateOf(false) }
    var pageState by remember(initialUrl) { mutableStateOf(X5PageState.Loading) }
    var pageError by remember(initialUrl) { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var isX5InstanceConfirmed by remember { mutableStateOf(false) }
    val connectedDappAccounts by WalletConnectSessionDelegate.accounts.collectAsStateWithLifecycle()
    val walletSessionGeneration by WalletConnectSessionDelegate.sessionGeneration.collectAsStateWithLifecycle()
    val authorizedDappOrigins by DappOriginPermissionStore.authorizedOrigins.collectAsStateWithLifecycle()
    var pendingDappConnection by remember { mutableStateOf<PendingDappConnection?>(null) }
    var waitingDappWalletApproval by remember { mutableStateOf(false) }

    LaunchedEffect(currentUrl, isAddressEditing) {
        if (!isAddressEditing) addressInput = currentUrl
    }

    DisposableEffect(browser) {
        onDispose {
            browser?.let { view ->
                runCatching { view.stopLoading() }
                runCatching { view.loadUrl("about:blank") }
                runCatching { view.removeAllViews() }
                runCatching { view.destroy() }
            }
        }
    }

    LaunchedEffect(pageState, currentUrl, browser) {
        if (pageState == X5PageState.Loading && browser != null) {
            kotlinx.coroutines.delay(30_000)
            if (pageState == X5PageState.Loading) {
                pageState = X5PageState.Error
                pageError = "错误类型：X5 页面加载超时\n等待时间：30 秒\n当前网址：$currentUrl\n说明：腾讯 X5 内核未能在等待时间内提交首屏内容。"
            }
        }
    }

    val navigate = {
        val target = normalizeX5BrowserUrl(addressInput)
        if (target == null) {
            Toast.makeText(context, "网址无效，请输入正确的网址", Toast.LENGTH_SHORT).show()
        } else if (!isX5InstanceConfirmed || browser == null) {
            Toast.makeText(context, "腾讯 X5 内核尚未就绪，暂时不能加载网页", Toast.LENGTH_LONG).show()
        } else {
            isAddressEditing = false
            currentUrl = target
            pageState = X5PageState.Loading
            pageError = null
            browser?.stopLoading()
            browser?.loadUrl(target)
        }
    }

    LaunchedEffect(waitingDappWalletApproval, connectedDappAccounts, walletSessionGeneration) {
        val pending = pendingDappConnection
        if (waitingDappWalletApproval && pending != null && connectedDappAccounts.isNotEmpty()) {
            DappOriginPermissionStore.grant(pending.origin, x5DappAuthorizationStamp())
            DappProviderBridge.success(
                pending.replyProxy,
                pending.request.id,
                JSONArray(connectedDappAccounts.map { it.address }.distinct())
            )
            pendingDappConnection = null
            waitingDappWalletApproval = false
        }
    }

    LaunchedEffect(pendingDappConnection?.request?.id, waitingDappWalletApproval) {
        val requestId = pendingDappConnection?.request?.id ?: return@LaunchedEffect
        kotlinx.coroutines.delay(120_000)
        val pending = pendingDappConnection
        if (pending?.request?.id == requestId) {
            DappProviderBridge.error(pending.replyProxy, requestId, 4001, "Wallet connection timed out or was cancelled")
            pendingDappConnection = null
            waitingDappWalletApproval = false
        }
    }

    BackHandler {
        val active = browser
        if (active != null && active.canGoBack()) {
            active.goBack()
        } else {
            onExit()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onExit, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("返回")
                    }
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { onConnectWallet(currentUrl) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("连接钱包") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        modifier = Modifier.weight(1f).onFocusChanged { isAddressEditing = it.isFocused },
                        singleLine = true,
                        placeholder = { Text("输入网址") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate() })
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { navigate() }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text("前往")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { browser?.takeIf { it.canGoBack() }?.goBack() },
                        enabled = browser?.canGoBack() == true,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("后退") }
                    OutlinedButton(
                        onClick = { browser?.takeIf { it.canGoForward() }?.goForward() },
                        enabled = browser?.canGoForward() == true,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("前进") }
                    OutlinedButton(
                        onClick = {
                            pageState = X5PageState.Loading
                            pageError = null
                            browser?.reload()
                        },
                        enabled = isX5InstanceConfirmed,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("刷新") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        when (kernelState) {
                            X5KernelState.AVAILABLE -> if (isX5InstanceConfirmed) "腾讯 X5" else "验证内核…"
                            X5KernelState.INITIALIZING, X5KernelState.NOT_STARTED -> "X5 初始化中"
                            X5KernelState.UNAVAILABLE -> "X5 不可用"
                        },
                        color = if (kernelState == X5KernelState.AVAILABLE && isX5InstanceConfirmed)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (progress in 1..99 && kernelState == X5KernelState.AVAILABLE) {
                    LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                when (kernelState) {
                    X5KernelState.NOT_STARTED, X5KernelState.INITIALIZING -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator()
                            Text("正在准备腾讯 X5 内核", color = MaterialTheme.colorScheme.onBackground)
                            Text("内核初始化完成后会自动打开网页", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    X5KernelState.UNAVAILABLE -> {
                        val help = remember(kernelState) {
                            runCatching { com.tencent.smtt.sdk.QbSdk.getX5CoreLoadHelp(context.applicationContext) }
                                .getOrNull().orEmpty()
                        }
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("腾讯 X5 内核暂不可用", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("当前没有悄悄切回系统内核。为了避免误以为正在使用腾讯内核，网页暂不加载。",
                                color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                "SDK 版本：${runCatching { com.tencent.smtt.sdk.QbSdk.getTbsSdkVersion() }.getOrDefault(0)}\n" +
                                    "内核版本：${runCatching { com.tencent.smtt.sdk.QbSdk.getTbsVersion(context) }.getOrDefault(0)}\n" +
                                    "诊断信息：${help.ifBlank { "腾讯 SDK 未提供详细原因" }}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    X5KernelState.AVAILABLE -> {
                        key(webViewGeneration) {
                            AndroidView(
                                factory = { ctx ->
                                    runCatching {
                                        X5WebView(ctx).apply {
                                            setBackgroundColor(AndroidColor.WHITE)
                                            settings.apply {
                                                javaScriptEnabled = true
                                                domStorageEnabled = true
                                                databaseEnabled = true
                                                loadsImagesAutomatically = true
                                                allowFileAccess = false
                                                allowContentAccess = false
                                                setAllowFileAccessFromFileURLs(false)
                                                setAllowUniversalAccessFromFileURLs(false)
                                                javaScriptCanOpenWindowsAutomatically = false
                                                setSupportMultipleWindows(false)
                                                setMixedContentMode(0)
                                                setSafeBrowsingEnabled(true)
                                                useWideViewPort = true
                                                loadWithOverviewMode = true
                                            }
                                            CookieManager.getInstance().setAcceptCookie(true)
                                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                            webViewClient = object : X5WebViewClient() {
                                                override fun shouldOverrideUrlLoading(view: X5WebView?, url: String?): Boolean {
                                                    val target = url ?: return true
                                                    val scheme = runCatching { Uri.parse(target).scheme?.lowercase() }.getOrNull()
                                                    if (scheme == "https" || scheme == "http") return false
                                                    Toast.makeText(context, "已阻止不支持的链接协议；仅允许安全网页链接", Toast.LENGTH_LONG).show()
                                                    return true
                                                }

                                                override fun onPageStarted(view: X5WebView?, url: String?, favicon: Bitmap?) {
                                                    url?.let { nextUrl ->
                                                        val previousOrigin = runCatching {
                                                            DappProviderBridge.origin(Uri.parse(lastCommittedDocumentUrl))
                                                        }.getOrNull()
                                                        val nextOrigin = runCatching {
                                                            DappProviderBridge.origin(Uri.parse(nextUrl))
                                                        }.getOrNull()
                                                        if (previousOrigin != null && previousOrigin != nextOrigin) {
                                                            DappOriginPermissionStore.revokeAll()
                                                        }
                                                        // A full document navigation invalidates any unanswered prompt,
                                                        // even when it stays on the same origin. Never let an old page's
                                                        // approval dialog authorize a later document.
                                                        pendingDappConnection?.let { pending ->
                                                            DappProviderBridge.error(
                                                                pending.replyProxy, pending.request.id, 4001,
                                                                "The page navigated before wallet access was approved"
                                                            )
                                                        }
                                                        pendingDappConnection = null
                                                        waitingDappWalletApproval = false
                                                        lastCommittedDocumentUrl = nextUrl
                                                        currentUrl = nextUrl
                                                        if (!isAddressEditing) addressInput = nextUrl
                                                    }
                                                    pageState = X5PageState.Loading
                                                    pageError = null
                                                }

                                                override fun onPageCommitVisible(view: X5WebView?, url: String?) {
                                                    url?.let { currentUrl = it }
                                                    pageState = X5PageState.Success
                                                    pageError = null
                                                }

                                                override fun onPageFinished(view: X5WebView?, url: String?) {
                                                    url?.let { currentUrl = it }
                                                    if (pageState != X5PageState.Error) pageState = X5PageState.Success
                                                    if (view != null && DappProviderBridge.isTrustedX5Prompt(url, view.url)) {
                                                        // evaluateJavascript runs in the top-level page context. The script
                                                        // refuses to install itself in a subframe.
                                                        view.evaluateJavascript(DappProviderBridge.X5_PROVIDER_SCRIPT, null)
                                                    }
                                                }

                                                override fun onReceivedError(
                                                    view: X5WebView?,
                                                    errorCode: Int,
                                                    description: String?,
                                                    failingUrl: String?
                                                ) {
                                                    pageState = X5PageState.Error
                                                    pageError = "错误类型：腾讯 X5 网络错误\n错误码：$errorCode\n错误描述：${description ?: "系统未提供"}\n失败网址：${failingUrl ?: currentUrl}"
                                                    Log.e("AirdropX5Browser", "X5 load error code=$errorCode description=$description url=$failingUrl")
                                                }

                                                override fun onReceivedSslError(
                                                    view: X5WebView?,
                                                    handler: SslErrorHandler?,
                                                    error: SslError?
                                                ) {
                                                    handler?.cancel()
                                                    pageState = X5PageState.Error
                                                    pageError = "错误类型：HTTPS/SSL 证书验证失败\n证书错误：${error ?: "未知"}\n网址：$currentUrl\n安全处理：已阻止连接，没有跳过证书验证。"
                                                    Log.e("AirdropX5Browser", "Blocked X5 page because TLS validation failed: $error")
                                                }
                                            }
                                            webChromeClient = object : WebChromeClient() {
                                                override fun onProgressChanged(view: X5WebView?, newProgress: Int) {
                                                    progress = newProgress
                                                }

                                                override fun onJsPrompt(
                                                    view: X5WebView?,
                                                    url: String?,
                                                    message: String?,
                                                    defaultValue: String?,
                                                    result: JsPromptResult?
                                                ): Boolean {
                                                    if (message != DappProviderBridge.X5_PROMPT_PREFIX) return false
                                                    val activeView = view
                                                    val promptResult = result
                                                    if (activeView == null || promptResult == null ||
                                                        !DappProviderBridge.isTrustedX5Prompt(url, activeView.url)
                                                    ) {
                                                        promptResult?.confirm("rejected")
                                                        return true
                                                    }
                                                    val request = DappProviderBridge.parseX5Prompt(message, defaultValue)
                                                    if (request == null) {
                                                        promptResult.confirm("rejected")
                                                        return true
                                                    }
                                                    val origin = runCatching {
                                                        DappProviderBridge.origin(Uri.parse(url))
                                                    }.getOrNull()
                                                    if (origin.isNullOrBlank()) {
                                                        promptResult.confirm("rejected")
                                                        return true
                                                    }
                                                    val replyChannel = DappReplyChannel { response ->
                                                        activeView.post {
                                                            if (activeView.url == url) {
                                                                activeView.evaluateJavascript(
                                                                    DappProviderBridge.x5ResponseDispatchScript(response), null
                                                                )
                                                            }
                                                        }
                                                    }
                                                    promptResult.confirm("queued")
                                                    when (request.method) {
                                                        "eth_requestAccounts" -> {
                                                            if (pendingDappConnection != null) {
                                                                DappProviderBridge.error(replyChannel, request.id, -32002, "A wallet connection request is already pending")
                                                            } else if (authorizedDappOrigins[origin] == x5DappAuthorizationStamp() &&
                                                                connectedDappAccounts.isNotEmpty()
                                                            ) {
                                                                DappProviderBridge.success(
                                                                    replyChannel, request.id,
                                                                    JSONArray(connectedDappAccounts.map { it.address }.distinct())
                                                                )
                                                            } else {
                                                                pendingDappConnection = PendingDappConnection(request, origin, replyChannel)
                                                            }
                                                        }
                                                        "eth_accounts" -> {
                                                            val addresses = if (authorizedDappOrigins[origin] == x5DappAuthorizationStamp()) {
                                                                connectedDappAccounts.map { it.address }.distinct()
                                                            } else emptyList()
                                                            DappProviderBridge.success(replyChannel, request.id, JSONArray(addresses))
                                                        }
                                                        "eth_chainId", "net_version" -> {
                                                            val rawChain = connectedDappAccounts.firstOrNull()?.chainId
                                                                ?: runCatching { com.reown.appkit.client.AppKit.getSelectedChain()?.chainReference }.getOrNull()
                                                            val reference = rawChain?.removePrefix("eip155:")?.trim()
                                                            val number = when {
                                                                reference.isNullOrBlank() -> null
                                                                reference.startsWith("0x", true) -> reference.substring(2).toLongOrNull(16)
                                                                else -> reference.toLongOrNull()
                                                            }
                                                            if (number == null || number <= 0L) {
                                                                DappProviderBridge.error(replyChannel, request.id, -32000, "Active EVM chain is unavailable")
                                                            } else {
                                                                val value = if (request.method == "eth_chainId") "0x" + number.toString(16) else number.toString()
                                                                DappProviderBridge.success(replyChannel, request.id, value)
                                                            }
                                                        }
                                                        else -> DappProviderBridge.error(
                                                            replyChannel, request.id, 4200,
                                                            if (DappProviderBridge.isBlockedRawSubmissionMethod(request.method))
                                                                "Raw transaction submission is blocked; use a wallet-reviewed transaction request"
                                                            else
                                                                "This X5 wallet bridge stage currently supports account access and chain queries only"
                                                        )
                                                    }
                                                    return true
                                                }
                                            }
                                            setDownloadListener { downloadUrl, _, _, _, _ ->
                                                Toast.makeText(context, "检测到下载链接，请使用外部浏览器处理：${downloadUrl.take(100)}", Toast.LENGTH_LONG).show()
                                            }
                                            if (getIsX5Core()) {
                                                isX5InstanceConfirmed = true
                                                loadUrl(initialUrl)
                                            } else {
                                                isX5InstanceConfirmed = false
                                                pageState = X5PageState.Error
                                                pageError = "错误类型：X5 内核验证失败\n说明：SDK WebView 实例没有使用腾讯 X5 内核。为避免冒充 X5，本页面没有加载网址。"
                                            }
                                        }
                                    }.getOrElse { error ->
                                        pageState = X5PageState.Error
                                        pageError = "错误类型：X5 WebView 创建失败\n原因：${error.javaClass.simpleName}: ${error.localizedMessage ?: "未提供"}"
                                        Log.e("AirdropX5Browser", "Could not create X5 WebView", error)
                                        android.widget.FrameLayout(ctx)
                                    }
                                },
                                update = { view ->
                                    if (view is X5WebView && browser !== view) browser = view
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (!isX5InstanceConfirmed || pageState == X5PageState.Error) {
                            Column(
                                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("网页无法打开", color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(pageError ?: "X5 页面加载失败，请重试。", color = MaterialTheme.colorScheme.onBackground)
                                Button(onClick = {
                                    if (browser != null && isX5InstanceConfirmed) {
                                        pageState = X5PageState.Loading
                                        pageError = null
                                        browser?.reload()
                                    } else {
                                        webViewGeneration += 1
                                        pageState = X5PageState.Loading
                                        pageError = null
                                    }
                                }) { Text("重新加载") }
                            }
                        } else if (pageState == X5PageState.Loading && browser != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator()
                                Text("正在使用腾讯 X5 加载网页", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            BrowserAccountingToolbar(
                walletAddress = "",
                chainName = "unknown",
                onCopyAddress = {
                    Toast.makeText(context, "钱包相关操作请从原有钱包 DApp 入口进入", Toast.LENGTH_SHORT).show()
                },
                onRefreshPage = { browser?.reload() },
                onCopyPageUrl = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Page URL", currentUrl))
                    Toast.makeText(context, "网址已复制", Toast.LENGTH_SHORT).show()
                },
                onOpenPageExternally = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }
                        .onFailure { Toast.makeText(context, "未找到可用的外部浏览器", Toast.LENGTH_SHORT).show() }
                },
                modifier = Modifier.fillMaxWidth().height(72.dp).navigationBarsPadding(),
                showWalletTools = false
            )
        }
    }
    pendingDappConnection?.takeIf { !waitingDappWalletApproval }?.let { pending ->
        val requestedHost = runCatching { Uri.parse(pending.origin).host.orEmpty() }.getOrDefault("unknown site")
        AlertDialog(
            onDismissRequest = {
                DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "User rejected wallet connection")
                pendingDappConnection = null
            },
            title = { Text("网站请求连接钱包") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("来源网站：$requestedHost", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text("确认后，该网站才能读取当前外部钱包的公开地址。本次不会签名消息或发送交易。")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val addresses = connectedDappAccounts.map { it.address }.distinct()
                    if (addresses.isNotEmpty()) {
                        DappOriginPermissionStore.grant(pending.origin, x5DappAuthorizationStamp())
                        DappProviderBridge.success(pending.replyProxy, pending.request.id, JSONArray(addresses))
                        pendingDappConnection = null
                    } else {
                        waitingDappWalletApproval = true
                        onConnectWallet(currentUrl)
                    }
                }) { Text(if (connectedDappAccounts.isEmpty()) "连接钱包并授权" else "确认授权") }
            },
            dismissButton = {
                TextButton(onClick = {
                    DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "User rejected wallet connection")
                    pendingDappConnection = null
                }) { Text("拒绝") }
            }
        )
    }
}

private fun x5DappAuthorizationStamp(): String {
    val generation = WalletConnectSessionDelegate.sessionGeneration.value
    val addresses = WalletConnectSessionDelegate.accounts.value
        .map { it.address.lowercase() }
        .sorted()
    return generation.toString() + ":" + addresses.joinToString(",")
}


private fun normalizeX5BrowserUrl(value: String): String? {
    return runCatching {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val parsed = Uri.parse(withScheme)
        if (parsed.scheme !in setOf("http", "https") || parsed.host.isNullOrBlank() || parsed.userInfo != null) {
            null
        } else {
            parsed.toString()
        }
    }.getOrNull()
}
