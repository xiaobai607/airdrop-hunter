package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.OkxTickerDetail
import com.airdrophunter.app.ui.theme.AppColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CoinMarketDetailScreen(symbol: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val repository = remember(application) { application.appContainer.okxMarketRepository }
    var detail by remember(symbol) { mutableStateOf<OkxTickerDetail?>(null) }
    var loading by remember(symbol) { mutableStateOf(true) }
    var error by remember(symbol) { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        error = null
    }

    LaunchedEffect(symbol, loading) {
        if (loading) {
            runCatching { repository.fetchSpotTickerDetail(symbol) }
                .onSuccess { detail = it; loading = false }
                .onFailure { error = it.message ?: "行情获取失败"; loading = false }
        }
    }

    val priceFormat = remember {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 8
        }
    }
    Column(
        modifier.fillMaxSize().background(AppColors.Background).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("$symbol / USDT", color = AppColors.TextSecondary, style = MaterialTheme.typography.titleMedium)
        when {
            loading && detail == null -> Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            error != null && detail == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("暂时无法获取行情", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(error.orEmpty(), color = AppColors.TextSecondary)
                Button(onClick = { refresh() }) { Text("重新加载") }
            }
            detail != null -> {
                val value = detail!!
                val positive = value.change24hPercent >= 0
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("最新价格", color = AppColors.TextSecondary)
                        Text("$ " + priceFormat.format(value.lastPrice), color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(String.format(Locale.US, "%+.2f%%（24小时）", value.change24hPercent),
                            color = if (positive) AppColors.Success else AppColors.Danger,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        MarketMetricRow("24小时最高", "$ " + priceFormat.format(value.high24h))
                        HorizontalDivider(color = AppColors.Border)
                        MarketMetricRow("24小时最低", "$ " + priceFormat.format(value.low24h))
                        HorizontalDivider(color = AppColors.Border)
                        MarketMetricRow("24小时成交量", priceFormat.format(value.volume24h) + " $symbol")
                        HorizontalDivider(color = AppColors.Border)
                        MarketMetricRow("24小时成交额", priceFormat.format(value.volumeCurrency24h) + " USDT")
                    }
                }
                Text(
                    "数据来源：OKX · 更新时间：" + SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value.timestamp)),
                    color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall
                )
                OutlinedButton(onClick = { refresh() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("刷新行情")
                }
                if (error != null) Text(error.orEmpty(), color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MarketMetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}
