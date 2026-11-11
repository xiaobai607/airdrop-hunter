package com.airdrophunter.app

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.airdrophunter.app.data.AirdropSite
import com.airdrophunter.app.data.AirdropSiteStore
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.GlobalSyncScheduler
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.ui.theme.AppColors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSources: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenGlobalSearch: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenAirdropCampaigns: () -> Unit = {},
    onOpenBinanceOfficial: () -> Unit = {},
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
    val scope = rememberCoroutineScope()
    var showNotifications by remember { mutableStateOf(false) }
    var showNavOverlay by remember { mutableStateOf(false) }
    var navContentVisible by remember { mutableStateOf(false) }
    fun closeNavOverlay() { scope.launch { navContentVisible = false; delay(180); showNavOverlay = false } }
    var sites by remember { mutableStateOf(AirdropSiteStore.defaults) }

    LaunchedEffect(isGuest, sessionScopeKey) {
        // Entering Home always queues a non-blocking refresh check for public airdrop data.
        // The worker applies its six-hour success cadence and keeps cached/server data usable.
        GlobalSyncScheduler.enqueueDynamicCatalogWarmup(context)
        sites = withContext(Dispatchers.IO) { AirdropSiteStore.load() }
        homeViewModel.load(isGuest)
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier.fillMaxSize().background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("首页", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium,
                color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showNotifications = true }) {
                Icon(Icons.Default.NotificationsNone, "通知", tint = AppColors.TextSecondary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("行情看板", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(if (state.isRefreshing) "更新中" else "刷新",
                    modifier = Modifier.clickable { homeViewModel.refresh(isGuest) }.padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
                    color = AppColors.Primary, style = MaterialTheme.typography.labelSmall)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                when {
                    state.isLoading && state.market.isEmpty() -> Box(
                        Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = AppColors.Primary) }
                    state.market.isEmpty() -> Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("无法获取实时行情", color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(state.marketError ?: "请检查网络连接后重试。",
                            color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { homeViewModel.refresh(isGuest) }, contentPadding = PaddingValues(0.dp)) {
                            Text("点击重试", color = AppColors.Primary)
                        }
                    }
                    else -> Column {
                        state.market.filter { it.symbol == "BTC" || it.symbol == "ETH" }.forEachIndexed { index, ticker ->
                            MarketRow(ticker)
                            if (index != state.market.lastIndex) HorizontalDivider(color = AppColors.Border)
                        }
                    }
                }
            }
            Text(
                when {
                    state.isRefreshing && state.lastSuccessfulUpdateAt != null -> "行情更新中 · 保留上次数据"
                    state.marketError != null && state.lastSuccessfulUpdateAt != null -> "更新失败 · 显示上次成功数据"
                    state.marketCacheStatus == com.airdrophunter.app.data.CacheDisplayStatus.STALE && state.lastSuccessfulUpdateAt != null ->
                        "缓存数据 · 上次更新 " + java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(state.lastSuccessfulUpdateAt ?: 0L))
                    state.marketCacheStatus == com.airdrophunter.app.data.CacheDisplayStatus.REFRESHING && state.lastSuccessfulUpdateAt != null ->
                        "行情更新中 · 保留上次数据"
                    state.lastSuccessfulUpdateAt != null -> "数据更新于 " + java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(state.lastSuccessfulUpdateAt ?: 0L))
                    state.marketError != null -> "行情暂不可用"
                    else -> "等待行情数据"
                },
                color = AppColors.TextHelper,
                style = MaterialTheme.typography.labelSmall
            )
        }
        // Search belongs directly below the market dashboard, before all shortcuts.
        Row(
            Modifier.fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.Surface)
                .clickable(onClick = onOpenGlobalSearch)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.TextSecondary)
            Text("搜索币种或 DApp", color = AppColors.TextHelper,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
                "airdrop_navigation" -> { showNavOverlay = true; navContentVisible = true }
                "wallet_management" -> onOpenWallet()
                "tron_energy" -> onOpenTronEnergy()
                "accounting" -> onOpenAccounting()
                "airdrop_library" -> onOpenAirdropCampaigns()
                "binance_official" -> onOpenBinanceOfficial()
                "project_library" -> onOpenProjects()
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("快捷入口", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { editingDashboard = !editingDashboard }) {
                    Text(if (editingDashboard) "完成" else "编辑", color = AppColors.Primary)
                }
            }
            if (editingDashboard) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showAddDashboardCard = true }) { Text("＋ 添加卡片") }
                    TextButton(onClick = { saveDashboardCards(HomeDashboardLayoutStore.defaults) }) {
                        Text("恢复默认布局")
                    }
                }
                Text("长按卡片拖动排序；可添加或移除。", color = AppColors.TextHelper,
                    style = MaterialTheme.typography.labelSmall)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                dashboardCards.forEach { card ->
                    val title = HomeDashboardLayoutStore.title(card.id)
                    val subtitle = HomeDashboardLayoutStore.subtitle(card.id)
                    val layout = HomeDashboardLayoutStore.layout(card.id)
                    val icon = when (card.id) {
                        "wallet_management" -> Icons.Default.AccountBalanceWallet
                        "tron_energy" -> Icons.Default.Shield
                        "accounting" -> Icons.Default.ReceiptLong
                        "airdrop_library" -> Icons.Default.NotificationsNone
                        "binance_official" -> Icons.Default.Shield
                        "project_library" -> Icons.Default.Search
                        else -> Icons.Default.Search
                    }
                    Card(
                        onClick = { if (!editingDashboard) openDashboardCard(card.id) },
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
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        border = BorderStroke(1.dp, AppColors.Border),
                        elevation = CardDefaults.cardElevation(if (activeDragCard == card.id) 5.dp else 1.dp)
                    ) {
                        if (card.id == "airdrop_navigation") {
                            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(30.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(title, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(subtitle, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                                if (editingDashboard) {
                                    TextButton(onClick = { saveDashboardCards(dashboardCards.filterNot { it.id == card.id }) },
                                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text("移除", color = AppColors.Danger) }
                                } else Text("打开  ›", color = AppColors.Primary, style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(26.dp))
                                    Spacer(Modifier.weight(1f))
                                    if (editingDashboard) TextButton(
                                        onClick = { saveDashboardCards(dashboardCards.filterNot { it.id == card.id }) },
                                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text("移除", color = AppColors.Danger) }
                                }
                                Text(title, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(subtitle, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
                containerColor = AppColors.Surface,
                title = { Text("添加快捷卡片", color = AppColors.TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (available.isEmpty()) Text("目前没有更多可添加的快捷功能。", color = AppColors.TextSecondary)
                        available.forEach { id ->
                            TextButton(onClick = {
                                saveDashboardCards(dashboardCards + HomeDashboardCard(id))
                                showAddDashboardCard = false
                            }) { Text(HomeDashboardLayoutStore.title(id), color = AppColors.Primary) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAddDashboardCard = false }) { Text("关闭") } }
            )
        }

        Spacer(Modifier.height(8.dp))
    }
    }

    if (showNavOverlay) {
        Dialog(
            onDismissRequest = { closeNavOverlay() },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            AnimatedVisibility(
                visible = navContentVisible,
                enter = fadeIn() + scaleIn(initialScale = 0.88f),
                exit = fadeOut() + scaleOut(targetScale = 0.92f)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("空投导航", modifier = Modifier.weight(1f), color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { closeNavOverlay() }) { Text("关闭", color = AppColors.Primary) }
                        }
                        Text("选择一个平台打开", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                            contentPadding = PaddingValues(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(sites, key = { it.id }) { site ->
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            closeNavOverlay()
                                            scope.launch {
                                                val updated = withContext(Dispatchers.IO) { AirdropSiteStore.recordClick(site) }
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
                                                walletRows.firstOrNull()?.let { intent.putExtra(BrowserActivity.EXTRA_SELECTED_WALLET_ID, it.id) }
                                                context.startActivity(intent)
                                            }
                                        }.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    FaviconTile(site, Modifier.size(52.dp))
                                    Text(site.name, color = AppColors.TextPrimary, style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotifications) AlertDialog(
        onDismissRequest = { showNotifications = false }, containerColor = AppColors.Surface,
        title = { Text("消息通知", color = AppColors.TextPrimary) },
        text = { Text("通知功能正在开发中。", color = AppColors.TextSecondary) },
        confirmButton = { TextButton(onClick = { showNotifications = false }) { Text("关闭") } }
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
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
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
    Box(Modifier.fillMaxSize().background(Color(0xFFE7F1EF), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center) {
        Text(site.name.take(1).uppercase(), color = AppColors.Primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MarketRow(ticker: com.airdrophunter.app.data.OkxTicker) {
    val price = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(ticker.lastPrice)
    val change = String.format(Locale.US, "%+.2f%%", ticker.change24hPercent)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ticker.symbol, color = AppColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(ticker.displayName, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("$ $price", color = AppColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(change, color = if (ticker.change24hPercent >= 0) AppColors.Success else AppColors.Danger,
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
        onClick = onClick,
        modifier = modifier.wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.Border),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(34.dp))
            Text(title, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
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
        "wallet_management" to HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 104),
        "tron_energy" to HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 104),
        "accounting" to HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 104),
        "airdrop_library" to HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 104),
        "binance_official" to HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 104),
        "project_library" to HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 104)
    )

    fun layout(id: String): HomeDashboardCardSpec =
        cardSpecs[id] ?: HomeDashboardCardSpec(widthFraction = 0.48f, heightDp = 104)

    val defaults = listOf(
        HomeDashboardCard("airdrop_navigation"),
        HomeDashboardCard("accounting"),
        HomeDashboardCard("wallet_management"),
        HomeDashboardCard("tron_energy"),
        HomeDashboardCard("airdrop_library"),
        HomeDashboardCard("binance_official")
    )
    private val supported = listOf("airdrop_navigation", "accounting", "wallet_management", "tron_energy", "airdrop_library", "binance_official", "project_library")
    fun title(id: String) = when (id) {
        "airdrop_navigation" -> "空投导航"
        "accounting" -> "记账"
        "wallet_management" -> "钱包管理"
        "tron_energy" -> "⚡ TRON 能量租赁"
        "airdrop_library" -> "空投列表"
        "binance_official" -> "币安官方活动"
        "project_library" -> "库管理"
        else -> "快捷入口"
    }
    fun subtitle(id: String) = when (id) {
        "airdrop_navigation" -> "常用活动网站"
        "accounting" -> "记录 Gas 消耗与实际收益"
        "wallet_management" -> "管理已保存地址"
        "tron_energy" -> "租赁前核对转出地址"
        "airdrop_library" -> "浏览已核验的公开空投活动"
        "binance_official" -> "独立查看币安官方公告与活动"
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
        // One-time migration: expose the two distinct discovery destinations in existing layouts.
        if (preferences.getInt("layout_version", 0) < 2) {
            if (valid.none { it.id == "airdrop_library" }) valid.add(HomeDashboardCard("airdrop_library"))
            if (valid.none { it.id == "binance_official" }) valid.add(HomeDashboardCard("binance_official"))
            preferences.edit().putInt("layout_version", 2).apply()
        }
        // Keep the accounting shortcut visible for users upgrading from older layouts.
        if (valid.none { it.id == "accounting" }) valid.add(minOf(1, valid.size), HomeDashboardCard("accounting"))
        return valid
    }
    fun save(context: android.content.Context, cards: List<HomeDashboardCard>) {
        val valid = cards.filter { it.id in supported }.distinctBy { it.id }
        context.getSharedPreferences("home_dashboard_layout", android.content.Context.MODE_PRIVATE)
            .edit().putString("cards", Gson().toJson(valid)).putInt("layout_version", 2).apply()
    }
}
