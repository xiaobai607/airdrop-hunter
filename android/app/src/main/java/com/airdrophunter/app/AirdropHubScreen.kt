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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.AirdropHubDetailDto
import com.airdrophunter.app.data.AirdropHubDto
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.appPressFeedback
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AirdropSourceHealthPanel
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.StaggeredListItem
import com.airdrophunter.app.ui.components.AppIconButton
import com.airdrophunter.app.ui.components.AppButtonStyle
import java.text.DateFormat
import java.time.Instant
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
    var showSourceDetails by remember { mutableStateOf(false) }

    if (selectedAnnouncement != null) {
        AirdropAnnouncementDetail(
            item = selectedAnnouncement!!,
            onBack = { selectedAnnouncement = null }
        )
        return
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("官方公告", modifier = Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("来自官方来源的最新空投动态", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall)
            }
            AppTextButton(onClick = hubViewModel::refresh, enabled = !state.isRefreshing) {
                Text(if (state.isRefreshing) "刷新中…" else "刷新", color = MaterialTheme.colorScheme.primary)
            }
        }

        Column(Modifier.weight(1f)) {
            if (state.sourceStatuses.isNotEmpty() || state.sourceStatusError != null || state.sourceError != null) {
                AirdropSourceHealthPanel(
                    statuses = state.sourceStatuses,
                    error = state.sourceError ?: state.sourceStatusError,
                    expanded = showSourceDetails,
                    onToggle = { showSourceDetails = !showSourceDetails },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            when {
            state.isLoading && state.projects.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            state.error != null && state.projects.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("暂时无法加载空投公告", color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("请检查网络后重试。", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                AppButton("重新加载", onClick = hubViewModel::refresh, enabled = !state.isRefreshing)
            }

            state.projects.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂时没有新的公告", color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("官方公告目录暂时没有可展示的新内容。可以稍后刷新查看。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        AppButton("刷新公告", onClick = hubViewModel::refresh, enabled = !state.isRefreshing, fullWidth = false)
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("${state.projects.size} 条空投公告",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                itemsIndexed(state.projects, key = { _, item -> item.id }) { index, item ->
                    StaggeredListItem(item.id, index) {
                    Card(
                        modifier = Modifier.fillMaxWidth().appPressFeedback(
                            onClick = { selectedAnnouncement = item }, scale = 0.96f
                        ),
                        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.name,
                                    Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                                    Text("Binance", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            item.published_at?.let { publishedAt ->
                                val formattedDate = runCatching {
                                    DateFormat.getDateTimeInstance().format(Date(Instant.parse(publishedAt).toEpochMilli()))
                                }.getOrNull()
                                if (formattedDate != null) {
                                    Text("公告时间：$formattedDate",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text("点击查看公告完整内容",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Text("查看详情 →", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
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
    var reloadKey by remember(item.id) { mutableIntStateOf(0) }

    LaunchedEffect(item.id, reloadKey) {
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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.airdrophunter.app.ui.components.AppIconButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "返回列表",
                onClick = onBack
            )
            Text("公告详情", color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(
            detail?.title ?: item.name,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Binance 官方公告原文",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelSmall
        )

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            detail == null -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error ?: "公告正文暂不可用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppButton("重新加载", onClick = { reloadKey += 1 }, style = AppButtonStyle.SECONDARY)
            }
            else -> {
                detail!!.release_date?.let { timestamp ->
                    Text(
                        DateFormat.getDateTimeInstance().format(Date(timestamp)),
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
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
                                    ): Boolean {
                                        // Only explicit top-level user clicks may leave the
                                        // source article. Block subframes and automatic redirects.
                                        if (!request.isForMainFrame || !request.hasGesture()) {
                                            return true
                                        }
                                        val target = request.url
                                        if (!target.scheme.equals("https", ignoreCase = true)) {
                                            return true
                                        }
                                        return runCatching {
                                            viewContext.startActivity(
                                                Intent(viewContext, BrowserActivity::class.java)
                                                    .putExtra(BrowserActivity.EXTRA_URL, target.toString())
                                                    .putExtra(BrowserActivity.EXTRA_TITLE, "公告链接")
                                            )
                                        }.fold(
                                            onSuccess = { true },
                                            onFailure = {
                                                Toast.makeText(viewContext, "无法打开公告链接", Toast.LENGTH_SHORT).show()
                                                true
                                            }
                                        )
                                    }
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
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppButton(
                text = "返回列表",
                onClick = onBack,
                modifier = Modifier.weight(1f),
                style = AppButtonStyle.SECONDARY
            )
            AppButton(
                text = "在币安打开原文",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(context, BrowserActivity::class.java)
                                .putExtra(BrowserActivity.EXTRA_URL, detail?.original_url ?: item.link)
                                .putExtra(BrowserActivity.EXTRA_TITLE, item.name)
                        )
                    }.onFailure {
                        Toast.makeText(context, "无法打开 Binance 原文", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

