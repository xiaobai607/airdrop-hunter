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
            Card(
                onClick = { showNavOverlay = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("空投平台导航", Modifier.weight(1f), color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("打开文件夹  ›", color = AppColors.Primary, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        sites.sortedByDescending { it.clickCount }.take(4).forEach { site ->
                            FaviconTile(site, Modifier.size(42.dp))
                        }
                    }
                }
            }
            Card(
                onClick = onOpenWallet,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = AppColors.Primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("安全查钱包", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("通过公链网络验证公开地址", color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", color = AppColors.TextHelper)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("行情看板", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(if (state.isRefreshing) "更新中" else "刷新",
                    modifier = Modifier.clickable { vm.refresh(isGuest) }.padding(6.dp),
                    color = AppColors.Primary, style = MaterialTheme.typography.labelLarge)
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
                    state.market.isEmpty() -> Text(
                        if (state.hasError) "行情暂不可用，请稍后重试" else "暂无行情数据",
                        Modifier.padding(18.dp), color = AppColors.TextSecondary
                    )
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
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = Color(0xFFF3F5F7)) {
        SubcomposeAsyncImage(
            model = if (useFallback) fallbackUrl else primaryUrl,
            contentDescription = site.name,
            modifier = Modifier.fillMaxSize().padding(7.dp),
            loading = { FaviconLetter(site) },
            error = {
                if (!useFallback) {
                    LaunchedEffect(site.id) { useFallback = true }
                    FaviconLetter(site)
                } else FaviconLetter(site)
            }
        )
    }
}

@Composable
private fun FaviconLetter(site: AirdropSite) {
    Box(Modifier.fillMaxSize().background(Color(0xFFE7F1EF), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center) {
        Text(site.name.take(1).uppercase(), color = AppColors.Primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MarketRow(ticker: BinanceTicker) {
    val price = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(ticker.lastPrice)
    val change = String.format(Locale.US, "%+.2f%%", ticker.priceChangePercent)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ticker.shortSymbol, color = AppColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(ticker.fullName, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("$ $price", color = AppColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(change, color = if (ticker.priceChangePercent >= 0) AppColors.Success else AppColors.Danger,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
