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
            android.widget.Toast.makeText(context, context.getString(R.string.home_market_rate_limit), android.widget.Toast.LENGTH_SHORT).show()
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

    LaunchedEffect(Unit) {
        marketSymbols = runCatching { CacheStore.readMarketDashboardSymbols() }
            .getOrDefault(defaultMarketSymbols)
            .filter { it in setOf("BTC", "ETH", "SOL", "BNB", "TRX", "XRP") }
            .ifEmpty { defaultMarketSymbols }
    }

    LaunchedEffect(isGuest, sessionScopeKey) {
        // Entering Home always queues a non-blocking refresh check for public airdrop data.
        // The worker applies its six-hour success cadence and keeps cached/server data usable.
        GlobalSyncScheduler.enqueueDynamicCatalogWarmup(context)
        sites = withContext(Dispatchers.IO) { AirdropSiteStore.load() }
        homeViewModel.load(isGuest)
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(context.getString(R.string.home_heading), modifier = Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                Text(context.getString(R.string.home_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.airdrophunter.app.ui.components.AppIconButton(
                    icon = Icons.Default.NotificationsNone,
                    contentDescription = context.getString(R.string.home_notifications),
                    onClick = { showNotifications = true }
                )
            }
        }

        var dashboardCards by remember { mutableStateOf(HomeDashboardLayoutStore.load(context)) }
        var editingDashboard by remember { mutableStateOf(false) }
        var showAddDashboardCard by remember { mutableStateOf(false) }
        val dashboardBounds = remember { mutableStateMapOf<String, Rect>() }
        var activeDragCard by remember { mutableStateOf<String?>(null) }
        var dashboardDragOffset by remember { mutableStateOf(Offset.Zero) }

        fun saveDashboardCards(updated: List<HomeDashboardCard>) {
            dashboardCards = updated
            scope.launch { withContext(Dispatchers.IO) { HomeDashboardLayoutStore.save(context, updated) } }
        }
        fun openDashboardCard(id: String) {
            when (id) {
                "airdrop_navigation" -> { siteSearchQuery = ""; showNavOverlay = true }
                "wallet_management" -> onOpenWallet()
                "tron_energy" -> onOpenTronEnergy()
                "accounting" -> onOpenAccounting()
                "project_library" -> onOpenProjects()
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(context.getString(R.string.home_quick_actions), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                AppTextButton(onClick = { editingDashboard = !editingDashboard }) {
                    Text(if (editingDashboard) context.getString(R.string.common_done) else context.getString(R.string.common_edit), color = MaterialTheme.colorScheme.primary)
                }
            }
            if (editingDashboard) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextButton(onClick = { showAddDashboardCard = true }) { Text(context.getString(R.string.home_add_card)) }
                    AppTextButton(onClick = { saveDashboardCards(HomeDashboardLayoutStore.defaults) }) {
                        Text(context.getString(R.string.home_reset_layout))
                    }
                }
                Text(context.getString(R.string.home_card_reorder_help), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                dashboardCards.forEach { card ->
                    val title = context.getString(HomeDashboardLayoutStore.titleRes(card.id))
                    val subtitle = context.getString(HomeDashboardLayoutStore.subtitleRes(card.id))
                    val layout = HomeDashboardLayoutStore.layout(card.id)
                    val icon = when (card.id) {
                        "wallet_management" -> Icons.Default.AccountBalanceWallet
                        "tron_energy" -> Icons.Default.Shield
                        "accounting" -> Icons.Default.ReceiptLong
                        "project_library" -> Icons.Default.FolderOpen
                        else -> Icons.Default.Search
                    }
                    val iconBackground = MaterialTheme.colorScheme.primaryContainer
                    val iconTint = MaterialTheme.colorScheme.primary
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(layout.widthFraction)
                            .height(layout.heightDp.dp)
                            .onGloballyPositioned { dashboardBounds[card.id] = it.boundsInRoot() }
                            .graphicsLayer {
                                if (activeDragCard == card.id) {
                                    translationX = dashboardDragOffset.x
                                    translationY = dashboardDragOffset.y
                                    shadowElevation = 18.dp.toPx()
                                } else {
                                    translationX = 0f
                                    translationY = 0f
                                    shadowElevation = 0f
                                }
                            }
                            .zIndex(if (activeDragCard == card.id) 1f else 0f)
                            .appPressFeedback(
                                onClick = { if (!editingDashboard) openDashboardCard(card.id) },
                                scale = 0.96f,
                                enabled = !editingDashboard
                            )
                            .then(if (editingDashboard) Modifier.pointerInput(card.id, editingDashboard, dashboardCards) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { activeDragCard = card.id; dashboardDragOffset = Offset.Zero },
                                    onDrag = { change, amount -> change.consume(); dashboardDragOffset += amount },
                                    onDragEnd = {
                                        val source = dashboardBounds[card.id]
                                        val center = source?.center?.plus(dashboardDragOffset)
                                        val target = if (center == null) null else dashboardBounds
                                            .filterKeys { it != card.id && dashboardCards.any { item -> item.id == it } }
                                            .minByOrNull { (_, bounds) ->
                                                val dx = bounds.center.x - center.x
                                                val dy = bounds.center.y - center.y
                                                dx * dx + dy * dy
                                            }?.key
                                        if (target != null) {
                                            val mutable = dashboardCards.toMutableList()
                                            val from = mutable.indexOfFirst { it.id == card.id }
                                            val to = mutable.indexOfFirst { it.id == target }
                                            if (from >= 0 && to >= 0 && from != to) {
                                                val moved = mutable.removeAt(from)
                                                mutable.add(to, moved)
                                                saveDashboardCards(mutable)
                                            }
                                        }
                                        activeDragCard = null
                                        dashboardDragOffset = Offset.Zero
                                    },
                                    onDragCancel = { activeDragCard = null; dashboardDragOffset = Offset.Zero }
                                )
                            } else Modifier),
                        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        if (card.id == "airdrop_navigation") {
                            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DashboardIconTile(icon, iconBackground, iconTint, 40.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (editingDashboard) {
                                    AppTextButton(onClick = { saveDashboardCards(dashboardCards.filterNot { it.id == card.id }) },
                                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text(context.getString(R.string.common_remove), color = MaterialTheme.colorScheme.error) }
                                } else Text(context.getString(R.string.common_open), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    DashboardIconTile(icon, iconBackground, iconTint, 40.dp)
                                    Spacer(Modifier.weight(1f))
                                    if (editingDashboard) AppTextButton(
                                        onClick = { saveDashboardCards(dashboardCards.filterNot { it.id == card.id }) },
                                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text(context.getString(R.string.common_remove), color = MaterialTheme.colorScheme.error) }
                                }
                                Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        if (showAddDashboardCard) {
            val available = HomeDashboardLayoutStore.available(dashboardCards.map { it.id }.toSet())
            AlertDialog(
                onDismissRequest = { showAddDashboardCard = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(context.getString(R.string.home_add_quick_card), color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (available.isEmpty()) Text(context.getString(R.string.home_no_more_shortcuts), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        available.forEach { id ->
                            AppTextButton(onClick = {
                                saveDashboardCards(dashboardCards + HomeDashboardCard(id))
                                showAddDashboardCard = false
                            }) { Text(context.getString(HomeDashboardLayoutStore.titleRes(id)), color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                },
                confirmButton = { AppTextButton(onClick = { showAddDashboardCard = false }) { Text(context.getString(R.string.common_close)) } }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(context.getString(R.string.home_market_board), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                AppTextButton(onClick = {
                    marketSymbolDraft = marketSymbols.toSet()
                    showMarketEditor = true
                }) { Text(context.getString(R.string.common_edit), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                AppTextButton(
                    onClick = { homeViewModel.refresh(isGuest) },
                    enabled = !state.isRefreshing,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (state.isRefreshing) context.getString(R.string.common_refreshing) else context.getString(R.string.common_refresh),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
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
                when {
                    state.isLoading && state.market.isEmpty() -> Box(
                        Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                    state.market.isEmpty() -> Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(context.getString(R.string.home_market_unavailable), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(context.getString(R.string.common_check_network),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        AppTextButton(onClick = { homeViewModel.refresh(isGuest) }, contentPadding = PaddingValues(0.dp)) {
                            Text(context.getString(R.string.common_retry), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    else -> Column {
                        val visibleMarket = state.market.filter { ticker ->
                            ticker.symbol.substringBefore("-").uppercase(Locale.US) in marketSymbols
                        }
                        if (visibleMarket.isEmpty()) {
                            Text(context.getString(R.string.home_no_market_data), modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                        } else visibleMarket.forEachIndexed { index, ticker ->
                            MarketRow(ticker) { symbol -> onOpenMarketDetail(symbol) }
                            if (index != visibleMarket.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
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
                    Text(context.getString(R.string.home_search_assets), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                when {
                    state.isRefreshing && state.lastSuccessfulUpdateAt != null -> context.getString(R.string.home_market_refreshing)
                    state.marketError?.startsWith("刚刚刷新过") == true -> context.getString(R.string.home_market_rate_limit)
                    state.marketError != null && state.lastSuccessfulUpdateAt != null -> context.getString(R.string.home_market_error_cached)
                    state.marketCacheStatus == com.airdrophunter.app.data.CacheDisplayStatus.STALE && state.lastSuccessfulUpdateAt != null ->
                        context.getString(R.string.home_market_cached_at, java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(state.lastSuccessfulUpdateAt ?: 0L)))
                    state.marketCacheStatus == com.airdrophunter.app.data.CacheDisplayStatus.REFRESHING && state.lastSuccessfulUpdateAt != null ->
                        context.getString(R.string.home_market_refreshing)
                    state.lastSuccessfulUpdateAt != null -> context.getString(R.string.home_market_updated_at, java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(state.lastSuccessfulUpdateAt ?: 0L)))
                    state.marketError != null -> context.getString(R.string.home_market_unavailable)
                    else -> context.getString(R.string.home_market_waiting)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (showMarketEditor) {
            AlertDialog(
                onDismissRequest = { showMarketEditor = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(context.getString(R.string.home_custom_market), color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(context.getString(R.string.home_market_select_help), color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    }) { Text(context.getString(R.string.common_save)) }
                },
                dismissButton = {
                    AppTextButton(onClick = {
                        marketSymbolDraft = defaultMarketSymbols.toSet()
                        marketSymbols = defaultMarketSymbols
                        showMarketEditor = false
                        scope.launch { runCatching { CacheStore.saveMarketDashboardSymbols(defaultMarketSymbols) } }
                    }) { Text(context.getString(R.string.common_reset)) }
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
                        context.getString(R.string.home_airdrop_directory),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        context.getString(R.string.home_site_count, sites.size), 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                AppTextField(
                    value = siteSearchQuery,
                    onValueChange = { siteSearchQuery = it },
                    placeholder = context.getString(R.string.home_search_site),
                    trailingClear = true
                )
                if (filteredSites.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (sites.isEmpty()) context.getString(R.string.home_no_sites) else context.getString(R.string.home_no_site_match),
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
                                                val intent = Intent(context, BrowserActivity::class.java)
                                                    .putExtra(BrowserActivity.EXTRA_URL, site.url)
                                                    .putExtra(BrowserActivity.EXTRA_TITLE, site.name)
                                                    .putExtra(BrowserActivity.EXTRA_WALLETS_JSON, Gson().toJson(walletRows))
                                                walletRows.firstOrNull()?.let {
                                                    intent.putExtra(BrowserActivity.EXTRA_SELECTED_WALLET_ID, it.id)
                                                }
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
        title = { Text(context.getString(R.string.home_notifications), color = MaterialTheme.colorScheme.onSurface) },
        text = { Text(context.getString(R.string.home_notifications_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { AppTextButton(onClick = { showNotifications = false }) { Text(context.getString(R.string.common_close)) } }
    )
}

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
    fun titleRes(id: String): Int = when (id) {
        "airdrop_navigation" -> R.string.home_card_airdrop_title
        "accounting" -> R.string.home_card_accounting_title
        "wallet_management" -> R.string.home_card_wallet_title
        "tron_energy" -> R.string.home_card_tron_title
        "project_library" -> R.string.home_card_projects_title
        else -> R.string.home_quick_actions
    }
    fun subtitleRes(id: String): Int = when (id) {
        "airdrop_navigation" -> R.string.home_card_airdrop_subtitle
        "accounting" -> R.string.home_card_accounting_subtitle
        "wallet_management" -> R.string.home_card_wallet_subtitle
        "tron_energy" -> R.string.home_card_tron_subtitle
        "project_library" -> R.string.home_card_projects_subtitle
        else -> R.string.home_card_subtitle_empty
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
