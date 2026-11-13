package com.airdrophunter.app

import androidx.compose.foundation.background
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.OkxTickerDetail
import com.airdrophunter.app.data.CacheMetadataEntity
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.CacheDisplayStatus
import com.airdrophunter.app.data.CacheRefreshPolicy
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CoinMarketDetailScreen(symbol: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val repository = remember(application) { application.appContainer.okxMarketRepository }
    val scope = rememberCoroutineScope()
    var detail by remember(symbol) { mutableStateOf<OkxTickerDetail?>(null) }
    var loading by remember(symbol) { mutableStateOf(true) }
    var error by remember(symbol) { mutableStateOf<String?>(null) }
    var cacheMetadata by remember(symbol) { mutableStateOf<CacheMetadataEntity?>(null) }

    suspend fun refresh(manual: Boolean) {
        loading = true
        error = null
        val result = repository.loadSpotTickerDetail(symbol, manual)
        detail = result.data ?: detail
        error = result.errorMessage ?: if (result.throttled) "刚刚刷新过，请 30 秒后再试" else null
        cacheMetadata = CacheStore.readCacheMetadata("market-detail:${symbol.uppercase()}", "okx-spot-detail")
        loading = false
    }

    LaunchedEffect(symbol) {
        ApiClient.initialize(context)
        detail = repository.readCachedSpotTickerDetail(symbol)
        cacheMetadata = CacheStore.readCacheMetadata("market-detail:${symbol.uppercase()}", "okx-spot-detail")
        loading = detail == null
        refresh(manual = false)
    }

    val priceFormat = remember {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 8
        }
    }
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("$symbol / USDT", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        when {
            loading && detail == null -> Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error != null && detail == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("暂时无法获取行情", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppButton("重新加载", onClick = { scope.launch { refresh(manual = true) } })
            }
            detail != null -> {
                val value = detail!!
                val positive = value.change24hPercent >= 0
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("最新价格", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$ " + priceFormat.format(value.lastPrice), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(String.format(Locale.US, "%+.2f%%（24小时）", value.change24hPercent),
                            color = if (positive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        MarketMetricRow("24小时最高", "$ " + priceFormat.format(value.high24h))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MarketMetricRow("24小时最低", "$ " + priceFormat.format(value.low24h))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MarketMetricRow("24小时成交量", priceFormat.format(value.volume24h) + " $symbol")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MarketMetricRow("24小时成交额", priceFormat.format(value.volumeCurrency24h) + " USDT")
                    }
                }
                Text(
                    "数据来源：OKX · " + marketCacheStatusLabel(cacheMetadata),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall
                )
                AppButton(
                    text = "刷新行情",
                    onClick = { scope.launch { refresh(manual = true) } },
                    enabled = !loading,
                    loading = loading,
                    style = AppButtonStyle.SECONDARY
                )
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MarketMetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
}

private fun marketCacheStatusLabel(metadata: CacheMetadataEntity?): String {
    if (metadata == null) return "等待首次更新"
    val updated = metadata.fetchedAt?.let {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
    } ?: "暂无成功更新时间"
    return when (CacheRefreshPolicy.displayStatus(metadata, System.currentTimeMillis())) {
        CacheDisplayStatus.FRESH -> "更新于 $updated"
        CacheDisplayStatus.REFRESHING -> "正在更新 · 保留上次数据（$updated）"
        CacheDisplayStatus.STALE -> "缓存数据 · 上次更新 $updated"
        CacheDisplayStatus.ERROR -> "更新失败 · 保留上次缓存（$updated）"
        CacheDisplayStatus.UNKNOWN -> "等待首次更新"
    }
}
