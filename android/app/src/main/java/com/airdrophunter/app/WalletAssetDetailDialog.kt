package com.airdrophunter.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.CancellationException

private val ASSET_NETWORKS = listOf(
    "ethereum" to "Ethereum", "bsc" to "BNB Chain", "polygon" to "Polygon",
    "arbitrum" to "Arbitrum", "optimism" to "Optimism", "base" to "Base",
    "avalanche" to "Avalanche"
)

@Composable
fun WalletAssetDetailDialog(wallet: WalletDto, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { WalletAssetRepository(RetrofitWalletAssetsRemoteDataSource(ApiClient.service)) }
    val observedToken by TokenStore.observeToken(context).collectAsStateWithLifecycle(
        initialValue = TokenStore.readTokenBlocking()
    )
    val sessionToken = observedToken?.takeIf { it.isNotBlank() }
    val networkOptions = when (wallet.ecosystem) {
        WalletEcosystem.EVM -> ASSET_NETWORKS
        WalletEcosystem.BTC -> listOf("bitcoin" to "Bitcoin")
        WalletEcosystem.SOL -> listOf("solana" to "Solana")
        else -> emptyList()
    }
    var selectedNetwork by remember(wallet.id) { mutableStateOf(networkOptions.firstOrNull()?.first.orEmpty()) }
    var networkMenu by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var snapshot by remember(wallet.id, selectedNetwork, sessionToken) { mutableStateOf<WalletAssetSnapshot?>(null) }
    var error by remember(wallet.id, selectedNetwork, sessionToken) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(wallet.id, wallet.address, selectedNetwork, sessionToken, reload) {
        if (selectedNetwork.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        snapshot = repository.readCached(wallet.id, selectedNetwork, sessionToken)
        try {
            snapshot = repository.refresh(wallet.id, wallet.address, selectedNetwork, sessionToken, manual = reload > 0)
            error = snapshot?.errorMessage
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "读取资产失败，请稍后重试"
        } finally {
            loading = false
        }
    }

    val allAssets = snapshot?.assets.orEmpty()
    val visible = visibleUnifiedAssets(allAssets)
    val hidden = hiddenUnifiedAssets(allAssets)
    val displayed = if (showHidden) visible + hidden else visible
    val distribution = unifiedAssetDistribution(allAssets, selectedNetwork)
    val chartColors = listOf(
        Color(0xFF3B82F6), Color(0xFF16A34A), Color(0xFFF59E0B),
        Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF64748B)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text("钱包资产明细", color = AppColors.TextPrimary) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (wallet.ecosystem == WalletEcosystem.EVM) {
                    Column {
                        OutlinedButton(onClick = { networkMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(networkOptions.firstOrNull { it.first == selectedNetwork }?.second ?: "选择网络")
                        }
                        DropdownMenu(expanded = networkMenu, onDismissRequest = { networkMenu = false }) {
                            networkOptions.forEach { pair ->
                                DropdownMenuItem(text = { Text(pair.second) }, onClick = {
                                    selectedNetwork = pair.first
                                    networkMenu = false
                                    showHidden = false
                                })
                            }
                        }
                    }
                }
                Text(
                    "仅查询当前钱包在所选网络的公开余额，不会连接钱包或发起交易。",
                    color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall
                )
                if (loading && snapshot == null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = AppColors.Primary)
                    }
                }
                error?.let { Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall) }
                snapshot?.coverageNote?.let {
                    Text(it, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if (distribution.isNotEmpty() && selectedNetwork != "bitcoin") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Canvas(Modifier.size(112.dp)) {
                            var start = -90f
                            val total = distribution.sumOf { it.usdValue }.coerceAtLeast(0.000001)
                            distribution.forEachIndexed { index, slice ->
                                val sweep = (slice.usdValue / total * 360.0).toFloat()
                                drawArc(chartColors[index % chartColors.size], start, sweep, true)
                                start += sweep
                            }
                            drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension * 0.23f)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            distribution.forEachIndexed { index, slice ->
                                Text(
                                    slice.label + " · $" + "%.2f".format(slice.usdValue),
                                    color = chartColors[index % chartColors.size],
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("资产列表", color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showHidden = !showHidden }, enabled = hidden.isNotEmpty()) {
                        Text(if (showHidden) "隐藏低可信资产" else "查看隐藏资产")
                    }
                }
                if (displayed.isEmpty() && !loading) {
                    Text(
                        if (allAssets.isEmpty()) "该网络暂未读取到余额" else "没有符合展示条件的资产",
                        color = AppColors.TextSecondary
                    )
                }
                displayed.forEach { asset ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(asset.symbol, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                            Text(asset.formattedQuantity, color = AppColors.TextPrimary)
                        }
                        Text(asset.name, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                asset.usdValue?.let { "$" + "%.2f".format(it) } ?: "暂无可靠 USD 报价",
                                color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (!asset.isVerified) Text("未核验", color = AppColors.Danger, style = MaterialTheme.typography.labelSmall)
                            else if (asset.priceUsd == null) Text("未定价", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                snapshot?.let {
                    Text(
                        "数据来源：" + it.provider + " · " + if (it.stale) "缓存" else "已更新" +
                            " · " + java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date(it.fetchedAt)),
                        color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("关闭") }
                Button(onClick = { if (!loading) reload++ }, enabled = !loading) {
                    Text(if (loading) "刷新中…" else "刷新")
                }
            }
        }
    )
}
