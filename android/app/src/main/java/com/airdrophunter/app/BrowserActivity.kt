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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.data.ApiClient
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
        window.statusBarColor = AndroidColor.rgb(11, 16, 28)
        window.navigationBarColor = AndroidColor.rgb(11, 16, 28)
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
        containerColor = Color(0xFF0B101C),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.Default.ArrowBack, "返回", tint = AppColors.TextPrimary) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
                )
                Surface(Modifier.fillMaxWidth(), color = Color(0xFF7F1D1D)) {
                    Text("请确认当前网站为项目官方页面；App 不会连接或签署钱包交易",
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                        color = Color(0xFFFFE4E6), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            if (selectedWallet != null) {
                GlassBackground(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
                    blurRadius = 18.dp,
                    cornerRadius = 20.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChainIcon(selectedWallet!!.chain, selectedWallet!!.networkIcon, Modifier.size(38.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedWallet!!.walletName.ifBlank { selectedWallet!!.chain },
                                color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(shortAddress(selectedWallet!!.address), color = Color(0xFFD1D5DB),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { showWalletPicker = true }, enabled = wallets.isNotEmpty()) {
                            Text("切换钱包", color = Color.White)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
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
            modifier = Modifier.fillMaxSize().padding(padding),
            update = { view -> if (browser !== view) browser = view }
        )
        PnlAssistantOverlay(Modifier.align(Alignment.TopEnd).padding(top = 104.dp, end = 8.dp))
        }
    }

    if (showWalletPicker) {
        Dialog(onDismissRequest = { showWalletPicker = false }) {
            GlassBackground(
                modifier = Modifier.fillMaxWidth(),
                blurRadius = 22.dp,
                cornerRadius = 24.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择钱包", color = Color.White, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    if (wallets.isEmpty()) {
                        Text("尚未保存钱包地址", color = Color.LightGray)
                    } else {
                        wallets.forEach { wallet ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selectedWallet = wallet
                                    showWalletPicker = false
                                }.padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                ChainIcon(wallet.chain, wallet.networkIcon, Modifier.size(36.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(wallet.walletName.ifBlank { wallet.chain }, color = Color.White,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(shortAddress(wallet.address), color = Color(0xFFD1D5DB),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Wallet address", wallet.address))
                                        Toast.makeText(context, "已复制完整地址", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("复制")
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showWalletPicker = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("关闭", color = Color.White)
                    }
                }
            }
        }
    }
}
