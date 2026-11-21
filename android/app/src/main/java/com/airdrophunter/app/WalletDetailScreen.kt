package com.airdrophunter.app

import androidx.compose.foundation.background
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.AppIconButton
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppHaptic
import com.airdrophunter.app.ui.components.StaggeredListItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconButton(Icons.Default.ArrowBack, context.getString(R.string.browser_back), onBack)
            Column(Modifier.weight(1f)) {
                Text(
                    wallet.wallet_name.ifBlank { context.getString(R.string.wallet_detail_title) },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    shortAddress(wallet.address),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            AppIconButton(Icons.Default.ContentCopy, context.getString(R.string.wallet_copy_address), haptic = AppHaptic.MEDIUM, onClick = {
                clipboard.setText(AnnotatedString(wallet.address))
                android.widget.Toast.makeText(context, context.getString(R.string.wallet_address_copied), android.widget.Toast.LENGTH_SHORT).show()
            })
        }
        WalletActivityPanel(wallet, Modifier.weight(1f), isGuest)
    }
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
            if (result.throttled) android.widget.Toast.makeText(context, context.getString(R.string.wallet_refresh_throttled), android.widget.Toast.LENGTH_SHORT).show()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            errorMessage = error.message ?: context.getString(R.string.wallet_activity_load_failed)
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
                Text(context.getString(R.string.wallet_onchain_activity), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(context.getString(R.string.wallet_readonly_notice), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            AppIconButton(Icons.Default.Refresh, context.getString(R.string.wallet_refresh_activity), onClick = { if (!isLoading) refreshNonce++ }, enabled = !isLoading)
        }
        if (networksUnavailable.isNotEmpty()) {
            Text(
                context.getString(R.string.wallet_networks_partial, networksUnavailable.joinToString()),
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
        } else if (transactions.isEmpty() && errorMessage != null) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(context.getString(R.string.wallet_activity_unavailable), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                AppButton(
                    text = context.getString(R.string.common_retry),
                    onClick = { if (!isLoading) refreshNonce++ },
                    enabled = !isLoading,
                    loading = isLoading,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
            }
        } else if (transactions.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(context.getString(R.string.wallet_no_activity), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(context.getString(R.string.wallet_no_activity_help), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                AppButton(
                    text = context.getString(R.string.wallet_refresh_activity),
                    onClick = { if (!isLoading) refreshNonce++ },
                    enabled = !isLoading,
                    loading = isLoading,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(transactions, key = { _, tx -> "${tx.chain}:${tx.hash}" }) { index, tx ->
                    StaggeredListItem("${tx.chain}:${tx.hash}", index) {
                    val normalizedHash = normalizeWalletActivityHash(tx.chain, tx.hash)
                    val txKey = "${tx.chain.lowercase()}:$normalizedHash"
                    val alreadyPending = txKey in pendingKeys
                    val suggestedGas = WalletTransactionAccountingPolicy.suggestedGasCostUsd(tx)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        "success" -> context.getString(R.string.wallet_tx_success)
                                        "failed" -> context.getString(R.string.wallet_tx_failed_fee)
                                        else -> context.getString(R.string.wallet_tx_pending)
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
                                context.getString(R.string.wallet_tx_hash, shortAddress(tx.hash)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                context.getString(R.string.wallet_tx_time, formatWalletActivityTime(tx.timestamp)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        context.getString(R.string.wallet_tx_fee, tx.gas_cost_native, tx.native_symbol),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        tx.gas_cost_usd?.let { context.getString(R.string.wallet_tx_usd_estimate, it) } ?: context.getString(R.string.wallet_tx_usd_unavailable),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                if (!tx.fee_paid_by_wallet) {
                                    Text(context.getString(R.string.wallet_tx_attribution_review), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            AppButton(
                                text = when {
                                    alreadyPending -> context.getString(R.string.wallet_tx_already_pending)
                                    tx.status.equals("pending", true) -> context.getString(R.string.wallet_tx_waiting_chain)
                                    else -> context.getString(R.string.wallet_tx_add_pending)
                                },
                                onClick = {
                                    if (addingHash != null || alreadyPending || tx.status.equals("pending", true)) return@AppButton
                                    addingHash = tx.hash
                                    scope.launch {
                                        try {
                                            val recorded = accountingRepository.hasRecordedTransaction(
                                                tx.chain, wallet.address, tx.hash
                                            )
                                            if (recorded) {
                                                android.widget.Toast.makeText(context, context.getString(R.string.wallet_tx_already_recorded), android.widget.Toast.LENGTH_SHORT).show()
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
                                                    context.getString(R.string.wallet_tx_pending_added),
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } catch (_: Exception) {
                                            android.widget.Toast.makeText(context, context.getString(R.string.wallet_tx_add_failed), android.widget.Toast.LENGTH_SHORT).show()
                                        } finally {
                                            addingHash = null
                                        }
                                    }
                                },
                                enabled = !alreadyPending && !tx.status.equals("pending", true) && addingHash == null,
                                loading = addingHash == tx.hash,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (suggestedGas.isBlank()) {
                                Text(
                                    context.getString(R.string.wallet_tx_gas_manual_review),
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
}

private fun formatWalletActivityTime(timestampSeconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampSeconds * 1000L))


private fun normalizeWalletActivityHash(network: String, hash: String): String =
    if (network.equals("solana", true)) hash else hash.lowercase()