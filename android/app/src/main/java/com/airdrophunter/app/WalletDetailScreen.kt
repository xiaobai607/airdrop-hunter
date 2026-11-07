package com.airdrophunter.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.airdrophunter.app.data.TronTokenBalance
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.data.WalletEcosystem
import com.airdrophunter.app.ui.theme.AppColors
import java.math.BigDecimal
import java.text.DecimalFormat

private val assetChartColors = listOf(
    Color(0xFF16A085), Color(0xFF3977D5), Color(0xFFF0A33A),
    Color(0xFF8B65C7), Color(0xFFDB6B78), Color(0xFF9CA3AF)
)

@Composable
fun WalletDetailScreen(
    wallet: WalletDto,
    onBack: () -> Unit,
    vm: WalletDetailViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var selectedToken by remember(wallet.address) { mutableStateOf<TronTokenBalance?>(null) }

    LaunchedEffect(wallet.address) {
        if (wallet.ecosystem == WalletEcosystem.TRON) vm.loadWallet(wallet.address)
    }
    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(AppColors.Background)
    ) {
        WalletDetailTopBar(
            wallet = wallet,
            onBack = onBack,
            onCopy = { clipboard.setText(AnnotatedString(wallet.address)) }
        )

        if (wallet.ecosystem != WalletEcosystem.TRON) {
            UnsupportedNetworkContent(wallet)
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NetworkChip("TRON", selected = true)
            NetworkChip("Ethereum · 接入中", selected = false)
            NetworkChip("BNB Chain · 接入中", selected = false)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("总资产估值", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = state.totalUsd?.let { "$ " + formatMoney(it) } ?: "—",
                    color = AppColors.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("USD · TRONSCAN 返回", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = vm::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新资产", tint = AppColors.Primary)
            }
        }

        when {
            state.isLoading && state.tokenList.isEmpty() -> {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            }
            state.errorMessage != null && state.tokenList.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("暂时无法获取链上资产", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(state.errorMessage.orEmpty(), color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = vm::refresh) { Text("重试") }
                }
            }
            else -> {
                AssetDistributionChart(state.tokenList)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("币种资产", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (state.isLoading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AppColors.Primary)
                    }
                }
                if (state.tokenList.isEmpty()) {
                    Text(
                        "当前地址没有检测到有余额的代币",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        color = AppColors.TextSecondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = state.tokenList,
                            key = { token -> token.tokenId.ifBlank { token.symbol + token.name } }
                        ) { token ->
                            TokenAssetRow(token = token, onClick = { selectedToken = token })
                        }
                    }
                }
            }
        }
    }

    selectedToken?.let { token ->
        TokenDetailDialog(token = token, onDismiss = { selectedToken = null })
    }
}

@Composable
private fun WalletDetailTopBar(wallet: WalletDto, onBack: () -> Unit, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(wallet.wallet_name.ifBlank { "钱包资产" }, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(shortAddress(wallet.address), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "复制地址", tint = AppColors.Primary)
        }
    }
}

@Composable
private fun UnsupportedNetworkContent(wallet: WalletDto) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DynamicChainIcon(wallet.ecosystem, Modifier.size(54.dp))
        Spacer(Modifier.height(14.dp))
        Text("该网络余额查询尚未接入", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Text("目前先开放 TRON 主网真实资产查询。", color = AppColors.TextSecondary)
    }
}

@Composable
private fun NetworkChip(label: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) AppColors.Primary else AppColors.Surface
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (selected) Color.White else AppColors.TextHelper,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun AssetDistributionChart(tokens: List<TronTokenBalance>) {
    val valuedTokens = tokens.filter { (it.usdValue ?: BigDecimal.ZERO).signum() > 0 }
    if (valuedTokens.isEmpty()) return

    val total = valuedTokens.fold(BigDecimal.ZERO) { sum, token ->
        sum + (token.usdValue ?: BigDecimal.ZERO)
    }
    if (total.signum() <= 0) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(108.dp)) {
                var startAngle = -90f
                valuedTokens.forEachIndexed { index, token ->
                    val sweep = ((token.usdValue ?: BigDecimal.ZERO).toDouble() / total.toDouble() * 360.0).toFloat()
                    if (sweep > 0f) {
                        drawArc(
                            color = assetChartColors[index % assetChartColors.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 16.dp.toPx())
                        )
                    }
                    startAngle += sweep
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                valuedTokens.take(5).forEachIndexed { index, token ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(assetChartColors[index % assetChartColors.size])
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            token.symbol,
                            modifier = Modifier.weight(1f),
                            color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                        val percent = token.usdValue!!.toDouble() / total.toDouble() * 100.0
                        Text(
                            DecimalFormat("0.#").format(percent) + "%",
                            color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenAssetRow(token: TronTokenBalance, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!token.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = token.logoUrl,
                    contentDescription = token.symbol,
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE8F3F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(token.symbol.take(1).uppercase(), color = AppColors.Primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(token.symbol, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(token.name, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(formatQuantity(token.quantity), color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    token.usdValue?.let { "$ " + formatMoney(it) } ?: "估值暂无",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun TokenDetailDialog(token: TronTokenBalance, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(token.name, color = AppColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("网络：TRON", color = AppColors.TextSecondary)
                Text("代币：${token.symbol}", color = AppColors.TextSecondary)
                Text("持有数量：${formatQuantity(token.quantity)}", color = AppColors.TextPrimary)
                Text(
                    "美元估值：${token.usdValue?.let { "$ " + formatMoney(it) } ?: "暂无真实估值"}",
                    color = AppColors.TextPrimary
                )
                token.priceUsd?.let { Text("单价：$ ${formatMoney(it)}", color = AppColors.TextSecondary) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        containerColor = AppColors.Surface
    )
}

private fun formatMoney(value: BigDecimal): String = DecimalFormat("#,##0.00").format(value)
private fun formatQuantity(value: BigDecimal): String = DecimalFormat("#,##0.########").format(value)
