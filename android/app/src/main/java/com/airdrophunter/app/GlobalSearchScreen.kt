package com.airdrophunter.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheMetadataEntity
import com.airdrophunter.app.data.CacheRefreshPolicy
import com.airdrophunter.app.data.CacheDisplayStatus
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.CommunityProjectCatalogDto
import com.airdrophunter.app.data.HotCoin
import com.airdrophunter.app.data.MarketCoin
import com.airdrophunter.app.data.MarketSearchRepository
import com.airdrophunter.app.data.normalizeSearchText
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private enum class SearchCategory(val key: String, val label: String) {
    COIN("coin", "币种"),
    DAPP("dapp", "DApp")
}

@Composable
fun GlobalSearchScreen(
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onCoinSelected: (String) -> Unit,
    onDappSelected: (String, String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val repository = remember(application) { MarketSearchRepository(application.appContainer.okxMarketRepository) }
    val scope = rememberCoroutineScope()
    val history by CacheStore.observeSearchHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    var category by remember { mutableStateOf(SearchCategory.COIN) }
    var query by remember { mutableStateOf("") }
    var coinResults by remember { mutableStateOf<List<MarketCoin>>(emptyList()) }
    var dappResults by remember { mutableStateOf<List<CommunityProjectCatalogDto>>(emptyList()) }
    var hotCoins by remember { mutableStateOf<List<HotCoin>>(emptyList()) }
    var hotCoinMetadata by remember { mutableStateOf<CacheMetadataEntity?>(null) }
    var coinCacheMetadata by remember { mutableStateOf<CacheMetadataEntity?>(null) }
    var dappCacheMetadata by remember { mutableStateOf<CacheMetadataEntity?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ApiClient.initialize(context)
        // Publish Room's last-known-good hot coins before waiting for coordinator refresh.
        hotCoins = try {
            withContext(Dispatchers.IO) { repository.readCachedHotCoins() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        hotCoinMetadata = try {
            withContext(Dispatchers.IO) {
                CacheStore.readCacheMetadata("global-search-hot-coins", "okx-spot")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

        try {
            val refreshed = withContext(Dispatchers.IO) { repository.loadHotCoins() }
            hotCoins = refreshed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the cached rows; metadata below exposes stale/error status.
        }
        hotCoinMetadata = try {
            withContext(Dispatchers.IO) {
                CacheStore.readCacheMetadata("global-search-hot-coins", "okx-spot")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            hotCoinMetadata
        }
    }

    LaunchedEffect(query, category) {
        val value = query.trim()
        searchError = null
        if (value.isBlank()) {
            coinResults = emptyList()
            dappResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }

        // Room results are rendered first; only then does the 300 ms remote debounce begin.
        if (category == SearchCategory.COIN) {
            coinResults = withContext(Dispatchers.IO) { repository.searchLocalCoins(value) }
            dappResults = emptyList()
        } else {
            dappResults = withContext(Dispatchers.IO) { repository.searchLocalDapps(value) }
            coinResults = emptyList()
        }
        isSearching = true
        delay(300)
        if (category == SearchCategory.COIN) {
            try {
                coinResults = withContext(Dispatchers.IO) { repository.searchRemoteCoins(value) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (coinResults.isEmpty()) searchError = "暂时无法获取远程币种目录，本地结果仍可使用"
            }
            coinCacheMetadata = withContext(Dispatchers.IO) {
                CacheStore.readCacheMetadata("market-prices-supported", "coinpaprika")
            }
        } else {
            try {
                dappResults = withContext(Dispatchers.IO) { repository.searchRemoteDapps(value) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (dappResults.isEmpty()) searchError = "暂时无法获取 DApp 目录，本地结果仍可使用"
            }
            dappCacheMetadata = withContext(Dispatchers.IO) {
                CacheStore.readCacheMetadata("global-search-dapp-catalog", "community-catalog")
            }
        }
        isSearching = false
    }

    fun recordAndSearchCoin(symbol: String) {
        scope.launch { CacheStore.saveSearchHistory(query.ifBlank { symbol }, SearchCategory.COIN.key) }
        onCoinSelected(symbol)
    }
    fun recordAndOpenDapp(project: CommunityProjectCatalogDto) {
        val url = project.website_url.trim()
        val parsed = Uri.parse(url)
        if (parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank()) {
            scope.launch { CacheStore.saveSearchHistory(query.ifBlank { project.name }, SearchCategory.DAPP.key) }
            onDappSelected(project.name, url)
        } else {
            searchError = "该 DApp 暂无有效 HTTPS 链接"
        }
    }

    Column(
        modifier.fillMaxSize().background(AppColors.Background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(Color(0xFFF0F2F5)).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.TextHelper)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (category == SearchCategory.COIN) "搜索币种名称或代号" else "搜索 DApp 名称、链或标签") },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                    )
                )
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Clear, contentDescription = "清空输入", tint = AppColors.TextHelper)
                }
            }
            TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 2.dp)) {
                Text("取消", color = AppColors.TextPrimary)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchCategory.values().forEach { option ->
                FilterChip(
                    selected = category == option,
                    onClick = { category = option; query = "" },
                    label = { Text(option.label) },
                    shape = RoundedCornerShape(999.dp)
                )
            }
        }

        if (query.isBlank() && history.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("搜索历史", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { CacheStore.clearSearchHistory() } }) {
                        Text("清空", color = AppColors.TextHelper)
                    }
                }
                history.take(10).forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Surface)
                            .clickable {
                                category = if (item.category == SearchCategory.DAPP.key) SearchCategory.DAPP else SearchCategory.COIN
                                query = item.query
                            }.padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (item.category == SearchCategory.DAPP.key) "DApp" else "币种", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(10.dp))
                        Text(item.query, color = AppColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                        Icon(Icons.Default.History, null, tint = AppColors.TextHelper, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (query.isBlank() && category == SearchCategory.COIN) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("热门币种", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                cacheStatusLabel(hotCoinMetadata)?.let {
                    Text(it, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                }
                hotCoins.forEach { coin -> HotCoinRow(coin) { recordAndSearchCoin(coin.symbol) } }
                if (hotCoins.isEmpty()) Text("热门行情暂不可用，输入币种名称仍可搜索", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (query.isNotBlank()) {
            Text(
                if (category == SearchCategory.COIN) "币种搜索结果" else "DApp 搜索结果",
                color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
            )
            cacheStatusLabel(if (category == SearchCategory.COIN) coinCacheMetadata else dappCacheMetadata)?.let {
                Text(it, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            }
            when (category) {
                SearchCategory.COIN -> if (coinResults.isEmpty()) {
                    Text(if (isSearching) "正在搜索…" else searchError ?: "没有找到匹配的币种", color = AppColors.TextHelper)
                } else coinResults.forEach { coin -> MarketCoinRow(coin) { recordAndSearchCoin(coin.symbol) } }
                SearchCategory.DAPP -> if (dappResults.isEmpty()) {
                    Text(if (isSearching) "正在搜索…" else searchError ?: "没有找到匹配的 DApp", color = AppColors.TextHelper)
                } else dappResults.forEach { project ->
                    DappResultRow(project) { recordAndOpenDapp(project) }
                }
            }
            if (searchError != null && (coinResults.isNotEmpty() || dappResults.isNotEmpty())) {
                Text(searchError.orEmpty(), color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun HotCoinRow(coin: HotCoin, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Surface)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF0F2F5)), contentAlignment = Alignment.Center) {
            Text(coin.symbol.take(1), color = AppColors.Primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(coin.symbol, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(coin.name, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$ " + formatSearchPrice(coin.lastPrice), color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(String.format(Locale.US, "%+.2f%%", coin.change24hPercent),
                color = if (coin.change24hPercent >= 0) AppColors.Success else AppColors.Danger,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MarketCoinRow(coin: MarketCoin, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Surface)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF0F2F5)), contentAlignment = Alignment.Center) {
            Text(coin.symbol.take(1), color = AppColors.Primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${coin.symbol} · ${coin.name}", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            val context = listOfNotNull(coin.network, coin.contractAddress).joinToString(" · ")
            if (context.isNotBlank()) Text(context, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(coin.priceUsd?.let { "$ " + formatSearchPrice(it) } ?: "价格暂缺", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            coin.change24hPercent?.let { change ->
                Text(String.format(Locale.US, "%+.2f%%", change), color = if (change >= 0) AppColors.Success else AppColors.Danger, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DappResultRow(project: CommunityProjectCatalogDto, onClick: () -> Unit) {
    val validUrl = Uri.parse(project.website_url).let { it.scheme.equals("https", true) && !it.host.isNullOrBlank() }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Surface)
            .clickable(enabled = validUrl, onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFEAF2FF)), contentAlignment = Alignment.Center) {
            Text(project.name.take(1).uppercase(), color = Color(0xFF315A9D), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(project.name, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(project.chain, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            Text(if (validUrl) project.website_url else "链接待补充", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Text(if (validUrl) "打开" else "暂无链接", color = if (validUrl) AppColors.Primary else AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatSearchPrice(value: Double): String = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 8
}.format(value)


private fun cacheStatusLabel(metadata: CacheMetadataEntity?): String? {
    metadata ?: return null
    val updated = metadata.fetchedAt?.let {
        java.text.SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date(it))
    } ?: "暂无成功更新时间"
    return when (CacheRefreshPolicy.displayStatus(metadata, System.currentTimeMillis())) {
        CacheDisplayStatus.FRESH -> "数据更新于 $updated"
        CacheDisplayStatus.REFRESHING -> "正在更新 · 保留上次数据（$updated）"
        CacheDisplayStatus.STALE -> "缓存数据 · 上次更新 $updated"
        CacheDisplayStatus.ERROR -> "更新失败 · 保留上次缓存（$updated）"
        CacheDisplayStatus.UNKNOWN -> "等待首次更新"
    }
}
