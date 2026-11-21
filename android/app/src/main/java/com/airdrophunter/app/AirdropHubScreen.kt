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
                Text(context.getString(R.string.loc_hub_official_announcements), modifier = Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(context.getString(R.string.loc_hub_latest_official_airdrops), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall)
            }
            AppTextButton(onClick = hubViewModel::refresh, enabled = !state.isRefreshing) {
                Text(if (state.isRefreshing) context.getString(R.string.loc_hub_refreshing) else context.getString(R.string.loc_hub_refresh), color = MaterialTheme.colorScheme.primary)
            }
        }

        Column(Modifier.weight(1f)) {
            if (state.sourceStatuses.isNotEmpty() || state.sourceStatusError != null || state.sourceError != null) {
                AirdropSourceHealthPanel(
                    statuses = state.sourceStatuses,
                    error = state.sourceError ?: state.sourceStatusError,
                    expanded = showSourceDetails,
                    onToggle = { showSourceDetails = !showSourceDetails },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                Text(context.getString(R.string.loc_hub_announcement_load_failed), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(context.getString(R.string.loc_hub_check_network_retry), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                AppButton(context.getString(R.string.loc_hub_reload), onClick = hubViewModel::refresh, enabled = !state.isRefreshing)
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
                        Text(context.getString(R.string.loc_hub_no_new_announcements), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(context.getString(R.string.loc_hub_announcement_empty_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        AppButton(context.getString(R.string.loc_hub_refresh_announcements), onClick = hubViewModel::refresh, enabled = !state.isRefreshing, fullWidth = false)
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(context.getString(R.string.loc_hub_announcement_count, state.projects.size),
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
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    item.name,
                                    Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
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
                                    Text(context.getString(R.string.loc_hub_announcement_time, formattedDate),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(context.getString(R.string.loc_hub_open_full_announcement),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Text(context.getString(R.string.loc_hub_view_details), color = MaterialTheme.colorScheme.primary,
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
            error = context.getString(R.string.loc_hub_announcement_body_failed)
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
                contentDescription = context.getString(R.string.loc_hub_back_to_list),
                onClick = onBack
            )
            Text(context.getString(R.string.loc_hub_announcement_details), color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(
            detail?.title ?: item.name,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            context.getString(R.string.loc_hub_binance_official_original),
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
                Text(error ?: context.getString(R.string.loc_hub_announcement_body_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppButton(context.getString(R.string.loc_hub_reload), onClick = { reloadKey += 1 }, style = AppButtonStyle.SECONDARY)
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
                                                    .putExtra(BrowserActivity.EXTRA_TITLE, context.getString(R.string.loc_hub_announcement_link))
                                            )
                                        }.fold(
                                            onSuccess = { true },
                                            onFailure = {
                                                Toast.makeText(viewContext, context.getString(R.string.loc_hub_cannot_open_announcement), Toast.LENGTH_SHORT).show()
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
                text = context.getString(R.string.loc_hub_back_to_list),
                onClick = onBack,
                modifier = Modifier.weight(1f),
                style = AppButtonStyle.SECONDARY
            )
            AppButton(
                text = context.getString(R.string.loc_hub_open_binance_original),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(context, BrowserActivity::class.java)
                                .putExtra(BrowserActivity.EXTRA_URL, detail?.original_url ?: item.link)
                                .putExtra(BrowserActivity.EXTRA_TITLE, item.name)
                        )
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.loc_hub_cannot_open_binance), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

