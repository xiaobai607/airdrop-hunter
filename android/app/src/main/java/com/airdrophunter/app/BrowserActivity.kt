package com.airdrophunter.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * In-app browser for third-party websites.
 * Only HTTPS pages are loaded; local file access and mixed content are disabled.
 */
class BrowserActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val pageTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        if (!isAllowedWebUrl(initialUrl)) {
            Toast.makeText(this, "仅支持安全的 HTTPS 网站地址", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 14, 17))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val warning = TextView(this).apply {
            text = "安全提示：本应用绝不索要私钥，请勿在网页中输入助记词！"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(176, 35, 45))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 14, 16, 14)
            contentDescription = "重要安全警告"
        }
        root.addView(
            warning,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val heading = TextView(this).apply {
            text = if (pageTitle.isBlank()) Uri.parse(initialUrl).host.orEmpty() else pageTitle
            setTextColor(Color.rgb(245, 247, 250))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(16, 12, 16, 12)
        }
        root.addView(
            heading,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(11, 14, 17))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeBrowsingEnabled = true
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = !isAllowedWebUrl(request?.url?.toString().orEmpty())

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                !isAllowedWebUrl(url.orEmpty())

            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                handler?.cancel()
            }
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
        webView.loadUrl(initialUrl)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun isAllowedWebUrl(value: String): Boolean = try {
        val uri = Uri.parse(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    } catch (_: Exception) {
        false
    }

    companion object {
        const val EXTRA_URL = "com.airdrophunter.app.extra.BROWSER_URL"
        const val EXTRA_TITLE = "com.airdrophunter.app.extra.BROWSER_TITLE"
    }
}
