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
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.components.AppTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WalletDetailScreen(
    wallet: WalletDto,
    onBack: () -> Unit,
    isGuest: Boolean = false
) {
    val clipboard = LocalClipboardManager.current
    var showAssetDialog by remember(wallet.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    wallet.wallet_name.ifBlank { "钱包详情" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    shortAddress(wallet.address),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            AppTextButton(onClick = { showAssetDialog = true }) { Text("资产", color = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = { clipboard.setText(AnnotatedString(wallet.address)) }) {
                Icon(Icons.Default.ContentCopy, "复制地址", tint = MaterialTheme.colorScheme.primary)
            }
        }
        WalletActivityPanel(wallet, Modifier.weight(1f), isGuest)
    }
    if (showAssetDialog) WalletAssetDetailDialog(wallet = wallet, onDismiss = { showAssetDialog = false })
}

@Composable
private fun WalletActivityPanel(
    wallet: WalletDto,
    modifier: Modifier = Modifier,
    isGuest: Boolean = false
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val activityRepository = remember(application) { application.appContainer.walletActivityRepository }
    val accountingRepository = remember(application) { application.appContainer.accountingRepository }
    val pendingBills by accountingRepository.observePendingBills().collectAsStateWithLifecycle(initialValue = emptyList())
    // Observe token identity, not only guest/account status: A -> B must restart this request.
    val observedToken by TokenStore.observeToken(context).collectAsStateWithLifecycle(
        initialValue = if (isGuest) null else TokenStore.readTokenBlocking()
    )
    val sessionToken = observedToken?.takeIf { it.isNotBlank() }
    val sessionIsGuest = sessionToken == null
    val scope = rememberCoroutineScope()
    var transactions by remember(wallet.address, sessionToken) { mutableStateOf<List<WalletTransactionDto>>(emptyList()) }
    var networksUnavailable by remember(wallet.address, sessionToken) { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember(wallet.address, sessionToken) { mutableStateOf(false) }
    var errorMessage by remember(wallet.address, sessionToken) { mutableStateOf<String?>(null) }
    var refreshNonce by remember(wallet.address, sessionToken) { mutableIntStateOf(0) }
    var addingHash by remember(wallet.address, sessionToken) { mutableStateOf<String?>(null) }

    LaunchedEffect(wallet.address, refreshNonce, sessionToken) {
        // Keep last-known-good rows visible while Room is read and the coordinator
        // refreshes. The remember keys above already isolate wallet/session changes.
        isLoading = true
        errorMessage = null
        try {
            activityRepository.readCached(wallet, sessionIsGuest, sessionToken)?.let { cached ->
                transactions = cached.transactions
                networksUnavailable = cached.networksUnavailable
            }
            val result = activityRepository.refresh(wallet, sessionIsGuest, sessionToken, manual = refreshNonce > 0)
            transactions = result.transactions
            networksUnavailable = result.networksUnavailable
            if (result.throttled) android.widget.Toast.makeText(context, "刷新太频繁，请30秒后再试", android.widget.Toast.LENGTH_SHORT).show()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            errorMessage = error.message ?: "读取交易记录失败，请稍后重试"
        } finally {
            isLoading = false
        }
    }

    val pendingKeys = remember(pendingBills, wallet.address, wallet.ecosystem) {
        pendingBills.filter { bill ->
            bill.walletAddress.equals(wallet.address, ignoreCase = wallet.ecosystem == WalletEcosystem.EVM)
        }.map { "${it.network.lowercase()}:${normalizeWalletActivityHash(it.network, it.txHash)}" }.toSet()
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("链上交易", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text("只读查询，不会发起交易或签名", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { if (!isLoading) refreshNonce++ }, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, "刷新交易", tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (networksUnavailable.isNotEmpty()) {
            Text(
                "部分网络暂时不可用：${networksUnavailable.joinToString()}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }
        errorMessage?.let {
            Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }
        if (isLoading && transactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (!isLoading && transactions.isEmpty() && errorMessage == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无链上交易记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactions, key = { "${it.chain}:${it.hash}" }) { tx ->
                    val normalizedHash = normalizeWalletActivityHash(tx.chain, tx.hash)
                    val txKey = "${tx.chain.lowercase()}:$normalizedHash"
                    val alreadyPending = txKey in pendingKeys
                    val suggestedGas = WalletTransactionAccountingPolicy.suggestedGasCostUsd(tx)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tx.chain.uppercase(Locale.US),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    when (tx.status.lowercase()) {
                                        "success" -> "成功"
                                        "failed" -> "失败（已产生费用）"
                                        else -> "待确认"
                                    },
                                    color = if (tx.status.equals("failed", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                "${shortAddress(tx.from_address)} → ${shortAddress(tx.to_address)}",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "哈希：${shortAddress(tx.hash)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                "时间：${formatWalletActivityTime(tx.timestamp)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "费用：${tx.gas_cost_native} ${tx.native_symbol}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        tx.gas_cost_usd?.let { "约 \$${it}" } ?: "美元估值暂不可用",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                if (!tx.fee_paid_by_wallet) {
                                    Text("付款归属待核对", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
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
                                                val id = WalletTransactionIdentity.pendingBillId(
                                                    tx.chain, wallet.address, tx.hash, wallet.ecosystem
                                                )
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
                                                        network = tx.chain,
                                                        assetBreakdownJson = LedgerAssetBreakdownCodec.encode(
                                                            listOfNotNull(
                                                                tx.gas_cost_native.takeIf { it.isNotBlank() && tx.native_symbol.isNotBlank() }?.let {
                                                                    LedgerAssetAmount(
                                                                        kind = "gas",
                                                                        amount_native = it,
                                                                        symbol = tx.native_symbol,
                                                                        decimals = WalletTransactionAccountingPolicy.nativeDecimals(tx),
                                                                        usd_valuation = tx.gas_cost_usd?.toDoubleOrNull(),
                                                                        valuation_timestamp = tx.native_price_timestamp,
                                                                        valuation_source = tx.native_price_source
                                                                    )
                                                                }
                                                            )
                                                        )
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
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
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


private fun normalizeWalletActivityHash(network: String, hash: String): String =
    if (network.equals("solana", true)) hash else hash.lowercase()