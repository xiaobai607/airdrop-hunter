package com.airdrophunter.app

import android.content.Intent
import android.widget.Toast
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
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSources: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenAirdropHub: () -> Unit = {},
    isGuest: Boolean = true,
    onRequireAuth: () -> Unit = {},
    vm: HomeViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showNavOverlay by remember { mutableStateOf(false) }
    var sites by remember { mutableStateOf(AirdropSiteStore.defaults) }

    LaunchedEffect(isGuest) {
        sites = withContext(Dispatchers.IO) { AirdropSiteStore.load() }
        vm.load(isGuest)
    }

    Column(
        modifier.fillMaxSize().background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("首页", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium,
                color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showSearch = true }) {
                Icon(Icons.Default.Search, "搜索", tint = AppColors.TextSecondary)
            }
            IconButton(onClick = { showNotifications = true }) {
                Icon(Icons.Default.NotificationsNone, "通知", tint = AppColors.TextSecondary)
            }
        }

        Card(
            onClick = onOpenAirdropHub,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            border = BorderStroke(1.dp, AppColors.Border),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("空投聚合大厅", color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("浏览从公开数据源同步的项目", color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("›", color = AppColors.Primary, style = MaterialTheme.typography.headlineMedium)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("快捷入口", color = AppColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    onClick = { showNavOverlay = true },
                    modifier = Modifier.weight(1f).height(164.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    border = BorderStroke(1.dp, AppColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(13.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            sites.sortedByDescending { it.clickCount }.take(4).chunked(2).forEach { rowSites ->
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    rowSites.forEach { site ->
                                        FaviconTile(site, Modifier.size(25.dp))
                                    }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("空投平台导航", color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                maxLines = 1)
                            Text("常用活动网站", color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }

                Card(
                    onClick = onOpenWallet,
                    modifier = Modifier.weight(1f).height(164.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    border = BorderStroke(1.dp, AppColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(13.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null,
                            tint = AppColors.Primary, modifier = Modifier.size(48.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("安全查钱包", color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("查询公开地址资产", color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
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
                    modifier = Modifier.clickable { vm.refresh(isGuest) }.padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
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
                        TextButton(onClick = { vm.refresh(isGuest) }, contentPadding = PaddingValues(0.dp)) {
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

    if (showNavOverlay) {
        Dialog(
            onDismissRequest = { showNavOverlay = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            GlassBackground(
                modifier = Modifier.fillMaxSize(),
                blurRadius = 30.dp,
                cornerRadius = 0.dp
            ) {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        Text("空投导航", color = Color.White, style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showNavOverlay = false }) { Text("关闭", color = Color.White) }
                    }
                    Spacer(Modifier.height(22.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(sites, key = { it.id }) { site ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        showNavOverlay = false
                                        scope.launch {
                                            val updated = withContext(Dispatchers.IO) { AirdropSiteStore.recordClick(site) }
                                            sites = updated
                                            val walletRows = withContext(Dispatchers.IO) {
                                                val raw = CacheStore.readLocal("wallets")
                                                val type = object : TypeToken<List<WalletDto>>() {}.type
                                                raw?.let { runCatching { Gson().fromJson<List<WalletDto>>(it, type) }.getOrNull() }.orEmpty()
                                            }
                                            val walletJson = com.google.gson.Gson().toJson(walletRows)
                                            val intent = Intent(context, BrowserActivity::class.java)
                                                .putExtra(BrowserActivity.EXTRA_URL, site.url)
                                                .putExtra(BrowserActivity.EXTRA_TITLE, site.name)
                                                .putExtra(BrowserActivity.EXTRA_WALLETS_JSON, walletJson)
                                            walletRows.firstOrNull()?.let { intent.putExtra(BrowserActivity.EXTRA_SELECTED_WALLET_ID, it.id) }
                                            context.startActivity(intent)
                                        }
                                    }.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FaviconTile(site, Modifier.size(62.dp))
                                Text(site.name, color = Color.White, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSearch) AlertDialog(
        onDismissRequest = { showSearch = false }, containerColor = AppColors.Surface,
        title = { Text("搜索", color = AppColors.TextPrimary) },
        text = { Text("搜索功能正在开发中。", color = AppColors.TextSecondary) },
        confirmButton = { TextButton(onClick = { showSearch = false }) { Text("知道了") } }
    )
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
            Text(change, color = if (ticker.priceChangePercent >= 0) AppColors.Success else AppColors.Danger,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
