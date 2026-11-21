package com.airdrophunter.app

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.airdrophunter.app.data.AirdropSite
import com.airdrophunter.app.data.AirdropSiteStore
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.GlobalSyncScheduler
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AppTextField
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.appPressFeedback
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSources: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenGlobalSearch: () -> Unit,
    onOpenMarketDetail: (String) -> Unit = {},
    onOpenProjects: () -> Unit,
    onOpenAirdropCampaigns: () -> Unit = {},
    onOpenTronEnergy: () -> Unit = {},
    onOpenAccounting: () -> Unit = {},
    onOpenTodo: () -> Unit = {},
    onOpenAirdropLibrary: () -> Unit = {},
    isGuest: Boolean = true,
    onRequireAuth: () -> Unit = {},
    sessionScopeKey: String = "guest",
    vm: HomeViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val homeViewModel: HomeViewModel = vm ?: viewModel(
        factory = HomeViewModelFactory(application.appContainer)
    )
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.marketError) {
        if (state.marketError?.startsWith("刚刚刷新过") == true) {
            android.widget.Toast.makeText(context, state.marketError, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val scope = rememberCoroutineScope()
    val defaultMarketSymbols = remember { listOf("BTC", "ETH", "SOL", "BNB", "TRX") }
    var marketSymbols by remember { mutableStateOf(defaultMarketSymbols) }
    var marketSymbolDraft by remember { mutableStateOf(defaultMarketSymbols.toSet()) }
    var showMarketEditor by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showNavOverlay by remember { mutableStateOf(false) }
    var siteSearchQuery by remember { mutableStateOf("") }
    val navSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sites by remember { mutableStateOf(AirdropSiteStore.defaults) }
    var dashboardProjects by remember { mutableStateOf<List<com.airdrophunter.app.data.ProjectDto>>(emptyList()) }
    var dashboardWalletCount by remember { mutableIntStateOf(0) }
    var dashboardGasSpend by remember { mutableDoubleStateOf(0.0) }
    var dashboardActualIncome by remember { mutableDoubleStateOf(0.0) }
    var dashboardRecentLedger by remember {
        mutableStateOf<List<com.airdrophunter.app.data.PersonalLedgerWriteRequest>>(emptyList())
    }

    LaunchedEffect(Unit) {
        marketSymbols = runCatching { CacheStore.readMarketDashboardSymbols() }
            .getOrDefault(defaultMarketSymbols)
            .filter { it in setOf("BTC", "ETH", "SOL", "BNB", "TRX", "XRP") }
            .ifEmpty { defaultMarketSymbols }
    }

    LaunchedEffect(isGuest, sessionScopeKey) {
        // Keep dashboard summaries cache-first; this UI pass does not add new sync behavior.
        GlobalSyncScheduler.enqueueDynamicCatalogWarmup(context)
        val snapshot = withContext(Dispatchers.IO) {
            val projects = runCatching {
                application.appContainer.projectsRepository.readCachedProjects(isGuest)
            }.getOrDefault(emptyList())
            val wallets = runCatching {
                application.appContainer.walletRepository.loadCached(isGuest).wallets
            }.getOrDefault(emptyList())
            val ledger = runCatching {
                application.appContainer.accountingRepository.loadPersonalLedger()
            }.getOrDefault(emptyList())
            Triple(projects, wallets.size, ledger)
        }
        dashboardProjects = snapshot.first
        dashboardWalletCount = snapshot.second
        val actualSummary = com.airdrophunter.app.data.summarizePersonalLedgerRows(snapshot.third)
        dashboardGasSpend = actualSummary.actualGasUsd
        dashboardActualIncome = actualSummary.valuedActualRewardUsd
        dashboardRecentLedger = snapshot.third.take(2)
        sites = withContext(Dispatchers.IO) { AirdropSiteStore.load() }
        homeViewModel.load(isGuest)
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(AppDesignTokens.Spacing.Xxl.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.home_screen_title), modifier = Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.home_screen_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.airdrophunter.app.ui.components.AppIconButton(
                    icon = Icons.Default.NotificationsNone,
                    contentDescription = stringResource(R.string.home_notifications),
                    onClick = { showNotifications = true }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.home_overview), color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.home_saved_data_summary), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = onOpenAccounting) { Text(stringResource(R.string.home_view_ledger)) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeDashboardMetric(
                            title = stringResource(R.string.home_active_projects),
                            value = dashboardProjects.count { it.status != "completed" }.toString(),
                            unit = stringResource(R.string.home_items_unit),
                            modifier = Modifier.weight(1f)
                        )
                        HomeDashboardMetric(
                            title = stringResource(R.string.home_wallets_added),
                            value = dashboardWalletCount.toString(),
                            unit = stringResource(R.string.home_items_unit),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeDashboardMetric(
                            title = stringResource(R.string.home_total_actual_spend),
                            value = formatHomeUsd(dashboardGasSpend),
                            modifier = Modifier.weight(1f)
                        )
                        HomeDashboardMetric(
                            title = stringResource(R.string.home_actual_earnings),
                            value = formatHomeUsd(dashboardActualIncome),
                            modifier = Modifier.weight(1f),
                            valueColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        stringResource(R.string.home_wallet_valuation_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.home_todo), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.home_pending), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.home_todo_coming_soon), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onOpenTodo) {
                            Text(stringResource(R.string.home_more_todos))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_airdrop_activity), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onOpenAirdropLibrary) { Text(stringResource(R.string.home_view_all)) }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    if (dashboardProjects.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.home_no_projects), color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.home_explore_projects), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        dashboardProjects.take(2).forEachIndexed { index, project ->
                            Row(
                                Modifier.fillMaxWidth().appPressFeedback(onClick = onOpenAirdropLibrary, scale = 1f)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(project.name, color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        when (project.status) {
                                            "completed" -> stringResource(R.string.home_status_completed)
                                            "upcoming" -> stringResource(R.string.home_status_upcoming)
                                            else -> stringResource(R.string.home_status_in_progress)
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                            if (index != dashboardProjects.take(2).lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().appPressFeedback(onClick = onOpenWallet, scale = 1f),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.home_wallet_overview), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.home_wallet_count_summary, dashboardWalletCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().appPressFeedback(onClick = onOpenAccounting, scale = 1f),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.home_recent_ledger), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.home_view_all), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    if (dashboardRecentLedger.isEmpty()) {
                        Text(stringResource(R.string.home_no_ledger), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        dashboardRecentLedger.forEachIndexed { index, entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(entry.project_name.ifBlank { entry.interaction_method },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.home_ledger_entry_summary, formatHomeUsd(entry.actual_gas_cost_usd), formatHomeUsd(entry.actual_reward_usd)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(entry.occurred_at.substringBefore('T'),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            if (index != dashboardRecentLedger.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_market_watch), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                AppTextButton(onClick = {
                    marketSymbolDraft = marketSymbols.toSet()
                    showMarketEditor = true
                }) { Text(stringResource(R.string.home_edit), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                AppTextButton(
                    onClick = { homeViewModel.refresh(isGuest) },
                    enabled = !state.isRefreshing,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (state.isRefreshing) stringResource(R.string.home_updating) else stringResource(R.string.home_refresh),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AppDesignTokens.Radius.Full.dp))
                    .appPressFeedback(onClick = onOpenGlobalSearch, scale = 1f),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.home_search_tokens), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                when {
                    state.isLoading && state.market.isEmpty() -> Box(
                        Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                    state.market.isEmpty() -> Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.home_market_unavailable_title), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(state.marketError ?: stringResource(R.string.home_check_connection),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        AppTextButton(onClick = { homeViewModel.refresh(isGuest) }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.home_retry), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    else -> Column {
                        val visibleMarket = state.market.filter { ticker ->
                            ticker.symbol.substringBefore("-").uppercase(Locale.US) in marketSymbols
                        }
                        if (visibleMarket.isEmpty()) {
                            Text(stringResource(R.string.home_no_token_data), modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                        } else visibleMarket.forEachIndexed { index, ticker ->
                            MarketRow(ticker) { symbol -> onOpenMarketDetail(symbol) }
                            if (index != visibleMarket.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            Text(
                when {
                    state.isRefreshing && state.lastSuccessfulUpdateAt != null -> stringResource(R.string.home_market_updating_cached)
                    state.marketError?.startsWith("刚刚刷新过") == true -> stringResource(R.string.home_market_recently_refreshed)
                    state.marketError != null && state.lastSuccessfulUpdateAt != null -> stringResource(R.string.home_market_update_failed_cached)
                    state.marketCacheStatus == com.airdrophunter.app.data.CacheDisplayStatus.STALE && state.lastSuccessfulUpdateAt != null ->
                        stringResource(R.string.home_cached_updated_at, java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(state.lastSuccessfulUpdateAt ?: 0L)))
                    state.marketCacheStatus == com.airdrophunter.app.data.CacheDisplayStatus.REFRESHING && state.lastSuccessfulUpdateAt != null ->
                        stringResource(R.string.home_market_updating_cached)
                    state.lastSuccessfulUpdateAt != null -> stringResource(R.string.home_updated_at, java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(state.lastSuccessfulUpdateAt ?: 0L)))
                    state.marketError != null -> stringResource(R.string.home_market_temporarily_unavailable)
                    else -> stringResource(R.string.home_waiting_market_data)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (showMarketEditor) {
            AlertDialog(
                onDismissRequest = { showMarketEditor = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(stringResource(R.string.home_customize_market), color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.home_customize_market_hint), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        listOf("BTC", "ETH", "SOL", "BNB", "TRX", "XRP").forEach { symbol ->
                            Row(Modifier.fillMaxWidth().appPressFeedback(onClick = {
                                marketSymbolDraft = if (symbol in marketSymbolDraft) marketSymbolDraft - symbol
                                    else marketSymbolDraft + symbol
                            }, scale = 1f).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = symbol in marketSymbolDraft, onCheckedChange = { checked ->
                                    marketSymbolDraft = if (checked) marketSymbolDraft + symbol else marketSymbolDraft - symbol
                                })
                                Text(symbol, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                confirmButton = {
                    AppTextButton(enabled = marketSymbolDraft.isNotEmpty(), onClick = {
                        val saved = listOf("BTC", "ETH", "SOL", "BNB", "TRX", "XRP").filter { it in marketSymbolDraft }
                        marketSymbols = saved
                        showMarketEditor = false
                        scope.launch { runCatching { CacheStore.saveMarketDashboardSymbols(saved) } }
                    }) { Text(stringResource(R.string.home_save)) }
                },
                dismissButton = {
                    AppTextButton(onClick = {
                        marketSymbolDraft = defaultMarketSymbols.toSet()
                        marketSymbols = defaultMarketSymbols
                        showMarketEditor = false
                        scope.launch { runCatching { CacheStore.saveMarketDashboardSymbols(defaultMarketSymbols) } }
                    }) { Text(stringResource(R.string.home_restore_defaults)) }
                }
            )
        }

        Spacer(Modifier.height(8.dp))
    }
    }

    if (showNavOverlay) {
        ModalBottomSheet(
            onDismissRequest = {
                showNavOverlay = false
                siteSearchQuery = ""
            },
            sheetState = navSheetState,
            shape = RoundedCornerShape(
                topStart = AppDesignTokens.Radius.Xl.dp,
                topEnd = AppDesignTokens.Radius.Xl.dp
            ),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
            dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp) }
        ) {
            val filteredSites = remember(sites, siteSearchQuery) {
                sites.filter {
                    it.name.contains(siteSearchQuery, ignoreCase = true) ||
                        it.domain.contains(siteSearchQuery, ignoreCase = true)
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 720.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(R.string.home_airdrop_directory),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.home_choose_platform_summary, sites.size), 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                AppTextField(
                    value = siteSearchQuery,
                    onValueChange = { siteSearchQuery = it },
                    placeholder = stringResource(R.string.home_search_platforms),
                    trailingClear = true
                )
                if (filteredSites.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (sites.isEmpty()) stringResource(R.string.home_no_platforms) else stringResource(R.string.home_no_matching_platforms),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredSites, key = { it.id }) { site ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(AppDesignTokens.Radius.Md.dp))
                                    .appPressFeedback(
                                        onClick = {
                                            scope.launch {
                                                navSheetState.hide()
                                                showNavOverlay = false
                                                siteSearchQuery = ""
                                                val updated = withContext(Dispatchers.IO) {
                                                    AirdropSiteStore.recordClick(site)
                                                }
                                                sites = updated
                                                val walletRows = withContext(Dispatchers.IO) {
                                                    val raw = if (isGuest) CacheStore.readLocal("wallets") else CacheStore.read("wallets")
                                                    val type = object : TypeToken<List<WalletDto>>() {}.type
                                                    raw?.let { runCatching { Gson().fromJson<List<WalletDto>>(it, type) }.getOrNull() }.orEmpty()
                                                }
                                                val intent = Intent(context, X5BrowserActivity::class.java)
                                                    .putExtra(BrowserActivity.EXTRA_URL, site.url)
                                                    .putExtra(BrowserActivity.EXTRA_TITLE, site.name)
                                                    .putExtra(BrowserActivity.EXTRA_WALLETS_JSON, Gson().toJson(walletRows))
                                                walletRows.firstOrNull()?.let {
                                                    intent.putExtra(BrowserActivity.EXTRA_SELECTED_WALLET_ID, it.id)
                                                }
                                                // Airdrop sites are browsing-only X5 pages. The X5 page
                                                // forwards an explicit wallet-connect action to the
                                                // existing system-WebView wallet flow.
                                                context.startActivity(intent)
                                            }
                                        },
                                        scale = 0.94f
                                    )
                                     .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(52.dp),
                                    shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    FaviconTile(site, Modifier.fillMaxSize().padding(8.dp))
                                }
                                Text(
                                    site.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotifications) AlertDialog(
        onDismissRequest = { showNotifications = false }, containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.home_notifications_title), color = MaterialTheme.colorScheme.onSurface) },
        text = { Text(stringResource(R.string.home_notifications_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { AppTextButton(onClick = { showNotifications = false }) { Text(stringResource(R.string.home_close)) } }
    )
}

@Composable
private fun HomeDashboardMetric(
    title: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = valueColor, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (unit != null) Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatHomeUsd(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.US).format(value)

@Composable
private fun FaviconTile(site: AirdropSite, modifier: Modifier = Modifier) {
    val primaryUrl = "https://www.google.com/s2/favicons?domain=${site.domain}&sz=128"
    val fallbackUrl = "https://icons.duckduckgo.com/ip3/${site.domain}.ico"
    var useFallback by remember(site.id) { mutableStateOf(false) }
    SubcomposeAsyncImage(
        model = if (useFallback) fallbackUrl else primaryUrl,
        contentDescription = site.name,
        modifier = modifier.clip(RoundedCornerShape(AppDesignTokens.Radius.Sm.dp)),
        contentScale = ContentScale.Fit,
        loading = { FaviconLetter(site) },
        error = {
            if (!useFallback) {
                LaunchedEffect(site.id) { useFallback = true }
                FaviconLetter(site)
            } else FaviconLetter(site)
        }
    )
}

@Composable
private fun FaviconLetter(site: AirdropSite) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(AppDesignTokens.Radius.Sm.dp)),
        contentAlignment = Alignment.Center) {
        Text(site.name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MarketRow(ticker: com.airdrophunter.app.data.OkxTicker, onClick: (String) -> Unit) {
    // Keep small-priced assets readable; two decimals can otherwise render as $ 0.00.
    val price = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 8
    }.format(ticker.lastPrice)
    val change = String.format(Locale.US, "%+.2f%%", ticker.change24hPercent)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .appPressFeedback(onClick = { onClick(ticker.symbol.substringBefore("-").uppercase(Locale.US)) }, scale = 1f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ticker.symbol, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                ticker.displayName,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "$ $price",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            Text(change, color = if (ticker.change24hPercent >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}


@Composable
private fun ShortcutCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.wrapContentHeight()
            .appPressFeedback(onClick = onClick, scale = 0.96f),
        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}


@Composable
private fun DashboardIconTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    iconColor: Color,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(AppDesignTokens.Radius.Md.dp)).background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(size * 0.52f))
    }
}


private data class HomeDashboardCard(val id: String)

/**
 * Developer-owned visual specification. User preferences persist card IDs/order only;
 * they cannot change width, height, span, or other layout dimensions.
 * Add new card types here when new Home features are introduced.
 */
private data class HomeDashboardCardSpec(
    val widthFraction: Float,
    val heightDp: Int
)

private object HomeDashboardLayoutStore {
    private val cardSpecs = mapOf(
        "airdrop_navigation" to HomeDashboardCardSpec(widthFraction = 1f, heightDp = 76),
        "wallet_management" to HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 120),
        "tron_energy" to HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 104),
        "accounting" to HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 104),
        "project_library" to HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 104)
    )

    fun layout(id: String): HomeDashboardCardSpec =
        cardSpecs[id] ?: HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 104)

    val defaults = listOf(
        HomeDashboardCard("airdrop_navigation"),
        HomeDashboardCard("accounting"),
        HomeDashboardCard("wallet_management"),
        HomeDashboardCard("tron_energy"),
    )
    private val supported = listOf("airdrop_navigation", "accounting", "wallet_management", "tron_energy", "project_library")
    fun title(id: String) = when (id) {
        "airdrop_navigation" -> stringResource(R.string.home_airdrop_directory)
        "accounting" -> "记账"
        "wallet_management" -> "钱包管理"
        "tron_energy" -> "TRON 能量租赁"
        "project_library" -> "库管理"
        else -> "快捷入口"
    }
    fun subtitle(id: String) = when (id) {
        "airdrop_navigation" -> "常用活动网站"
        "accounting" -> "记录Gas与收益"
        "wallet_management" -> "管理已保存地址"
        "tron_energy" -> "租赁前核对转出地址"
        "project_library" -> "管理个人项目与任务"
        else -> ""
    }
    fun available(selected: Set<String>) = supported.filterNot { it in selected }
    fun load(context: android.content.Context): List<HomeDashboardCard> {
        val preferences = context.getSharedPreferences("home_dashboard_layout", android.content.Context.MODE_PRIVATE)
        val raw = preferences.getString("cards", null) ?: return defaults
        val type = object : TypeToken<List<HomeDashboardCard>>() {}.type
        val parsed = runCatching { Gson().fromJson<List<HomeDashboardCard>>(raw, type) }.getOrNull() ?: return defaults
        val valid = parsed.filter { it.id in supported }.distinctBy { it.id }
            .map { HomeDashboardCard(it.id) }.toMutableList()
        // v4 removes the duplicate Home shortcut; the bottom navigation Airdrop Library is the sole entry.
        if (preferences.getInt("layout_version", 0) < 4) {
            valid.removeAll { it.id == "binance_official" || it.id == "airdrop_library" }
            preferences.edit().putInt("layout_version", 4).apply()
        }
        // Keep the accounting shortcut visible for users upgrading from older layouts.
        if (valid.none { it.id == "accounting" }) valid.add(minOf(1, valid.size), HomeDashboardCard("accounting"))
        return valid
    }
    fun save(context: android.content.Context, cards: List<HomeDashboardCard>) {
        val valid = cards.filter { it.id in supported }.distinctBy { it.id }
        context.getSharedPreferences("home_dashboard_layout", android.content.Context.MODE_PRIVATE)
            .edit().putString("cards", Gson().toJson(valid)).putInt("layout_version", 4).apply()
    }
}
