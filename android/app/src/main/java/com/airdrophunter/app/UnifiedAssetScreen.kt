package com.airdrophunter.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airdrophunter.app.data.UnifiedAssetData
import com.airdrophunter.app.data.UnifiedToken
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.data.WalletTransactionDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.airdrophunter.app.ui.theme.AppColors
import java.text.DecimalFormat

@Composable
fun UnifiedAssetContent(
    assets: UnifiedAssetData?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: (() -> Unit)? = null,
    transactions: List<WalletTransactionDto> = emptyList(),
    transactionsLoading: Boolean = false,
    transactionsError: String? = null,
    transactionSupportMessage: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        if (assets != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("总资产估值", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        assets.totalUsd?.let { "$ " + money(it) } ?: "暂无可靠估值",
                        color = AppColors.TextPrimary, fontSize = 29.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (assets.totalUsd != null) "USD · ${assets.ecosystemName} 数据源" else "${assets.ecosystemName} · 未获取价格数据",
                        color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall
                    )
                }
                if (onRefresh != null) IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "刷新资产", tint = AppColors.Primary)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Surface(color = AppColors.Surface, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text("主币余额", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${quantity(assets.nativeBalance)} ${assets.nativeSymbol}",
                            color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(color = AppColors.Surface, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text("网络", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        Text(assets.ecosystemName, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        when {
            isLoading && assets == null -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            errorMessage != null && assets == null -> Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("暂时无法获取链上资产", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(errorMessage, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
                if (onRefresh != null) TextButton(onClick = onRefresh) { Text("重试") }
            }
            assets != null -> {
                if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppColors.Primary)
                AssetDistributionChart(assets.tokenList)
                Text(
                    "币种资产",
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (assets.tokenList.isEmpty()) {
                        item { Text("当前地址没有检测到有余额的代币", Modifier.padding(vertical = 12.dp), color = AppColors.TextSecondary) }
                    } else {
                        items(assets.tokenList, key = { it.id.ifBlank { it.symbol + it.name } }) { token ->
                            UnifiedTokenRow(token)
                        }
                    }
                    item {
                        Text(
                            "近期链上交易",
                            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                            color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    when {
                        transactionSupportMessage != null -> item {
                            Text(transactionSupportMessage, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 10.dp))
                        }
                        transactionsLoading -> item {
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = AppColors.Primary, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("正在读取真实链上交易…", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        transactionsError != null -> item {
                            Text("交易记录暂时无法读取：$transactionsError", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 10.dp))
                        }
                        transactions.isEmpty() -> item {
                            Text("该地址暂未查询到交易记录", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 10.dp))
                        }
                        else -> items(transactions, key = { "${it.chain}:${it.hash}" }) { transaction ->
                            WalletTransactionRow(transaction)
                        }
                    }
                }
            }
        }
    }
}

private val chartColors = listOf(
    Color(0xFF16A085), Color(0xFF3977D5), Color(0xFFF0A33A),
    Color(0xFF8B65C7), Color(0xFFDB6B78), Color(0xFF9CA3AF)
)

@Composable
private fun AssetDistributionChart(tokens: List<UnifiedToken>) {
    val valued = tokens.filter { (it.usdValue ?: java.math.BigDecimal.ZERO).signum() > 0 }
    if (valued.isEmpty()) return
    val total = valued.fold(java.math.BigDecimal.ZERO) { sum, item -> sum + (item.usdValue ?: java.math.BigDecimal.ZERO) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(104.dp)) {
                var angle = -90f
                valued.forEachIndexed { index, item ->
                    val sweep = ((item.usdValue ?: java.math.BigDecimal.ZERO).toDouble() / total.toDouble() * 360).toFloat()
                    drawArc(chartColors[index % chartColors.size], angle, sweep, false, style = Stroke(15.dp.toPx()))
                    angle += sweep
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                valued.take(5).forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(chartColors[index % chartColors.size]))
                        Spacer(Modifier.width(7.dp))
                        Text(item.symbol, Modifier.weight(1f), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        Text(
                            DecimalFormat("0.#").format((item.usdValue ?: java.math.BigDecimal.ZERO).toDouble() / total.toDouble() * 100) + "%",
                            color = AppColors.TextPrimary, style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedTokenRow(token: UnifiedToken) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!token.logoUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(token.logoUrl, token.symbol, Modifier.size(38.dp).clip(CircleShape))
            } else {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE8F3F1)), contentAlignment = Alignment.Center) {
                    Text(token.symbol.take(1).uppercase(), color = AppColors.Primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(token.symbol, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(token.name, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(quantity(token.quantity), color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(token.usdValue?.let { "$ " + money(it) } ?: "估值暂无", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun money(value: java.math.BigDecimal) = DecimalFormat("#,##0.00").format(value)
private fun quantity(value: java.math.BigDecimal) = DecimalFormat("#,##0.########").format(value)


@Composable
private fun WalletTransactionRow(transaction: WalletTransactionDto) {
    val date = remember(transaction.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(transaction.timestamp * 1000L))
    }
    val fee = transaction.gas_cost_native.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()
        ?: transaction.gas_cost_native
    val usd = transaction.gas_cost_usd?.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${fee} ${transaction.native_symbol}",
                    Modifier.weight(1f),
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (transaction.status == "success") "成功" else "失败",
                    color = if (transaction.status == "success") AppColors.Primary else AppColors.Danger,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(date, Modifier.weight(1f), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                Text(usd?.let { "约 $" + it } ?: "USD 估值暂无", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                if (transaction.fee_paid_by_wallet) "手续费由此钱包支付" else "手续费由交易对方支付",
                color = AppColors.TextHelper,
                style = MaterialTheme.typography.labelSmall
            }
            Text(
                "${transaction.chain.uppercase()} · ${transaction.hash.take(10)}…${transaction.hash.takeLast(6)}",
                color = AppColors.TextHelper,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
