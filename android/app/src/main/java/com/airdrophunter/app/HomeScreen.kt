package com.airdrophunter.app

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
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

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSources: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenGlobalSearch: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenAirdropHub: () -> Unit = {},
    onOpenTronEnergy: () -> Unit = {},
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
        sites = withContext(Dispatchers.IO) { AirdropSiteStore.load() }
        homeViewModel.load(isGuest)
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier.fillMaxSize().background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("首页", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium,
                color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenGlobalSearch) {
                Icon(Icons.Default.Search, "搜索", tint = AppColors.TextSecondary)
            }
            IconButton(onClick = { showNotifications = true }) {
                Icon(Icons.Default.NotificationsNone, "通知", tint = AppColors.TextSecondary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("快捷入口", color = AppColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    onClick = { showNavOverlay = true; navContentVisible = true },
                    modifier = Modifier.weight(1f).wrapContentHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    border = BorderStroke(1.dp, AppColors.Border),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            sites.sortedByDescending { it.clickCount }.take(4).forEach { FaviconTile(it, Modifier.size(25.dp)) }
                        }
                        Text("空投导航", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("常用活动网站", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                ShortcutCard(
                    icon = Icons.Default.Search,
                    title = "全局搜索",
                    subtitle = "搜索币种、地址、DApp",
                    onClick = onOpenGlobalSearch,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShortcutCard(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "钱包管理",
                    subtitle = "管理已保存地址",
                    onClick = onOpenWallet,
                    modifier = Modifier.weight(1f)
                )
                ShortcutCard(
                    icon = Icons.Default.Shield,
                    title = "⚡️ TRON 能量租赁",
                    subtitle = "租赁前核对转出地址",
                    onClick = onOpenTronEnergy,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("行情看板", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, BrowserActivity::class.java)
                                .putExtra(BrowserActivity.EXTRA_URL, "https://m.coinmarketcap.com/")
                                .putExtra(BrowserActivity.EXTRA_TITLE, "CoinMarketCap 行情")
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp)
                ) {
                    Text("全部行情 / 自主搜索", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
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
                        state.market.forEachIndexed { index, ticker ->
                            MarketRow(ticker)
                            if (index != state.market.lastIndex) HorizontalDivider(color = AppColors.Border)
                        }
                    }
                }
            }
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
