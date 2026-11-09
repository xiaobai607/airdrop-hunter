package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun WalletDetailScreen(
    wallet: WalletDto,
    onBack: () -> Unit,
    vm: WalletDetailViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val walletDetailViewModel: WalletDetailViewModel = vm ?: viewModel(
        factory = WalletDetailViewModelFactory(application.appContainer.assetRepository)
    )
    val state by walletDetailViewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(wallet.address) { walletDetailViewModel.loadWallet(wallet.address) }
    DisposableEffect(walletDetailViewModel) { onDispose { walletDetailViewModel.clear() } }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = AppColors.TextPrimary) }
            Column(Modifier.weight(1f)) {
                Text(wallet.wallet_name.ifBlank { "钱包详情" }, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(shortAddress(wallet.address), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(wallet.address)) }) {
                Icon(Icons.Default.ContentCopy, "复制地址", tint = AppColors.Primary)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text("资产") }
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text("交易记录") }
            )
        }
        if (selectedTab == 0) {
            UnifiedAssetContent(
                assets = state.assets,
                isLoading = state.isLoading,
                errorMessage = state.errorMessage,
                onRefresh = walletDetailViewModel::refresh,
                modifier = Modifier.weight(1f)
            )
        } else {
            WalletActivityPanel(wallet, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WalletActivityPanel(
    wallet: WalletDto,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val activityRepository = remember(application) { application.appContainer.walletActivityRepository }
    val accountingRepository = remember(application) { application.appContainer.accountingRepository }
    val pendingBills by accountingRepository.observePendingBills().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var transactions by remember(wallet.address) { mutableStateOf<List<WalletTransactionDto>>(emptyList()) }
    var networksUnavailable by remember(wallet.address) { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember(wallet.address) { mutableStateOf(false) }
    var errorMessage by remember(wallet.address) { mutableStateOf<String?>(null) }
    var refreshNonce by remember(wallet.address) { mutableIntStateOf(0) }
    var addingHash by remember(wallet.address) { mutableStateOf<String?>(null) }

    LaunchedEffect(wallet.address, refreshNonce) {
        isLoading = true
        errorMessage = null
        try {
            val result = activityRepository.load(wallet)
            transactions = result.transactions
            networksUnavailable = result.networksUnavailable
        } catch (error: Exception) {
            errorMessage = error.message ?: "读取交易记录失败，请稍后重试"
        } finally {
            isLoading = false
        }
    }

    val pendingKeys = remember(pendingBills, wallet.address, wallet.ecosystem) {
        pendingBills.filter { bill ->
            bill.walletAddress.equals(wallet.address, ignoreCase = wallet.ecosystem == WalletEcosystem.EVM)
        }.map { "${it.network.lowercase()}:${it.txHash.lowercase()}" }.toSet()
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("链上交易", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("只读查询，不会发起交易或签名", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { if (!isLoading) refreshNonce++ }, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, "刷新交易", tint = AppColors.Primary)
            }
        }
        if (networksUnavailable.isNotEmpty()) {
            Text(
                "部分网络暂时不可用：${networksUnavailable.joinToString()}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = AppColors.Danger,
                style = MaterialTheme.typography.labelSmall
            )
        }
        errorMessage?.let {
            Text(it, modifier = Modifier.padding(16.dp), color = AppColors.Danger)
        }
        if (isLoading && transactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        } else if (!isLoading && transactions.isEmpty() && errorMessage == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无链上交易记录", color = AppColors.TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactions, key = { "${it.chain}:${it.hash}" }) { tx ->
                    val normalizedHash = if (tx.chain.equals("solana", true)) tx.hash else tx.hash.lowercase()
                    val txKey = "${tx.chain.lowercase()}:$normalizedHash"
                    val alreadyPending = txKey in pendingKeys
                    val suggestedGas = WalletTransactionAccountingPolicy.suggestedGasCostUsd(tx)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tx.chain.uppercase(Locale.US),
                                    color = AppColors.Primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    when (tx.status.lowercase()) {
                                        "success" -> "成功"
                                        "failed" -> "失败（已产生费用）"
                                        else -> "待确认"
                                    },
                                    color = if (tx.status.equals("failed", true)) AppColors.Danger else AppColors.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                "${shortAddress(tx.from_address)} → ${shortAddress(tx.to_address)}",
                                color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "哈希：${shortAddress(tx.hash)}",
                                color = AppColors.TextHelper,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                "时间：${formatWalletActivityTime(tx.timestamp)}",
                                color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "费用：${tx.gas_cost_native} ${tx.native_symbol}",
                                        color = AppColors.TextPrimary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        tx.gas_cost_usd?.let { "约 \\${it}" } ?: "美元估值暂不可用",
                                        color = AppColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                if (!tx.fee_paid_by_wallet) {
                                    Text("付款归属待核对", color = AppColors.Danger, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Button(
                                onClick = {
                                    if (addingHash != null || alreadyPending || tx.status.equals("pending", true)) return@Button
                                    addingHash = tx.hash
                                    scope.launch {
                                        try {
                                            val recorded = accountingRepository.hasRecordedTransaction(
                                                tx.chain, wallet.address, tx.hash
                                            )
                                            if (recorded) {
                                                android.widget.Toast.makeText(context, "这笔交易已经记过账", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                val id = UUID.nameUUIDFromBytes(
                                                    "${tx.chain.lowercase()}|${if (wallet.ecosystem == WalletEcosystem.EVM) wallet.address.lowercase() else wallet.address}|${if (tx.chain.equals("solana", true)) tx.hash else tx.hash.lowercase()}".toByteArray()
                                                ).toString()
                                                val prefilledGas = suggestedGas.toDoubleOrNull()
                                                    ?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                                                accountingRepository.addPendingBill(
                                                    PendingBillEntity(
                                                        id = id,
                                                        txHash = tx.hash,
                                                        walletAddress = wallet.address,
                                                        gasCostUsd = prefilledGas,
                                                        interactionMethod = "链上交易导入",
                                                        timestamp = tx.timestamp * 1000L,
                                                        chain = tx.chain,
                                                        network = tx.chain
                                                    )
                                                )
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "已加入待确认账单，请在浏览器记账工具栏选择项目并确认实际金额",
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } catch (_: Exception) {
                                            android.widget.Toast.makeText(context, "加入待确认账单失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
                                        } finally {
                                            addingHash = null
                                        }
                                    }
                                },
                                enabled = !alreadyPending && !tx.status.equals("pending", true) && addingHash == null,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) {
                                Text(
                                    when {
                                        alreadyPending -> "已在待确认账单"
                                        tx.status.equals("pending", true) -> "等待链上确认"
                                        addingHash == tx.hash -> "添加中…"
                                        else -> "加入待确认账单"
                                    }
                                )
                            }
                            if (suggestedGas.isBlank()) {
                                Text(
                                    "系统无法确认该钱包是否承担完整费用，Gas 不会自动填入；入账时请人工核对。",
                                    color = AppColors.TextHelper,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatWalletActivityTime(timestampSeconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampSeconds * 1000L))
