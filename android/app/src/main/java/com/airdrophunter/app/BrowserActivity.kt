package com.airdrophunter.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import com.reown.appkit.ui.appKitGraph
import com.reown.appkit.ui.openAppKit
import androidx.compose.foundation.background
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
                BrowserScreen(site.ifBlank { Uri.parse(url).host.orEmpty() }, url, wallets, selectedId) { finish() }
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
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalMaterialNavigationApi::class)
@Composable
private fun BrowserScreen(
    title: String,
    url: String,
    wallets: List<BrowserWallet>,
    selectedWalletId: String,
    onExit: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var browser by remember { mutableStateOf<WebView?>(null) }
    var selectedWallet by remember(wallets, selectedWalletId) {
        mutableStateOf(wallets.firstOrNull { it.id == selectedWalletId } ?: wallets.firstOrNull())
    }
    var showWalletPicker by remember { mutableStateOf(false) }
    var pendingSslHandler by remember { mutableStateOf<SslErrorHandler?>(null) }
    var pendingSslMessage by remember { mutableStateOf<String?>(null) }
    BackHandler { val web = browser; if (web != null && web.canGoBack()) web.goBack() else onExit() }

    DisposableEffect(browser) {
        onDispose { browser?.apply { stopLoading(); loadUrl("about:blank"); removeAllViews(); destroy() } }
    }

    val bottomSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden, skipHalfExpanded = true)
    val bottomSheetNavigator = remember(bottomSheetState) { BottomSheetNavigator(bottomSheetState) }
    val walletNavController = rememberNavController(bottomSheetNavigator)

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
                    Text("⚠️ 安全提醒：本 App 不保存您的私钥。交易签名将在外部钱包 App 中完成，请仔细核对。",
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().background(Color.White)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        browser = this
                        setBackgroundColor(AndroidColor.WHITE)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadsImagesAutomatically = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                            allowFileAccess = false
                            allowContentAccess = false
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            safeBrowsingEnabled = true
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url ?: return true
                                return !target.scheme.equals("https", true) || target.host.isNullOrBlank() || target.userInfo != null
                            }
                            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                                Log.d("AirdropBrowser", "Page started: $pageUrl")
                            }

                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                Log.d("AirdropBrowser", "Page finished: $pageUrl")
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
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

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                                if (handler == null) return
                                pendingSslHandler?.cancel()
                                pendingSslHandler = handler
                                pendingSslMessage = "网站证书验证失败（${error?.primaryError ?: "未知错误"}）。继续访问可能导致账号、密码或钱包信息泄露。仅在确认网址可信且不输入敏感信息时继续。"
                                Log.w("AirdropBrowser", "SSL certificate error: $error")
                            }
                        }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White),
                update = { view -> if (browser !== view) browser = view }
            )
            BrowserAccountingToolbar(
                walletAddress = selectedWallet?.address ?: "MOCK-WALLET-LOCAL",
                onCopyAddress = {
                    val address = selectedWallet?.address
                    if (address.isNullOrBlank()) Toast.makeText(context, "当前没有可复制的钱包地址", Toast.LENGTH_SHORT).show()
                    else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Wallet address", address))
                        Toast.makeText(context, "钱包地址已复制", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    if (pendingSslMessage != null) {
        AlertDialog(
            onDismissRequest = {
                pendingSslHandler?.cancel()
                pendingSslHandler = null
                pendingSslMessage = null
            },
            title = { Text("网站证书不安全", color = AppColors.TextPrimary) },
            text = { Text(pendingSslMessage.orEmpty(), color = AppColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    pendingSslHandler?.proceed()
                    pendingSslHandler = null
                    pendingSslMessage = null
                }) { Text("仍然继续（不安全）", color = Color(0xFFB42318)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingSslHandler?.cancel()
                    pendingSslHandler = null
                    pendingSslMessage = null
                }) { Text("取消并返回") }
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
