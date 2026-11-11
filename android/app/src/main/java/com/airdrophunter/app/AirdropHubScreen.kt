package com.airdrophunter.app

import android.content.Intent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.AirdropHubDetailDto
import com.airdrophunter.app.data.AirdropHubDto
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.ui.theme.AppColors
import java.text.DateFormat
import java.util.Date

@Composable
fun AirdropHubScreen(
    modifier: Modifier = Modifier,
    vm: AirdropHubViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val hubViewModel: AirdropHubViewModel = vm ?: viewModel(
        factory = AirdropHubViewModelFactory(application.appContainer.airdropHubRepository)
    )
    val state by hubViewModel.uiState.collectAsStateWithLifecycle()
    var selectedAnnouncement by remember { mutableStateOf<AirdropHubDto?>(null) }

    if (selectedAnnouncement != null) {
        AirdropAnnouncementDetail(
            item = selectedAnnouncement!!,
            onBack = { selectedAnnouncement = null }
        )
        return
    }

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "查看最新官方空投公告",
                Modifier.weight(1f),
                color = AppColors.TextHelper,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = hubViewModel::refresh, enabled = !state.isRefreshing) {
                Text(if (state.isRefreshing) "刷新中…" else "刷新", color = AppColors.Primary)
            }
        }

        when {
            state.isLoading && state.projects.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AppColors.Primary) }

            state.error != null && state.projects.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("暂时无法加载空投公告", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("请检查网络后重试。", color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = hubViewModel::refresh, enabled = !state.isRefreshing,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                    Text("重新加载")
                }
            }

            state.projects.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center
            ) {
                Text("暂时没有新的空投公告", color = AppColors.TextHelper)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("${state.projects.size} 条空投公告",
                        color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                items(state.projects, key = { it.id }) { item ->
                    Card(
                        onClick = { selectedAnnouncement = item },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        border = BorderStroke(1.dp, AppColors.Border),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, Modifier.weight(1f), color = AppColors.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold)
                                Surface(color = AppColors.TealTint, shape = RoundedCornerShape(999.dp)) {
                                    Text("Binance", Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        color = AppColors.Primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text("点击查看公告完整内容",
                                color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Text("查看详情 →", color = AppColors.Primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AirdropAnnouncementDetail(
    item: AirdropHubDto,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var detail by remember(item.id) { mutableStateOf<AirdropHubDetailDto?>(null) }
    var loading by remember(item.id) { mutableStateOf(true) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id) {
        loading = true
        error = null
        try {
            detail = ApiClient.service.getAirdropHubDetail(item.id)
        } catch (_: Exception) {
            error = "公告正文暂时加载失败，请重试或打开币安原文。"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回列表", tint = AppColors.TextPrimary)
            }
            Text("公告详情", color = AppColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(
            item.name,
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Binance 官方公告原文",
            Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
            color = AppColors.TextHelper,
            style = MaterialTheme.typography.labelSmall
        )

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            detail == null -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error ?: "公告正文暂不可用", color = AppColors.TextSecondary)
                OutlinedButton(onClick = {
                    loading = true
                    error = null
                }) { Text("重新加载") }
            }
            else -> {
                detail!!.release_date?.let { timestamp ->
                    Text(
                        DateFormat.getDateTimeInstance().format(Date(timestamp)),
                        Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        color = AppColors.TextHelper,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                key(detail!!.content_html) {
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { viewContext ->
                            WebView(viewContext).apply {
                                settings.javaScriptEnabled = false
                                settings.domStorageEnabled = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                settings.setSupportMultipleWindows(false)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: android.webkit.WebResourceRequest
                                    ): Boolean = !request.url.scheme.equals("https", ignoreCase = true)
                                }
                                loadDataWithBaseURL(
                                    "https://www.binance.com",
                                    announcementHtmlDocument(detail!!.content_html),
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        },
                        update = {}
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("返回列表")
            }
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(context, BrowserActivity::class.java)
                                .putExtra(BrowserActivity.EXTRA_URL, item.link)
                                .putExtra(BrowserActivity.EXTRA_TITLE, item.name)
                        )
                    }.onFailure {
                        Toast.makeText(context, "无法打开 Binance 原文", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Text("在币安打开原文")
            }
        }
    }
}

private fun announcementHtmlDocument(content: String): String = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: data:; style-src 'unsafe-inline' https:; font-src https: data:; script-src 'none'; connect-src 'none'; frame-src 'none';">
<style>
body { color: #20242b; background: #ffffff; font-family: sans-serif; font-size: 15px; line-height: 1.65; overflow-wrap: anywhere; }
img, video { max-width: 100%; height: auto; }
table { max-width: 100%; border-collapse: collapse; display: block; overflow-x: auto; }
a { color: #1677ff; }
</style>
</head>
<body>$content</body>
</html>
""".trimIndent()
