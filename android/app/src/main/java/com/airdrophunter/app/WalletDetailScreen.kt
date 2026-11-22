package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WalletDetailScreen(
    wallet: WalletDto,
    onBack: () -> Unit,
    isGuest: Boolean = false,
    onOpenPendingAccounting: (String) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconButton(Icons.Default.ArrowBack, stringResource(R.string.loc_wallet_detail_01), onBack)
            Column(Modifier.weight(1f)) {
                Text(
                    wallet.wallet_name.ifBlank { stringResource(R.string.loc_wallet_detail_02) },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    shortAddress(wallet.address),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            AppIconButton(Icons.Default.ContentCopy, stringResource(R.string.loc_wallet_detail_03), haptic = AppHaptic.MEDIUM, onClick = {
                clipboard.setText(AnnotatedString(wallet.address))
                android.widget.Toast.makeText(context, context.getString(R.string.loc_wallet_detail_04), android.widget.Toast.LENGTH_SHORT).show()
            })
        }
        WalletAssetsPanel(wallet, isGuest)
        WalletActivityPanel(wallet, Modifier.weight(1f), isGuest, onOpenPendingAccounting)
    }
}

@Composable
private fun WalletActivityPanel(
    wallet: WalletDto,
    modifier: Modifier = Modifier,
    isGuest: Boolean = false,
    onOpenPendingAccounting: (String) -> Unit = {}
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
    val refreshPreferences = remember(context) {
        context.getSharedPreferences("wallet_activity_refresh_settings", android.content.Context.MODE_PRIVATE)
    }
    var cooldownMinutes by remember {
        mutableIntStateOf(refreshPreferences.getInt("cooldown_minutes", 30).let { if (it == 60) 60 else 30 })
    }
    var showCooldownMenu by remember { mutableStateOf(false) }
    var lastSuccessfulRefreshAt by remember(wallet.address, sessionToken) { mutableLongStateOf(0L) }
    var cooldownUntilEpochMillis by remember(wallet.address, sessionToken) { mutableLongStateOf(0L) }
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val cooldownMillis = cooldownMinutes * 60L * 1000L
    val cooldownRemainingMillis = (cooldownUntilEpochMillis - nowEpochMillis).coerceAtLeast(0L)
    val canManuallyRefresh = !isLoading && cooldownRemainingMillis == 0L
    val cooldownLabel = if (cooldownRemainingMillis > 0L) {
        val totalSeconds = (cooldownRemainingMillis + 999L) / 1000L
        stringResource(R.string.loc_wallet_detail_cooldown, totalSeconds / 60L, totalSeconds % 60L)
    } else stringResource(R.string.loc_wallet_detail_05)

    LaunchedEffect(lastSuccessfulRefreshAt, cooldownMinutes) {
        cooldownUntilEpochMillis = if (lastSuccessfulRefreshAt > 0L) {
            lastSuccessfulRefreshAt + cooldownMillis
        } else 0L
    }
    LaunchedEffect(cooldownUntilEpochMillis) {
        while (cooldownUntilEpochMillis > System.currentTimeMillis()) {
            delay(1000L)
            nowEpochMillis = System.currentTimeMillis()
        }
        nowEpochMillis = System.currentTimeMillis()
    }

    var activityFilter by remember(wallet.address, sessionToken) { mutableStateOf("总交易活动") }
    val sentTransactions = remember(transactions, wallet.address, wallet.ecosystem) {
        transactions.filter { isWalletActivityAddress(it.from_address, wallet) }
    }
    val receivedTransactions = remember(transactions, wallet.address, wallet.ecosystem) {
        transactions.filter { isWalletActivityAddress(it.to_address, wallet) }
    }
    val visibleTransactions = remember(transactions, sentTransactions, receivedTransactions, activityFilter) {
        when (activityFilter) {
            "发送" -> sentTransactions
            "接收" -> receivedTransactions
            else -> transactions
        }
    }

    LaunchedEffect(wallet.address, sessionToken) {
        // Opening a wallet is cache-only. Network access happens only after an explicit tap.
        isLoading = true
        try {
            activityRepository.readCached(wallet, sessionIsGuest, sessionToken)?.let { cached ->
                transactions = cached.transactions
                networksUnavailable = cached.networksUnavailable
                lastSuccessfulRefreshAt = cached.fetchedAtEpochMillis
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            errorMessage = error.message ?: context.getString(R.string.loc_wallet_detail_09)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(wallet.address, refreshNonce, sessionToken) {
        if (refreshNonce == 0) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        try {
            val result = activityRepository.refresh(
                wallet,
                sessionIsGuest,
                sessionToken,
                manual = true,
                manualCooldownMillis = cooldownMillis
            )
            transactions = result.transactions
            networksUnavailable = result.networksUnavailable
            if (result.throttled) {
                android.widget.Toast.makeText(context, context.getString(R.string.loc_wallet_detail_10), android.widget.Toast.LENGTH_SHORT).show()
            } else if (!result.stale && result.errorMessage == null) {
                lastSuccessfulRefreshAt = result.fetchedAtEpochMillis
                nowEpochMillis = System.currentTimeMillis()
            } else {
                errorMessage = result.errorMessage ?: context.getString(R.string.loc_wallet_detail_11)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            errorMessage = error.message ?: context.getString(R.string.loc_wallet_detail_12)
        } finally {
            isLoading = false
        }
    }

    val walletPendingBills = remember(pendingBills, wallet.address, wallet.ecosystem) {
        pendingBills.filter { bill ->
            !bill.txHash.startsWith("MOCK-") && bill.walletAddress.equals(wallet.address, ignoreCase = wallet.ecosystem == WalletEcosystem.EVM)
        }
    }
    val pendingKeys = remember(pendingBills, wallet.address, wallet.ecosystem) {
        pendingBills.filter { bill ->
            bill.walletAddress.equals(wallet.address, ignoreCase = wallet.ecosystem == WalletEcosystem.EVM)
        }.map { "${it.network.lowercase()}:${normalizeWalletActivityHash(it.network, it.txHash)}" }.toSet()
    }

    Column(modifier.fillMaxSize()) {
        if (walletPendingBills.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.loc_wallet_detail_pending_count, walletPendingBills.size), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.loc_wallet_detail_13), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    AppButton(stringResource(R.string.loc_wallet_detail_14), onClick = { onOpenPendingAccounting(wallet.address) }, fullWidth = false, heightDp = 38, fontSizeSp = 12)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.loc_wallet_detail_15), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.loc_wallet_manual_refresh, cooldownLabel), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Box {
                TextButton(onClick = { showCooldownMenu = true }) {
                    Text(stringResource(R.string.loc_wallet_detail_minutes, cooldownMinutes), style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(expanded = showCooldownMenu, onDismissRequest = { showCooldownMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.loc_wallet_detail_minutes, 30)) },
                        onClick = {
                            cooldownMinutes = 30
                            refreshPreferences.edit().putInt("cooldown_minutes", 30).apply()
                            showCooldownMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.loc_wallet_detail_hours, 1)) },
                        onClick = {
                            cooldownMinutes = 60
                            refreshPreferences.edit().putInt("cooldown_minutes", 60).apply()
                            showCooldownMenu = false
                        }
                    )
                }
            }
            AppIconButton(
                Icons.Default.Refresh,
                if (canManuallyRefresh) stringResource(R.string.loc_wallet_detail_16) else stringResource(R.string.loc_wallet_detail_17),
                onClick = { if (canManuallyRefresh) refreshNonce++ },
                enabled = canManuallyRefresh
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "总交易活动" to transactions.size,
                "发送" to sentTransactions.size,
                "接收" to receivedTransactions.size
            ).forEach { (label, count) ->
                FilterChip(
                    selected = activityFilter == label,
                    onClick = { activityFilter = label },
                    label = { Text("${when (label) { "总交易活动" -> stringResource(R.string.loc_wallet_detail_06); "发送" -> stringResource(R.string.loc_wallet_detail_07); else -> stringResource(R.string.loc_wallet_detail_08) }} $count") },
                    shape = RoundedCornerShape(50)
                )
            }
        }
        if (networksUnavailable.isNotEmpty()) {
            Text(
                stringResource(R.string.loc_wallet_detail_networks_unavailable, networksUnavailable.joinToString()),
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
                Text(stringResource(R.string.loc_wallet_detail_18), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                AppButton(
                    text = stringResource(R.string.loc_wallet_detail_19),
                    onClick = { if (!isLoading) refreshNonce++ },
                    enabled = canManuallyRefresh,
                    loading = isLoading,
                    modifier = Modifier.widthIn(min = 132.dp)
                )
            }
        } else if (visibleTransactions.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (transactions.isEmpty()) stringResource(R.string.loc_wallet_detail_20) else stringResource(R.string.loc_wallet_detail_21),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (transactions.isEmpty()) stringResource(R.string.loc_wallet_detail_empty_address) else stringResource(R.string.loc_wallet_detail_empty_filter),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                if (transactions.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    AppButton(
                        text = stringResource(R.string.loc_wallet_detail_16),
                        onClick = { if (!isLoading) refreshNonce++ },
                        enabled = canManuallyRefresh,
                        loading = isLoading,
                        modifier = Modifier.widthIn(min = 132.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(visibleTransactions, key = { _, tx -> "${tx.chain}:${tx.hash}" }) { index, tx ->
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
                                        "success" -> stringResource(R.string.loc_wallet_detail_26)
                                        "failed" -> stringResource(R.string.loc_wallet_detail_27)
                                        else -> stringResource(R.string.loc_wallet_detail_28)
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
                                stringResource(R.string.loc_wallet_detail_hash, shortAddress(tx.hash)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                stringResource(R.string.loc_wallet_detail_time, formatWalletActivityTime(tx.timestamp)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.loc_wallet_detail_fee, tx.gas_cost_native, tx.native_symbol),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        tx.gas_cost_usd?.let { stringResource(R.string.loc_wallet_detail_approx_usd, it) } ?: stringResource(R.string.loc_wallet_detail_25),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                if (!tx.fee_paid_by_wallet) {
                                    Text(stringResource(R.string.loc_wallet_detail_29), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            AppButton(
                                text = when {
                                    alreadyPending -> stringResource(R.string.loc_wallet_detail_30)
                                    tx.status.equals("pending", true) -> stringResource(R.string.loc_wallet_detail_31)
                                    else -> stringResource(R.string.loc_wallet_detail_32)
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
                                                android.widget.Toast.makeText(context, context.getString(R.string.loc_wallet_detail_33), android.widget.Toast.LENGTH_SHORT).show()
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
                                                    context.getString(R.string.loc_wallet_detail_added_pending),
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } catch (_: Exception) {
                                            android.widget.Toast.makeText(context, context.getString(R.string.loc_wallet_detail_34), android.widget.Toast.LENGTH_SHORT).show()
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
                                    stringResource(R.string.loc_wallet_detail_35),
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


@Composable
private fun WalletAssetsPanel(wallet: WalletDto, isGuest: Boolean) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val repository = remember(application) { application.appContainer.walletAssetRepository }
    val observedToken by TokenStore.observeToken(context).collectAsStateWithLifecycle(
        initialValue = if (isGuest) null else TokenStore.readTokenBlocking()
    )
    val token = observedToken?.takeIf { it.isNotBlank() }
    val chains = remember(wallet.ecosystem) {
        when (wallet.ecosystem) {
            WalletEcosystem.EVM -> listOf("ethereum", "bsc", "polygon", "arbitrum", "optimism", "base", "avalanche")
            WalletEcosystem.TRON -> listOf("tron")
            WalletEcosystem.SOL -> listOf("solana")
            WalletEcosystem.BTC -> listOf("bitcoin")
            else -> emptyList()
        }
    }
    var assets by remember(wallet.id, token) { mutableStateOf<List<UnifiedAsset>>(emptyList()) }
    var isLoading by remember(wallet.id, token) { mutableStateOf(false) }
    var error by remember(wallet.id, token) { mutableStateOf<String?>(null) }
    var refreshNonce by remember(wallet.id, token) { mutableIntStateOf(0) }
    var lastUpdated by remember(wallet.id, token) { mutableLongStateOf(0L) }

    LaunchedEffect(wallet.id, wallet.address, token, refreshNonce) {
        if (chains.isEmpty()) {
            error = context.getString(R.string.loc_wallet_detail_36)
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        val collected = linkedMapOf<String, UnifiedAsset>()
        val failures = mutableListOf<String>()
        try {
            for (chain in chains) {
                repository.readCached(wallet.id, chain, token)?.let { cached ->
                    cached.assets.forEach { collected["${it.chain}:${it.assetId}"] = it }
                    assets = collected.values.toList()
                    if (cached.fetchedAt > lastUpdated) lastUpdated = cached.fetchedAt
                }
                try {
                    val result = repository.refresh(
                        walletId = wallet.id,
                        address = wallet.address,
                        chain = chain,
                        accessToken = token,
                        manual = refreshNonce > 0
                    )
                    result.assets.forEach { collected["${it.chain}:${it.assetId}"] = it }
                    assets = collected.values.toList()
                    if (result.fetchedAt > lastUpdated) lastUpdated = result.fetchedAt
                    if (!result.errorMessage.isNullOrBlank()) failures += chain
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failures += chain
                }
            }
            if (failures.isNotEmpty()) {
                error = context.getString(R.string.loc_wallet_detail_networks_unavailable, failures.joinToString())
            }
            if (assets.isEmpty() && failures.isEmpty()) error = context.getString(R.string.loc_wallet_detail_37)
        } finally {
            isLoading = false
        }
    }

    val valued = assets.filter { it.isVerified && it.usdValue != null }
    val estimatedTotal = valued.sumOf { it.usdValue ?: 0.0 }
    val displayAssets = assets.sortedWith(compareByDescending<UnifiedAsset> { it.usdValue ?: -1.0 }).take(5)
    val networkCount = assets.map { it.chain }.distinct().size

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.loc_wallet_detail_38), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (valued.isNotEmpty()) stringResource(R.string.loc_wallet_detail_assets_valued, valued.size) else stringResource(R.string.loc_wallet_detail_39),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (valued.isNotEmpty()) {
                    Text(
                        "$" + String.format(Locale.US, "%,.2f", estimatedTotal),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                AppIconButton(
                    Icons.Default.Refresh,
                    stringResource(R.string.loc_wallet_detail_22),
                    onClick = { if (!isLoading) refreshNonce++ },
                    enabled = !isLoading
                )
            }
            if (isLoading && assets.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.loc_wallet_detail_23), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            displayAssets.forEach { asset ->
                val networkLabel = when (asset.chain) {
                    "ethereum" -> "Ethereum"
                    "bsc" -> "BNB Chain"
                    "polygon" -> "Polygon"
                    "arbitrum" -> "Arbitrum"
                    "optimism" -> "Optimism"
                    "base" -> "Base"
                    "avalanche" -> "Avalanche"
                    "tron" -> "TRON"
                    "solana" -> "Solana"
                    "bitcoin" -> "Bitcoin"
                    else -> asset.chain
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            asset.symbol + if (asset.isVerified) "" else stringResource(R.string.loc_wallet_detail_asset_unverified),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            networkLabel + " · " + asset.name,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            asset.formattedQuantity + " " + asset.symbol,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            if (asset.isVerified && asset.usdValue != null) "$" + String.format(Locale.US, "%,.2f", asset.usdValue)
                            else stringResource(R.string.loc_wallet_detail_25),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            if (assets.size > displayAssets.size) {
                Text(
                    stringResource(R.string.loc_wallet_detail_asset_summary, assets.size, networkCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (lastUpdated > 0L) {
                Text(
                    stringResource(R.string.loc_wallet_detail_updated, SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(lastUpdated))),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                if (wallet.ecosystem == WalletEcosystem.TRON) stringResource(R.string.loc_wallet_detail_tron_assets)
                else stringResource(R.string.loc_wallet_detail_40),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun formatWalletActivityTime(timestampSeconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampSeconds * 1000L))


private fun normalizeWalletActivityHash(network: String, hash: String): String =
    if (network.equals("solana", true)) hash else hash.lowercase()
private fun isWalletActivityAddress(candidate: String, wallet: WalletDto): Boolean {
    if (candidate.isBlank()) return false
    return if (wallet.ecosystem == WalletEcosystem.EVM) {
        candidate.equals(wallet.address, ignoreCase = true)
    } else {
        candidate == wallet.address
    }
}
