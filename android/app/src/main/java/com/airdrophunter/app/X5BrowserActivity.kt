package com.airdrophunter.app

import androidx.compose.ui.res.stringResource

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
import com.airdrophunter.app.data.Web3TransactionRepository
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AppTextField
import com.airdrophunter.app.ui.theme.AirdropHunterTheme
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.client.models.request.Request as ExternalWalletRequest
import com.reown.appkit.client.models.request.SentRequestResult
import kotlinx.coroutines.launch
import org.json.JSONObject
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

private data class X5PendingExternalWalletRequest(
    val request: DappRpcRequest,
    val origin: String,
    val replyProxy: DappReplyChannel,
    val sessionTopic: String,
    val chainId: String,
    val walletConnectRequestId: Long? = null,
    val web3DraftId: String? = null
)

class X5BrowserActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = normalizeX5BrowserUrl(intent.getStringExtra(BrowserActivity.EXTRA_URL).orEmpty())
        if (initialUrl == null) {
            Toast.makeText(this, getString(R.string.loc_x5_invalid_start_url), Toast.LENGTH_LONG).show()
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
    val app = context.applicationContext as AirdropHunterApplication
    val web3Repository = remember(app) { app.appContainer.web3TransactionRepository }
    val kernelState by X5KernelInitializer.state.collectAsStateWithLifecycle()
    val kernelDiagnostics by X5KernelInitializer.diagnostics.collectAsStateWithLifecycle()
    var browser by remember { mutableStateOf<X5WebView?>(null) }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var providerInjectionGeneration by remember { mutableIntStateOf(0) }
    var currentUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    // Track the last document URL independently of the editable address bar/current navigation target.
    // This lets origin changes revoke permissions even when the user typed a new URL before load starts.
    var lastCommittedDocumentUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var addressInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    var isAddressEditing by remember { mutableStateOf(false) }
    var pageState by remember(initialUrl) { mutableStateOf(X5PageState.Loading) }
    var pageError by remember(initialUrl) { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var isBrowserInstanceReady by remember { mutableStateOf(false) }
    var isUsingX5Core by remember { mutableStateOf(false) }
    val connectedDappAccounts by WalletConnectSessionDelegate.accounts.collectAsStateWithLifecycle()
    val walletSessionGeneration by WalletConnectSessionDelegate.sessionGeneration.collectAsStateWithLifecycle()
    val activeWalletConnectTopic by WalletConnectSessionDelegate.sessionTopic.collectAsStateWithLifecycle()
    val authorizedDappOrigins by DappOriginPermissionStore.authorizedOrigins.collectAsStateWithLifecycle()
    val dappRequestScope = rememberCoroutineScope()
    var pendingDappConnection by remember { mutableStateOf<PendingDappConnection?>(null) }
    var pendingDappExternalRequest by remember { mutableStateOf<X5PendingExternalWalletRequest?>(null) }
    var waitingDappWalletApproval by remember { mutableStateOf(false) }
    var lastObservedSessionGeneration by remember { mutableStateOf(walletSessionGeneration) }

    LaunchedEffect(Unit) {
        WalletConnectInitializer.ensureInitialized(context.applicationContext as android.app.Application)
        app.startWalletResponseReconciliation()
    }

    // Do not leave the browser stuck forever if the vendor kernel callback stalls.
    // The current process switches to Android System WebView; a future launch can retry X5.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(15_000)
        if (X5KernelInitializer.state.value == X5KernelState.INITIALIZING ||
            X5KernelInitializer.state.value == X5KernelState.NOT_STARTED
        ) {
            X5KernelInitializer.activateSystemWebViewFallback(
                context.getString(R.string.loc_x5_01)
            )
        }
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
                                txHash != null && Web3TransactionRepository.isValidTxHash(txHash)
                            ) {
                                val accepted = runCatching {
                                    web3Repository.attachWalletConnectHash(
                                        draftId, walletRequestId, chainNumber, txHash
                                    )
                                }.onFailure {
                                    Log.e("AirdropX5Browser", "Could not persist WalletConnect transaction hash", it)
                                }.getOrDefault(false)
                                if (accepted) {
                                    com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(
                                        context.applicationContext, draftId
                                    )
                                } else {
                                    runCatching { web3Repository.markWalletTimedOut(draftId) }
                                    Log.e("AirdropX5Browser", "WalletConnect hash was not attached; draft requires manual chain sync")
                                }
                            } else if (draftId != null) {
                                runCatching { web3Repository.markWalletTimedOut(draftId) }
                                Log.e("AirdropX5Browser", "WalletConnect returned an invalid or incomplete transaction hash")
                            }
                        }
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
            pending.web3DraftId?.let { draftId -> web3Repository.markWalletTimedOut(draftId) }
            DappProviderBridge.error(pending.replyProxy, requestId, 4001, "External wallet request timed out or was cancelled; chain status still requires reconciliation")
            pendingDappExternalRequest = null
        }
    }

    LaunchedEffect(waitingDappWalletApproval, connectedDappAccounts, walletSessionGeneration) {
        if (walletSessionGeneration != lastObservedSessionGeneration) {
            lastObservedSessionGeneration = walletSessionGeneration
            val completingApprovedConnection =
                waitingDappWalletApproval && pendingDappConnection != null && connectedDappAccounts.isNotEmpty()
            if (!completingApprovedConnection) {
                DappOriginPermissionStore.revokeAll()
                pendingDappExternalRequest?.let { pending ->
                    pending.web3DraftId?.let { id ->
                        dappRequestScope.launch { web3Repository.markWalletTimedOut(id) }
                    }
                    DappProviderBridge.error(pending.replyProxy, pending.request.id, 4100, "Wallet session changed; the transaction draft is retained for chain reconciliation")
                    pendingDappExternalRequest = null
                }
            }
        }
    }

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
                pageError = context.getString(R.string.loc_x5_timeout_detail, Uri.parse(currentUrl).host.orEmpty().ifBlank { context.getString(R.string.loc_x5_unknown_site) })
            }
        }
    }

    val navigate = {
        val target = normalizeX5BrowserUrl(addressInput)
        if (target == null) {
            Toast.makeText(context, context.getString(R.string.loc_x5_02), Toast.LENGTH_SHORT).show()
        } else if (!isBrowserInstanceReady || browser == null) {
            Toast.makeText(context, context.getString(R.string.loc_x5_03), Toast.LENGTH_LONG).show()
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

    // EIP-1193 wallet events are sent only to the currently displayed HTTPS origin
    // after that exact origin has an active account permission for this wallet session.
    LaunchedEffect(
        browser, currentUrl, connectedDappAccounts, walletSessionGeneration,
        authorizedDappOrigins, providerInjectionGeneration, pageState
    ) {
        if (providerInjectionGeneration == 0 || pageState != X5PageState.Success) return@LaunchedEffect
        val view = browser ?: return@LaunchedEffect
        val origin = runCatching { DappProviderBridge.origin(Uri.parse(currentUrl)) }.getOrNull()
            ?: return@LaunchedEffect
        if (authorizedDappOrigins[origin] != x5DappAuthorizationStamp()) return@LaunchedEffect
        val pageUrl = view.url
        if (!DappProviderBridge.isTrustedX5Prompt(pageUrl, currentUrl)) return@LaunchedEffect

        val addresses = connectedDappAccounts.map { it.address }.distinct()
        val accountsJson = JSONArray(addresses).toString()
        runCatching {
            view.evaluateJavascript(
                DappProviderBridge.x5EventDispatchScript("accountsChanged", accountsJson), null
            )
        }

        val rawChain = connectedDappAccounts.firstOrNull()?.chainId
            ?: runCatching { AppKit.getSelectedChain()?.chainReference }.getOrNull()
        val chainReference = rawChain?.removePrefix("eip155:")?.trim()
        val chainNumber = chainReference?.let { value ->
            runCatching {
                if (value.startsWith("0x", true)) value.substring(2).toLong(16)
                else value.toLong()
            }.getOrNull()
        }
        if (chainNumber != null && chainNumber > 0L) {
            val chainHexJson = JSONObject.quote("0x" + chainNumber.toString(16))
            runCatching {
                view.evaluateJavascript(
                    DappProviderBridge.x5EventDispatchScript("chainChanged", chainHexJson), null
                )
            }
        } else {
            runCatching {
                view.evaluateJavascript(
                    DappProviderBridge.x5EventDispatchScript("disconnect", JSONObject().put("code", 4900).toString()), null
                )
            }
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
                     .padding(horizontal = AppDesignTokens.Spacing.Lg.dp, vertical = AppDesignTokens.Spacing.Sm.dp),
                verticalArrangement = Arrangement.spacedBy(AppDesignTokens.Spacing.Sm.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(onClick = onExit) { Text(context.getString(R.string.loc_x5_04)) }
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    AppButton(
                        text = context.getString(R.string.loc_x5_05),
                        onClick = { onConnectWallet(currentUrl) },
                        style = AppButtonStyle.SECONDARY,
                        fullWidth = false,
                        heightDp = 44
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDesignTokens.Spacing.Sm.dp)
                ) {
                    AppTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        modifier = Modifier.weight(1f).onFocusChanged { isAddressEditing = it.isFocused },
                        placeholder = context.getString(R.string.loc_x5_06),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigate() })
                    )
                    AppButton(
                        text = context.getString(R.string.loc_x5_07),
                        onClick = { navigate() },
                        fullWidth = false,
                        heightDp = 48
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDesignTokens.Spacing.Sm.dp)
                ) {
                    AppButton(
                        text = context.getString(R.string.loc_x5_08),
                        onClick = { browser?.takeIf { it.canGoBack() }?.goBack() },
                        enabled = browser?.canGoBack() == true,
                        style = AppButtonStyle.SECONDARY,
                        fullWidth = false,
                        heightDp = 44
                    )
                    AppButton(
                        text = context.getString(R.string.loc_x5_09),
                        onClick = { browser?.takeIf { it.canGoForward() }?.goForward() },
                        enabled = browser?.canGoForward() == true,
                        style = AppButtonStyle.SECONDARY,
                        fullWidth = false,
                        heightDp = 44
                    )
                    AppButton(
                        text = context.getString(R.string.loc_x5_10),
                        onClick = {
                            pageState = X5PageState.Loading
                            pageError = null
                            browser?.reload()
                        },
                        enabled = isBrowserInstanceReady,
                        style = AppButtonStyle.SECONDARY,
                        fullWidth = false,
                        heightDp = 44
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        when (kernelState) {
                            X5KernelState.AVAILABLE -> if (!isBrowserInstanceReady) context.getString(R.string.loc_x5_11) else if (isUsingX5Core) context.getString(R.string.loc_x5_12) else context.getString(R.string.loc_x5_13)
                            X5KernelState.INITIALIZING, X5KernelState.NOT_STARTED -> context.getString(R.string.loc_x5_11)
                            X5KernelState.UNAVAILABLE -> context.getString(R.string.loc_x5_14)
                        },
                        color = if (kernelState == X5KernelState.AVAILABLE && isBrowserInstanceReady)
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppDesignTokens.Spacing.Md.dp)) {
                            CircularProgressIndicator()
                            Text(context.getString(R.string.loc_x5_15), color = MaterialTheme.colorScheme.onBackground)
                            Text(context.getString(R.string.loc_x5_16), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    X5KernelState.UNAVAILABLE -> {
                        Column(
                            Modifier.fillMaxWidth().padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppDesignTokens.Spacing.Md.dp)
                        ) {
                            Text(context.getString(R.string.loc_x5_17), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                kernelDiagnostics.lastFailure ?: context.getString(R.string.loc_x5_18),
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
                                                    Toast.makeText(context, context.getString(R.string.loc_x5_19), Toast.LENGTH_LONG).show()
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
                                                        pendingDappExternalRequest?.let { pending ->
                                                            pending.web3DraftId?.let { id ->
                                                                dappRequestScope.launch { web3Repository.markWalletTimedOut(id) }
                                                            }
                                                            DappProviderBridge.error(
                                                                pending.replyProxy, pending.request.id, 4001,
                                                                "The page navigated; the transaction draft is retained for chain reconciliation"
                                                            )
                                                            pendingDappExternalRequest = null
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
                                                        providerInjectionGeneration += 1
                                                    }
                                                }

                                                override fun onReceivedError(
                                                    view: X5WebView?,
                                                    errorCode: Int,
                                                    description: String?,
                                                    failingUrl: String?
                                                ) {
                                                    pageState = X5PageState.Error
                                                    pageError = context.getString(R.string.loc_x5_network_error_detail, errorCode, description ?: context.getString(R.string.loc_x5_system_not_provided), failingUrl ?: currentUrl)
                                                    Log.e("AirdropX5Browser", "X5 load error code=$errorCode description=$description url=$failingUrl")
                                                }

                                                override fun onReceivedSslError(
                                                    view: X5WebView?,
                                                    handler: SslErrorHandler?,
                                                    error: SslError?
                                                ) {
                                                    handler?.cancel()
                                                    pageState = X5PageState.Error
                                                    pageError = context.getString(R.string.loc_x5_ssl_error_detail, error ?: context.getString(R.string.loc_x5_unknown_error), currentUrl)
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
                                                                DappProviderBridge.success(replyChannel, request.id,
                                                                    JSONArray(connectedDappAccounts.map { it.address }.distinct()))
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
                                                                ?: runCatching { AppKit.getSelectedChain()?.chainReference }.getOrNull()
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
                                                        "wallet_switchEthereumChain" -> DappProviderBridge.error(
                                                            replyChannel, request.id, 4200, "Switch networks in the connected wallet, then reconnect this site"
                                                        )
                                                        else -> {
                                                            if (!DappProviderBridge.isSupportedExternalWalletMethod(request.method)) {
                                                                DappProviderBridge.error(
                                                                    replyChannel, request.id, 4200,
                                                                    if (DappProviderBridge.isBlockedRawSubmissionMethod(request.method))
                                                                        "Raw signed transaction submission is blocked"
                                                                    else "Unsupported wallet method: ${request.method}"
                                                                )
                                                            } else {
                                                                val sessionTopic = activeWalletConnectTopic
                                                                val authorized = authorizedDappOrigins[origin] == x5DappAuthorizationStamp()
                                                                val connectedAddresses = connectedDappAccounts.map { it.address }.toSet()
                                                                val firstParam = request.params.opt(0) as? String
                                                                val secondParam = request.params.opt(1) as? String
                                                                val transactionFrom = request.params.optJSONObject(0)?.optString("from")
                                                                val requestedAddress = DappProviderBridge.requestedAccountAddress(
                                                                    request.method, firstParam, secondParam, transactionFrom
                                                                )
                                                                val selectedChain = AppKit.getSelectedChain()?.chainReference?.removePrefix("eip155:")
                                                                val account = connectedDappAccounts.firstOrNull { candidate ->
                                                                    !requestedAddress.isNullOrBlank() &&
                                                                        candidate.address.equals(requestedAddress, ignoreCase = true) &&
                                                                        !selectedChain.isNullOrBlank() &&
                                                                        candidate.chainId.removePrefix("eip155:") == selectedChain
                                                                }
                                                                when {
                                                                    !authorized || connectedDappAccounts.isEmpty() ->
                                                                        DappProviderBridge.error(replyChannel, request.id, 4100, "Authorize this website before requesting a signature or transaction")
                                                                    sessionTopic.isNullOrBlank() ->
                                                                        DappProviderBridge.error(replyChannel, request.id, 4200, "The active wallet session cannot forward DApp requests")
                                                                    pendingDappExternalRequest != null ->
                                                                        DappProviderBridge.error(replyChannel, request.id, -32002, "Another external wallet request is awaiting confirmation")
                                                                    !DappProviderBridge.isValidExternalWalletParams(request.method, request.params) ->
                                                                        DappProviderBridge.error(replyChannel, request.id, -32602, "Invalid signing or transaction parameters")
                                                                    !DappProviderBridge.isAuthorizedAccountRequest(
                                                                        request.method, firstParam, secondParam, transactionFrom, connectedAddresses
                                                                    ) ->
                                                                        DappProviderBridge.error(replyChannel, request.id, 4100, "The requested account is not authorized in this wallet session")
                                                                    account == null ->
                                                                        DappProviderBridge.error(replyChannel, request.id, 4901, "The requested account is not authorized on the active EVM network")
                                                                    !DappProviderBridge.isTransactionChainCompatible(
                                                                        request.method, request.params.optJSONObject(0)?.optString("chainId"), account.chainId
                                                                    ) ->
                                                                        DappProviderBridge.error(replyChannel, request.id, 4901, "The transaction chain does not match the active wallet network")
                                                                    else -> {
                                                                        val chainReference = account.chainId.let { if (it.startsWith("eip155:")) it else "eip155:$it" }
                                                                        pendingDappExternalRequest = X5PendingExternalWalletRequest(
                                                                            request, origin, replyChannel, sessionTopic, chainReference
                                                                        )
                                                                        dappRequestScope.launch {
                                                                            var draftId: String? = null
                                                                            if (request.method.equals("eth_sendTransaction", ignoreCase = true)) {
                                                                                val transaction = request.params.optJSONObject(0)
                                                                                val chainNumber = chainReference.removePrefix("eip155:").toLongOrNull()
                                                                                if (transaction == null || chainNumber == null) {
                                                                                    DappProviderBridge.error(replyChannel, request.id, -32602, "Invalid transaction parameters or chain ID")
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
                                                                                }.getOrElse { error ->
                                                                                    Log.e("AirdropX5Browser", "Failed to persist Web3 transaction draft", error)
                                                                                    DappProviderBridge.error(replyChannel, request.id, -32000, "Could not safely save the transaction draft; request blocked")
                                                                                    pendingDappExternalRequest = null
                                                                                    return@launch
                                                                                }
                                                                            }
                                                                            val activePending = pendingDappExternalRequest
                                                                            if (activePending?.request?.id != request.id) {
                                                                                draftId?.let { web3Repository.markWalletTimedOut(it) }
                                                                                return@launch
                                                                            }
                                                                            pendingDappExternalRequest = activePending.copy(web3DraftId = draftId)
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
                                                                                                val walletRequest = sentRequest as? SentRequestResult.WalletConnect
                                                                                                if (walletRequest == null || walletRequest.sessionTopic != currentPending.sessionTopic) {
                                                                                                    currentPending.web3DraftId?.let { web3Repository.markWalletTimedOut(it) }
                                                                                                    DappProviderBridge.error(replyChannel, request.id, 4200, "Could not verify the external wallet request; cancelled safely")
                                                                                                    pendingDappExternalRequest = null
                                                                                                } else {
                                                                                                    val bound = currentPending.web3DraftId?.let { id ->
                                                                                                        web3Repository.bindWalletConnectRequest(id, walletRequest.requestId, walletRequest.sessionTopic)
                                                                                                    } ?: true
                                                                                                    if (!bound) {
                                                                                                        currentPending.web3DraftId?.let { web3Repository.markWalletTimedOut(it) }
                                                                                                        DappProviderBridge.error(replyChannel, request.id, 4200, "Could not bind the wallet request to its transaction draft")
                                                                                                        pendingDappExternalRequest = null
                                                                                                    } else {
                                                                                                        currentPending.web3DraftId?.let { id ->
                                                                                                            com.airdrophunter.app.data.Web3DraftTimeoutScheduler.enqueue(context.applicationContext, id)
                                                                                                        }
                                                                                                        pendingDappExternalRequest = currentPending.copy(walletConnectRequestId = walletRequest.requestId)
                                                                                                    }
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    },
                                                                                    onError = { error ->
                                                                                        dappRequestScope.launch {
                                                                                            val active = pendingDappExternalRequest
                                                                                            if (active?.request?.id == request.id) {
                                                                                                active.web3DraftId?.let { web3Repository.markWalletTimedOut(it) }
                                                                                                DappProviderBridge.error(replyChannel, request.id, 4001,
                                                                                                    error.localizedMessage ?: "External wallet request failed or was rejected")
                                                                                                pendingDappExternalRequest = null
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                )
                                                                            }.onFailure { error ->
                                                                                val active = pendingDappExternalRequest
                                                                                if (active?.request?.id == request.id &&
                                                                                    active.sessionTopic == sessionTopic && active.origin == origin
                                                                                ) {
                                                                                    active.web3DraftId?.let { web3Repository.markWalletTimedOut(it) }
                                                                                    DappProviderBridge.error(replyChannel, request.id, 4001,
                                                                                        error.localizedMessage ?: "Could not open external wallet request")
                                                                                    pendingDappExternalRequest = null
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    return true
                                                }
                                            }
                                            setDownloadListener { downloadUrl, _, _, _, _ ->
                                                Toast.makeText(context, context.getString(R.string.loc_x5_download_detected, Uri.parse(downloadUrl).host.orEmpty().ifBlank { context.getString(R.string.loc_x5_unknown_site) }), Toast.LENGTH_LONG).show()
                                            }
                                            isUsingX5Core = getIsX5Core()
                                            isBrowserInstanceReady = true
                                            // The Tencent wrapper automatically uses Android System WebView
                                            // when X5 is unavailable; do not block the initial page load.
                                            loadUrl(initialUrl)
                                        }
                                    }.getOrElse { error ->
                                        pageState = X5PageState.Error
                                        pageError = context.getString(R.string.loc_x5_create_failed, error.javaClass.simpleName, error.localizedMessage ?: context.getString(R.string.loc_x5_system_not_provided))
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
                        if (!isBrowserInstanceReady || pageState == X5PageState.Error) {
                            Column(
                                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(AppDesignTokens.Spacing.Md.dp)
                            ) {
                                Text(context.getString(R.string.loc_x5_20), color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(pageError ?: context.getString(R.string.loc_x5_21), color = MaterialTheme.colorScheme.onBackground)
                                AppButton(
                                    text = context.getString(R.string.loc_x5_22),
                                    onClick = {
                                        if (browser != null && isBrowserInstanceReady) {
                                            pageState = X5PageState.Loading
                                            pageError = null
                                            browser?.reload()
                                        } else {
                                            webViewGeneration += 1
                                            pageState = X5PageState.Loading
                                            pageError = null
                                        }
                                    }
                                )
                            }
                        } else if (pageState == X5PageState.Loading && browser != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator()
                                Text(if (isUsingX5Core) context.getString(R.string.loc_x5_23) else context.getString(R.string.loc_x5_24), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            BrowserAccountingToolbar(
                walletAddress = "",
                chainName = "unknown",
                onCopyAddress = {
                    Toast.makeText(context, context.getString(R.string.loc_x5_25), Toast.LENGTH_SHORT).show()
                },
                onRefreshPage = { browser?.reload() },
                onCopyPageUrl = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Page URL", currentUrl))
                    Toast.makeText(context, context.getString(R.string.loc_x5_26), Toast.LENGTH_SHORT).show()
                },
                onOpenPageExternally = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }
                        .onFailure { Toast.makeText(context, context.getString(R.string.loc_x5_27), Toast.LENGTH_SHORT).show() }
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
            title = { Text(context.getString(R.string.loc_x5_28)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.loc_x5_origin_label, requestedHost), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text(context.getString(R.string.loc_x5_29))
                }
            },
            confirmButton = {
                AppTextButton(onClick = {
                    val addresses = connectedDappAccounts.map { it.address }.distinct()
                    if (addresses.isNotEmpty()) {
                        DappOriginPermissionStore.grant(pending.origin, x5DappAuthorizationStamp())
                        DappProviderBridge.success(pending.replyProxy, pending.request.id, JSONArray(addresses))
                        pendingDappConnection = null
                    } else {
                        waitingDappWalletApproval = true
                        onConnectWallet(currentUrl)
                    }
                }) { Text(if (connectedDappAccounts.isEmpty()) context.getString(R.string.loc_x5_30) else context.getString(R.string.loc_x5_31)) }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    DappProviderBridge.error(pending.replyProxy, pending.request.id, 4001, "User rejected wallet connection")
                    pendingDappConnection = null
                }) { Text(context.getString(R.string.loc_x5_32)) }
            }
        )
    }
}

@Composable
private fun x5DownloadCodeHint(code: Int?): String = when (code) {
    null -> stringResource(R.string.loc_x5_sdk_not_callback)
    100 -> stringResource(R.string.loc_x5_download_success)
    101 -> stringResource(R.string.loc_x5_network_unavailable)
    111 -> stringResource(R.string.loc_x5_download_wifi_required)
    127 -> stringResource(R.string.loc_x5_download_limit)
    133 -> stringResource(R.string.loc_x5_download_in_progress)
    -122 -> stringResource(R.string.loc_x5_download_conditions)
    -134 -> stringResource(R.string.loc_x5_download_bandwidth_cancelled)
    else -> stringResource(R.string.loc_x5_download_unknown)
}

@Composable
private fun x5InstallCodeHint(code: Int?): String = when (code) {
    null -> "SDK 尚未回调"
    200 -> stringResource(R.string.loc_x5_install_success)
    243 -> stringResource(R.string.loc_x5_install_preinitializing)
    else -> stringResource(R.string.loc_x5_install_unknown)
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
