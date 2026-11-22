package com.airdrophunter.app

import androidx.compose.foundation.background
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
        error = result.errorMessage ?: if (result.throttled) context.getString(R.string.market_detail_recently_refreshed) else null
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
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.market_detail_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "$symbol / USDT",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        when {
            loading && detail == null -> Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error != null && detail == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.market_detail_unavailable), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppButton(stringResource(R.string.market_detail_reload), onClick = { scope.launch { refresh(manual = true) } })
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
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.market_detail_latest_price), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$ " + priceFormat.format(value.lastPrice),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(String.format(Locale.US, stringResource(R.string.market_detail_change_24h), value.change24hPercent),
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
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MarketMetricRow(stringResource(R.string.market_detail_high_24h), "$ " + priceFormat.format(value.high24h))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MarketMetricRow(stringResource(R.string.market_detail_low_24h), "$ " + priceFormat.format(value.low24h))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MarketMetricRow(stringResource(R.string.market_detail_volume_24h), priceFormat.format(value.volume24h) + " $symbol")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MarketMetricRow(stringResource(R.string.market_detail_turnover_24h), priceFormat.format(value.volumeCurrency24h) + " USDT")
                    }
                }
                Text(
                    stringResource(R.string.market_detail_source, marketCacheStatusLabel(cacheMetadata)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall
                )
                AppButton(
                    text = stringResource(R.string.market_detail_refresh),
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.1f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            softWrap = true
        )
    }
}

@Composable
private fun marketCacheStatusLabel(metadata: CacheMetadataEntity?): String {
    if (metadata == null) return stringResource(R.string.market_detail_cache_waiting)
    val updated = metadata.fetchedAt?.let {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
    } ?: stringResource(R.string.market_detail_cache_no_success_time)
    return when (CacheRefreshPolicy.displayStatus(metadata, System.currentTimeMillis())) {
        CacheDisplayStatus.FRESH -> stringResource(R.string.market_detail_cache_updated, updated)
        CacheDisplayStatus.REFRESHING -> stringResource(R.string.market_detail_cache_refreshing, updated)
        CacheDisplayStatus.STALE -> stringResource(R.string.market_detail_cache_stale, updated)
        CacheDisplayStatus.ERROR -> stringResource(R.string.market_detail_cache_error, updated)
        CacheDisplayStatus.UNKNOWN -> stringResource(R.string.market_detail_cache_waiting)
    }
}
