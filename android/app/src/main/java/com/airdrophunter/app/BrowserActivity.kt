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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.airdrophunter.app.ui.theme.AirdropHunterTheme
import com.airdrophunter.app.ui.theme.AppColors
import org.json.JSONObject

class BrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val site = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val prefillAddress = intent.getStringExtra(EXTRA_PREFILL_ADDRESS).orEmpty()
        if (!validHttps(url)) { finish(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.rgb(248, 249, 250)
        window.navigationBarColor = AndroidColor.rgb(248, 249, 250)
        setContent { AirdropHunterTheme { BrowserScreen(site.ifBlank { Uri.parse(url).host.orEmpty() }, url, prefillAddress) { finish() } } }
    }
    private fun validHttps(value: String): Boolean = try {
        val uri = Uri.parse(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }
    companion object {
        const val EXTRA_URL = "com.airdrophunter.app.extra.BROWSER_URL"
        const val EXTRA_TITLE = "com.airdrophunter.app.extra.BROWSER_TITLE"
        const val EXTRA_PREFILL_ADDRESS = "com.airdrophunter.app.extra.PREFILL_ADDRESS"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(title: String, url: String, prefillAddress: String, onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var browser by remember { mutableStateOf<WebView?>(null) }
    BackHandler { val w = browser; if (w != null && w.canGoBack()) w.goBack() else onExit() }
    DisposableEffect(browser) {
        onDispose { browser?.apply { stopLoading(); loadUrl("about:blank"); removeAllViews(); destroy() } }
    }
    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = AppColors.Background, topBar = {
        Column {
            TopAppBar(
                title = { Text(title, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AppColors.TextPrimary) } },
                actions = {
                    if (prefillAddress.isNotBlank()) IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Wallet address", prefillAddress))
                        Toast.makeText(context, "钱包地址已复制，可手动粘贴", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, "复制钱包地址", tint = AppColors.Primary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background, titleContentColor = AppColors.TextPrimary)
            )
            Surface(Modifier.fillMaxWidth(), color = Color(0xFF7F1D1D)) {
                Box(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("请确认当前网站为项目官方页面；App 不会连接或签署钱包交易。",
                        color = Color(0xFFFFE4E6), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }) { padding ->
        AndroidView(factory = { ctx ->
            WebView(ctx).apply {
                browser = this
                setBackgroundColor(AndroidColor.rgb(248, 249, 250))
                settings.apply {
                    javaScriptEnabled = true; domStorageEnabled = true
                    allowFileAccess = false; allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false; setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW; safeBrowsingEnabled = true
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val u = request?.url ?: return true
                        return !u.scheme.equals("https", true) || u.host.isNullOrBlank() || u.userInfo != null
                    }
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) { handler?.cancel() }
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                        if (prefillAddress.isNotBlank()) {
                            val addressLiteral = JSONObject.quote(prefillAddress)
                            val script = """
                                (function() {
                                  const address = $addressLiteral;
                                  const inputs = Array.from(document.querySelectorAll('input:not([type=hidden]),textarea'));
                                  const visible = inputs.filter(el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length));
                                  const target = visible.find(el => /address|wallet|account/i.test((el.name||'')+' '+(el.id||'')+' '+(el.placeholder||'')+' '+(el.getAttribute('aria-label')||''))) || visible.find(el => !['checkbox','radio','submit','button','file','password'].includes((el.type||'').toLowerCase()));
                                  if (!target) return false;
                                  const setter = Object.getOwnPropertyDescriptor(target.constructor.prototype, 'value')?.set;
                                  if (setter) setter.call(target, address); else target.value = address;
                                  target.dispatchEvent(new Event('input', {bubbles:true}));
                                  target.dispatchEvent(new Event('change', {bubbles:true}));
                                  return true;
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(script, null)
                        }
                    }
                }
                loadUrl(url)
            }
        }, modifier = Modifier.fillMaxSize().padding(padding), update = { view -> if (browser !== view) browser = view })
    }
}
