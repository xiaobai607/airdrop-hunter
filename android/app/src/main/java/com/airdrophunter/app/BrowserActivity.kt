package com.airdrophunter.app

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

class BrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val site = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (!validHttps(url)) { finish(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.rgb(11, 14, 17)
        window.navigationBarColor = AndroidColor.rgb(8, 11, 15)
        setContent { AirdropHunterTheme { BrowserScreen(site.ifBlank { Uri.parse(url).host.orEmpty() }, url) { finish() } } }
    }
    private fun validHttps(value: String): Boolean = try {
        val uri = Uri.parse(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }
    companion object {
        const val EXTRA_URL = "com.airdrophunter.app.extra.BROWSER_URL"
        const val EXTRA_TITLE = "com.airdrophunter.app.extra.BROWSER_TITLE"
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(title: String, url: String, onExit: () -> Unit) {
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background, titleContentColor = AppColors.TextPrimary, navigationIconContentColor = AppColors.TextPrimary)
            )
            Surface(Modifier.fillMaxWidth(), color = Color(0xFF7F1D1D)) {
                Box(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.browser_safety), color = Color(0xFFFFE4E6), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
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
                }
                loadUrl(url)
            }
        }, modifier = Modifier.fillMaxSize().padding(padding), update = { view -> if (browser !== view) browser = view })
    }
}
