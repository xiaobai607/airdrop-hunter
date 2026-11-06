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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
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
import androidx.core.view.WindowCompat
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
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val site = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (!validHttps(url)) { finish(); return }
        val wallets = parseWallets(intent.getStringExtra(EXTRA_WALLETS_JSON).orEmpty())
        val selectedId = intent.getStringExtra(EXTRA_SELECTED_WALLET_ID).orEmpty()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.rgb(248, 249, 250)
        window.navigationBarColor = AndroidColor.rgb(248, 249, 250)
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
            BrowserWallet(
                id = item.optString("id"),
                chain = item.optString("chain"),
                networkIcon = item.optString("network_icon"),
                walletName = item.optString("wallet_name"),
                address = address
            )
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
    BackHandler {
        val web = browser
        if (web != null && web.canGoBack()) web.goBack() else onExit()
    }
    DisposableEffect(browser) {
        onDispose {
            browser?.apply { stopLoading(); loadUrl("about:blank"); removeAllViews(); destroy() }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppColors.Background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(Icons.Default.ArrowBack, "返回", tint = AppColors.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.Background,
                        titleContentColor = AppColors.TextPrimary
                    )
                )
                Surface(Modifier.fillMaxWidth(), color = Color(0xFF7F1D1D)) {
                    Box(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "请确认当前网站为项目官方页面；App 不会连接或签署钱包交易。",
                            color = Color(0xFFFFE4E6),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (selectedWallet != null) {
                WalletAddressAssistBar(
                    wallets = wallets,
                    selectedWallet = selectedWallet!!,
                    onSelect = { selectedWallet = it },
                    onCopy = {
                        // One-way clipboard write only. Never read clipboard contents or observe clipboard changes.
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Wallet address", selectedWallet!!.address))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                )
            }
        }
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    browser = this
                    setBackgroundColor(AndroidColor.rgb(248, 249, 250))
                    settings.apply {
                        // JavaScript remains available for normal website functionality.
                        // No app-injected JavaScript, DOM access, or form autofill is performed.
                        javaScriptEnabled = true
                        domStorageEnabled = true
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
    }
}

@Composable
private fun WalletAddressAssistBar(
    wallets: List<BrowserWallet>,
    selectedWallet: BrowserWallet,
    onSelect: (BrowserWallet) -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.Border),
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = AppColors.Primary.copy(alpha = 0.10f), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = "钱包", tint = AppColors.Primary)
                }
            }
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = wallets.size > 1) { expanded = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NetworkBadge(selectedWallet.networkIcon, selectedWallet.chain)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${selectedWallet.chain}  ${selectedWallet.walletName}",
                            color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            shortBrowserAddress(selectedWallet.address),
                            color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    wallets.forEach { wallet ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("${wallet.chain}  ${wallet.walletName}", fontWeight = FontWeight.SemiBold)
                                    Text(shortBrowserAddress(wallet.address), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = { onSelect(wallet); expanded = false }
                        )
                    }
                }
            }
            Button(
                onClick = onCopy,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("复制地址", maxLines = 1)
            }
        }
    }
}

@Composable
private fun NetworkBadge(symbol: String, chain: String) {
    val badgeColor = when {
        symbol.equals("T", true) || chain.contains("tron", true) -> Color(0xFFDC3434)
        symbol.equals("S", true) || chain.contains("sol", true) -> Color(0xFF7B61C9)
        symbol.equals("B", true) || chain.contains("bitcoin", true) -> Color(0xFFF7931A)
        else -> Color(0xFF627EEA)
    }
    Surface(shape = CircleShape, color = badgeColor, modifier = Modifier.size(30.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol.ifBlank { chain.take(1) }.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private fun shortBrowserAddress(address: String): String =
    if (address.length > 13) address.take(6) + "…" + address.takeLast(5) else address
