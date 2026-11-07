package com.airdrophunter.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
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
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val site = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (!validHttps(url)) { finish(); return }
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

    private fun validHttps(value: String): Boolean = try {
        val uri = Uri.parse(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }

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
        const val EXTRA_URL = "com.airdrophunter.app.extra.BROWSER_URL"
        const val EXTRA_TITLE = "com.airdrophunter.app.extra.BROWSER_TITLE"
        const val EXTRA_WALLETS_JSON = "com.airdrophunter.app.extra.BROWSER_WALLETS_JSON"
        const val EXTRA_SELECTED_WALLET_ID = "com.airdrophunter.app.extra.BROWSER_SELECTED_WALLET_ID"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    BackHandler { val web = browser; if (web != null && web.canGoBack()) web.goBack() else onExit() }

    DisposableEffect(browser) {
        onDispose { browser?.apply { stopLoading(); loadUrl("about:blank"); removeAllViews(); destroy() } }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppColors.Background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.Default.ArrowBack, "返回", tint = AppColors.TextPrimary) } },
                    actions = {
                        if (selectedWallet != null) TextButton(onClick = { showWalletPicker = true }) {
                            Text("钱包 · ${selectedWallet!!.walletName.ifBlank { selectedWallet!!.chain }}", color = AppColors.Primary, maxLines = 1)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
                )
                Surface(Modifier.fillMaxWidth(), color = Color(0xFF9F2525)) {
                    Text("安全提醒：请核对当前网站域名；本 App 不会连接或签署钱包交易",
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        browser = this
                        setBackgroundColor(AndroidColor.WHITE)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                            allowFileAccess = false
                            allowContentAccess = false
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            safeBrowsingEnabled = true
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url ?: return true
                                return !target.scheme.equals("https", true) || target.host.isNullOrBlank() || target.userInfo != null
                            }
                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                                handler?.cancel()
                            }
                        }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
