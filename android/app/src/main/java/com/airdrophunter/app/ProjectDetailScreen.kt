package com.airdrophunter.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.UserInteractionEntity
import com.airdrophunter.app.data.formatLedgerAssetBreakdown
import com.airdrophunter.app.data.aggregateLedgerAssetPeriodTotals
import com.airdrophunter.app.data.aggregateLedgerEstimatePeriodTotals
import com.airdrophunter.app.data.TaskDto
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.data.WalletEcosystem
import com.airdrophunter.app.data.ManualProjectAccountingPolicy
import com.airdrophunter.app.data.ManualWalletAccountingInput
import com.airdrophunter.app.data.ManualRewardInput
import com.airdrophunter.app.data.ManualRewardKind
import com.airdrophunter.app.data.ManualRewardAllocationMode
import kotlinx.coroutines.launch
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.appPressFeedback
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppTextField
import java.util.Locale
import java.math.BigDecimal
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.time.Instant
import java.time.ZoneId
import java.time.DayOfWeek

enum class LedgerPeriod(val label: String) { DAY("日"), WEEK("周"), MONTH("月") }

@Composable
fun ProjectDetailScreen(project: ProjectDto, modifier: Modifier = Modifier, isGuest: Boolean = false,
    vm: ProjectDetailViewModel? = null,
    onOpenCommunity: ((ProjectDto) -> Unit)? = null,
    sessionScopeKey: String = "guest"
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val projectDetailViewModel: ProjectDetailViewModel = vm ?: viewModel(
        factory = ProjectDetailViewModelFactory(application.appContainer.projectsRepository)
    )
    val state by projectDetailViewModel.uiState.collectAsStateWithLifecycle()
    val userProject by CacheStore.observeUserProject(project.id).collectAsStateWithLifecycle(initialValue = null)
    val interactions by CacheStore.observeUserInteractions(project.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var ledgerPeriod by remember(project.id) { mutableStateOf(LedgerPeriod.MONTH) }
    val twitterTitle = stringResource(R.string.twitter)
    var taskName by remember(project.id, sessionScopeKey) { mutableStateOf("") }
    var editingTask by remember(project.id, sessionScopeKey) { mutableStateOf<TaskDto?>(null) }
    var editedTaskName by remember(project.id, sessionScopeKey) { mutableStateOf("") }
    var claimWallets by remember(sessionScopeKey) { mutableStateOf<List<WalletDto>>(emptyList()) }
    var showClaimWalletPicker by remember(sessionScopeKey) { mutableStateOf(false) }
    var isLoadingClaimWallets by remember(sessionScopeKey) { mutableStateOf(false) }
    var assignmentWallets by remember(project.id, sessionScopeKey) { mutableStateOf<List<WalletDto>>(emptyList()) }
    var assignedWalletIds by remember(project.id, sessionScopeKey) { mutableStateOf<Set<String>>(emptySet()) }
    var savedAssignedWalletIds by remember(project.id, sessionScopeKey) { mutableStateOf<Set<String>>(emptySet()) }
    var isLoadingAssignments by remember(project.id, sessionScopeKey) { mutableStateOf(false) }
    var isSavingAssignments by remember(project.id, sessionScopeKey) { mutableStateOf(false) }
    var assignmentError by remember(project.id, sessionScopeKey) { mutableStateOf<String?>(null) }
    var showManualAccounting by remember(project.id, sessionScopeKey) { mutableStateOf(false) }
    var accountingWallets by remember(project.id, sessionScopeKey) { mutableStateOf<List<WalletDto>>(emptyList()) }
    var selectedAccountingWalletIds by remember(project.id, sessionScopeKey) { mutableStateOf<Set<String>>(emptySet()) }
    var accountingBatchId by remember(project.id, sessionScopeKey) { mutableStateOf("") }
    var accountingGasInput by remember(project.id, sessionScopeKey) { mutableStateOf("1.00") }
    var accountingWalletGasAmounts by remember(project.id, sessionScopeKey) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var accountingWalletRewardAmounts by remember(project.id, sessionScopeKey) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var accountingRewardAmount by remember(project.id, sessionScopeKey) { mutableStateOf("") }
    var accountingRewardUnit by remember(project.id, sessionScopeKey) { mutableStateOf("") }
    var accountingRewardKind by remember(project.id, sessionScopeKey) { mutableStateOf(ManualRewardKind.USD) }
    var accountingAllocationMode by remember(project.id, sessionScopeKey) { mutableStateOf(ManualRewardAllocationMode.PER_WALLET) }
    var accountingWalletNetworks by remember(project.id, sessionScopeKey) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var accountingTxHashes by remember(project.id, sessionScopeKey) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isSavingAccounting by remember(project.id, sessionScopeKey) { mutableStateOf(false) }
    var isLoadingAccountingWallets by remember(project.id, sessionScopeKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val projectUsesLocalTasks = isGuest || project.id.startsWith("community:")
    val completed = state.tasks.count { it.is_completed }
    val progress = if (state.tasks.isEmpty()) 0f else completed.toFloat() / state.tasks.size

    LaunchedEffect(project.id, projectUsesLocalTasks, sessionScopeKey) {
        showClaimWalletPicker = false
        editingTask = null
        projectDetailViewModel.load(project.id, projectUsesLocalTasks)
    }
    LaunchedEffect(project.id, sessionScopeKey, isGuest) {
        assignmentWallets = emptyList()
        assignedWalletIds = emptySet()
        savedAssignedWalletIds = emptySet()
        assignmentError = null
        isLoadingAssignments = true
        try {
            assignmentWallets = application.appContainer.walletRepository.load(isGuest)
            val assigned = application.appContainer.projectWalletAssignmentRepository.load(project.id, isGuest)
            assignedWalletIds = assigned
            savedAssignedWalletIds = assigned
        } catch (error: Exception) {
            assignmentError = error.message ?: context.getString(R.string.loc_detail_01)
        } finally {
            isLoadingAssignments = false
        }
    }
    LaunchedEffect(state.claimUrl) {
        val claimUrl = state.claimUrl ?: return@LaunchedEffect
        val parsed = runCatching { Uri.parse(claimUrl) }.getOrNull()
        if (parsed != null && parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()) {
            context.startActivity(Intent(context, BrowserActivity::class.java)
                .putExtra(BrowserActivity.EXTRA_URL, claimUrl)
                .putExtra(BrowserActivity.EXTRA_TITLE, "${project.name} · 资格领取")
                )
        } else Toast.makeText(context, context.getString(R.string.loc_detail_02), Toast.LENGTH_LONG).show()
        projectDetailViewModel.consumeClaimUrl()
    }
    LaunchedEffect(state.claimError) {
        state.claimError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    LazyColumn(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(context.getString(R.string.loc_detail_03), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(project.name, modifier = Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (project.communityDelisted) {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                                Text(context.getString(R.string.loc_detail_04), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (project.description.isNotBlank()) Text(project.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectMetric(Modifier.weight(1f), context.getString(R.string.loc_detail_05), project.expected_income)
                        ProjectMetric(Modifier.weight(1f), context.getString(R.string.loc_detail_06), project.gas_cost)
                        ProjectMetric(Modifier.weight(1f), context.getString(R.string.loc_detail_07), project.actual_income)
                    }
                    project.deadline?.let { Text("${stringResource(R.string.deadline)}: $it",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (project.claim_url.startsWith("https://", ignoreCase = true)) {
                            AppButton(
                                text = context.getString(R.string.loc_detail_08),
                                onClick = {
                                    scope.launch {
                                        isLoadingClaimWallets = true
                                        try {
                                            val availableWallets = application.appContainer.walletRepository.load(isGuest)
                                            claimWallets = availableWallets.sortedByDescending { it.id in assignedWalletIds }
                                            if (claimWallets.isEmpty()) Toast.makeText(context, context.getString(R.string.loc_detail_09), Toast.LENGTH_LONG).show()
                                            else showClaimWalletPicker = true
                                        } catch (error: Exception) {
                                            Toast.makeText(context, error.message ?: context.getString(R.string.loc_detail_10), Toast.LENGTH_LONG).show()
                                        } finally { isLoadingClaimWallets = false }
                                    }
                                },
                                enabled = !isLoadingClaimWallets && !state.isClaimLoading,
                                loading = isLoadingClaimWallets || state.isClaimLoading,
                                fullWidth = false, heightDp = 44, fontSizeSp = 13
                            )
                        }
                        if (project.official_url.startsWith("https://")) AppButton(
                            text = stringResource(R.string.official_website),
                            onClick = {
                                context.startActivity(Intent(context, BrowserActivity::class.java)
                                    .putExtra(BrowserActivity.EXTRA_URL, project.official_url)
                                    .putExtra(BrowserActivity.EXTRA_TITLE, project.name))
                            },
                            style = AppButtonStyle.SECONDARY, fullWidth = false, heightDp = 44, fontSizeSp = 13
                        )
                        if (project.twitter_url.startsWith("https://")) AppButton(
                            text = stringResource(R.string.twitter),
                            onClick = {
                                context.startActivity(Intent(context, BrowserActivity::class.java)
                                    .putExtra(BrowserActivity.EXTRA_URL, project.twitter_url)
                                    .putExtra(BrowserActivity.EXTRA_TITLE, twitterTitle))
                            },
                            style = AppButtonStyle.SECONDARY, fullWidth = false, heightDp = 44, fontSizeSp = 13
                        )
                    }
                    AppButton(
                        text = context.getString(R.string.loc_detail_11),
                        enabled = !isLoadingAccountingWallets && !isSavingAccounting,
                        loading = isLoadingAccountingWallets,
                        onClick = {
                            scope.launch {
                                isLoadingAccountingWallets = true
                                try {
                                    accountingWallets = application.appContainer.walletRepository.load(isGuest)
                                        .sortedByDescending { it.id in assignedWalletIds }
                                    if (accountingWallets.isEmpty()) {
                                        Toast.makeText(context, context.getString(R.string.loc_detail_12), Toast.LENGTH_LONG).show()
                                    } else {
                                        selectedAccountingWalletIds = assignedWalletIds
                                            .intersect(accountingWallets.map { it.id }.toSet())
                                            .ifEmpty { accountingWallets.map { it.id }.toSet() }
                                        accountingTxHashes = emptyMap()
                                        accountingBatchId = UUID.randomUUID().toString()
                                        showManualAccounting = true
                                    }
                                } catch (error: Exception) {
                                    Toast.makeText(context, error.message ?: context.getString(R.string.loc_detail_10), Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoadingAccountingWallets = false
                                }
                            }
                        },
                    )
                    if (project.communityOfficialId != null && !project.communityDelisted && onOpenCommunity != null) {
                        AppButton(
                            text = context.getString(R.string.loc_detail_13),
                            onClick = { onOpenCommunity(project) },
                            style = AppButtonStyle.SECONDARY
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(context.getString(R.string.loc_detail_14), color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(context.getString(R.string.loc_detail_15),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${assignedWalletIds.size} 个", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    when {
                        isLoadingAssignments -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                        assignmentWallets.isEmpty() -> Text(context.getString(R.string.loc_detail_16), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodySmall)
                        else -> assignmentWallets.forEach { wallet ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = wallet.id in assignedWalletIds,
                                    onCheckedChange = { checked ->
                                        assignedWalletIds = if (checked) assignedWalletIds + wallet.id
                                            else assignedWalletIds - wallet.id
                                    },
                                    enabled = !isSavingAssignments
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(wallet.wallet_name, color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${wallet.ecosystem} · ${wallet.address.take(8)}…${wallet.address.takeLast(5)}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    assignmentError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    if (assignedWalletIds != savedAssignedWalletIds) {
                        AppButton(
                            text = context.getString(R.string.loc_detail_17),
                            enabled = !isSavingAssignments && !isLoadingAssignments,
                            loading = isSavingAssignments,
                            onClick = {
                                scope.launch {
                                    isSavingAssignments = true
                                    assignmentError = null
                                    try {
                                        val saved = application.appContainer.projectWalletAssignmentRepository.save(
                                            project.id, assignedWalletIds, isGuest
                                        )
                                        assignedWalletIds = saved
                                        savedAssignedWalletIds = saved
                                        Toast.makeText(context, context.getString(R.string.loc_detail_18), Toast.LENGTH_SHORT).show()
                                    } catch (error: Exception) {
                                        assignmentError = error.message ?: context.getString(R.string.loc_detail_19)
                                    } finally {
                                        isSavingAssignments = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            val zone = remember { ZoneId.systemDefault() }
            val today = remember { Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate() }
            val periodStart = remember(ledgerPeriod, today) {
                when (ledgerPeriod) {
                    LedgerPeriod.DAY -> today
                    LedgerPeriod.WEEK -> today.with(DayOfWeek.MONDAY)
                    LedgerPeriod.MONTH -> today.withDayOfMonth(1)
                }
            }
            val periodInteractions = remember(interactions, periodStart, zone) {
                interactions.filter { row ->
                    Instant.ofEpochMilli(row.timestamp).atZone(zone).toLocalDate() >= periodStart
                }
            }
            val estimateTotals = remember(periodInteractions) { aggregateLedgerEstimatePeriodTotals(periodInteractions) }
            val hasPeriodLedger = periodInteractions.isNotEmpty()
            val periodGas = periodInteractions.sumOf { it.gasCostUsd }
            val periodUsdComplete = periodInteractions.all { it.usdProfitComplete }
            val periodReward = if (hasPeriodLedger && periodUsdComplete) periodInteractions.sumOf { it.actualRewardUsd } else null
            val periodNet = periodReward?.minus(periodGas)
            val gasAssetTotals = remember(periodInteractions) { aggregateLedgerAssetPeriodTotals(periodInteractions, "gas") }
            val rewardAssetTotals = remember(periodInteractions) { aggregateLedgerAssetPeriodTotals(periodInteractions, "reward") }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(context.getString(R.string.loc_detail_20), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    val hasLedger = (userProject?.interactionCount ?: 0) > 0
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectMetric(Modifier.weight(1f), context.getString(R.string.loc_detail_21),
                            userProject?.totalGasCostUsd.takeIf { hasLedger }, emptyLabel = context.getString(R.string.loc_detail_22))
                        ProjectMetric(Modifier.weight(1f), context.getString(R.string.loc_detail_07),
                            userProject?.let { if (hasLedger && it.usdProfitComplete) it.totalActualRewardUsd else null },
                            emptyLabel = if (hasLedger) context.getString(R.string.loc_detail_23) else context.getString(R.string.loc_detail_22))
                        ProjectMetric(Modifier.weight(1f), context.getString(R.string.loc_detail_24),
                            userProject?.let { if (hasLedger && it.usdProfitComplete) it.netProfitUsd else null },
                            emptyLabel = if (hasLedger) context.getString(R.string.loc_detail_23) else context.getString(R.string.loc_detail_22))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        LedgerPeriod.values().forEach { option ->
                            FilterChip(
                                selected = ledgerPeriod == option,
                                onClick = { ledgerPeriod = option },
                                label = { Text(option.label) },
                                shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            context.getString(R.string.loc_detail_25) + (estimateTotals.expectedGasCostUsd?.let { "USD $%.2f".format(Locale.US, it.toDouble()) } ?: context.getString(R.string.loc_detail_26)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            context.getString(R.string.loc_detail_27) + (estimateTotals.expectedRewardUsd?.let { "USD $%.2f".format(Locale.US, it.toDouble()) } ?: context.getString(R.string.loc_detail_26)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LedgerPeriodMetric(Modifier.weight(1f), context.getString(R.string.loc_detail_28),
                            periodGas.takeIf { hasPeriodLedger },
                            if (hasPeriodLedger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            emptyLabel = context.getString(R.string.loc_detail_22))
                        LedgerPeriodMetric(Modifier.weight(1f), context.getString(R.string.loc_detail_07), periodReward,
                            if (periodReward != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            emptyLabel = if (hasPeriodLedger) context.getString(R.string.loc_detail_23) else context.getString(R.string.loc_detail_22))
                        LedgerPeriodMetric(Modifier.weight(1f), context.getString(R.string.loc_detail_29), periodNet,
                            when {
                                periodNet == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                periodNet >= 0.0 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            },
                            emptyLabel = if (hasPeriodLedger) context.getString(R.string.loc_detail_23) else context.getString(R.string.loc_detail_22))
                    }
                    if (gasAssetTotals.isNotEmpty() || rewardAssetTotals.isNotEmpty()) {
                        Text(
                            context.getString(R.string.loc_detail_30),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                        NativeAssetPeriodSection(context.getString(R.string.loc_detail_31), gasAssetTotals)
                        NativeAssetPeriodSection(context.getString(R.string.loc_detail_32), rewardAssetTotals)
                    }
                    Text(
                        context.getString(R.string.loc_detail_33),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(context.getString(R.string.loc_detail_34), color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(context.getString(R.string.loc_detail_35), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Text("$completed / ${state.tasks.size}", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp), color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    AppTextField(taskName, { taskName = it }, context.getString(R.string.loc_detail_36))
                    AppButton(
                        text = context.getString(R.string.loc_detail_37),
                        enabled = taskName.isNotBlank() && !state.isBusy,
                        onClick = { projectDetailViewModel.addTask(project.id, taskName.trim(), projectUsesLocalTasks); taskName = "" }
                    )
                }
            }
        }
        if (state.isLoading && state.tasks.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(context.getString(R.string.loc_detail_38), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (interactions.isEmpty()) {
                    Text(context.getString(R.string.loc_detail_39),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(interactions, key = { "interaction:${it.id}" }) { interaction ->
            InteractionLedgerRow(interaction)
        }
        items(state.tasks.sortedBy { it.step_order }, key = { it.id }) { task ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(task.step_order.toString(), Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Checkbox(checked = task.is_completed,
                        onCheckedChange = { checked -> projectDetailViewModel.setCompleted(project.id, task, checked, projectUsesLocalTasks) })
                    Text(task.task_name, Modifier.weight(1f),
                        color = if (task.is_completed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium)
                    AppTextButton(onClick = {
                        editingTask = task
                        editedTaskName = task.task_name
                    }) { Text(context.getString(R.string.loc_detail_40), color = MaterialTheme.colorScheme.primary) }
                    AppTextButton(onClick = { projectDetailViewModel.deleteTask(project.id, task, projectUsesLocalTasks) }) {
                        Text(context.getString(R.string.loc_detail_41), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (showManualAccounting) {
        val selectedWallets = accountingWallets.filter { it.id in selectedAccountingWalletIds }
        AlertDialog(
            onDismissRequest = { if (!isSavingAccounting) showManualAccounting = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(context.getString(R.string.loc_detail_42) + project.name, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(context.getString(R.string.loc_detail_43) + project.name, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                    Text(context.getString(R.string.loc_detail_44),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                    accountingWallets.forEach { wallet ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = wallet.id in selectedAccountingWalletIds,
                                onCheckedChange = { checked ->
                                    selectedAccountingWalletIds = if (checked) selectedAccountingWalletIds + wallet.id
                                    else selectedAccountingWalletIds - wallet.id
                                },
                                enabled = !isSavingAccounting
                            )
                            Column(Modifier.weight(1f)) {
                                Text(wallet.wallet_name.ifBlank { context.getString(R.string.loc_detail_45) }, color = MaterialTheme.colorScheme.onSurface)
                                Text(wallet.chain + " · " + wallet.address.take(8) + "…" + wallet.address.takeLast(5),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (wallet.id in selectedAccountingWalletIds) {
                            AppTextField(
                                value = accountingWalletGasAmounts[wallet.id].orEmpty(),
                                onValueChange = { accountingWalletGasAmounts = accountingWalletGasAmounts + (wallet.id to it.filter { ch -> ch.isDigit() || ch == '.' }.take(18)) },
                                placeholder = wallet.wallet_name.ifBlank { context.getString(R.string.loc_detail_45) } + " · Gas（USD）",
                                enabled = !isSavingAccounting
                            )
                            if (accountingAllocationMode == ManualRewardAllocationMode.PER_WALLET &&
                                accountingRewardKind != ManualRewardKind.UNKNOWN) {
                                AppTextField(
                                    value = accountingWalletRewardAmounts[wallet.id].orEmpty(),
                                    onValueChange = { accountingWalletRewardAmounts = accountingWalletRewardAmounts + (wallet.id to it.filter { ch -> ch.isDigit() || ch == '.' }.take(24)) },
                                    placeholder = wallet.wallet_name.ifBlank { context.getString(R.string.loc_detail_45) } + context.getString(R.string.loc_detail_46),
                                    enabled = !isSavingAccounting
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("0.5", "1", "5", "10").forEach { amount ->
                            AssistChip(onClick = {
                                accountingWalletGasAmounts = accountingWalletGasAmounts +
                                    selectedWallets.associate { it.id to amount }
                            }, label = { Text(amount + " USD") }, enabled = !isSavingAccounting)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = accountingAllocationMode == ManualRewardAllocationMode.PER_WALLET,
                            onClick = { accountingAllocationMode = ManualRewardAllocationMode.PER_WALLET },
                            enabled = !isSavingAccounting
                        )
                        Text(context.getString(R.string.loc_detail_47), color = MaterialTheme.colorScheme.onSurface)
                        RadioButton(
                            selected = accountingAllocationMode == ManualRewardAllocationMode.EVEN_SPLIT_TOTAL,
                            onClick = { accountingAllocationMode = ManualRewardAllocationMode.EVEN_SPLIT_TOTAL },
                            enabled = !isSavingAccounting
                        )
                        Text(context.getString(R.string.loc_detail_48), color = MaterialTheme.colorScheme.onSurface)
                    }
                    var rewardMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        AppButton(
                            text = context.getString(R.string.loc_detail_49) + when (accountingRewardKind) {
                                ManualRewardKind.USD -> "USD"
                                ManualRewardKind.TOKEN -> context.getString(R.string.loc_detail_50)
                                ManualRewardKind.MANUAL_USD_VALUATION -> context.getString(R.string.loc_detail_51)
                                ManualRewardKind.POINTS -> context.getString(R.string.loc_detail_52)
                                ManualRewardKind.UNISSUED -> context.getString(R.string.loc_detail_53)
                                ManualRewardKind.UNKNOWN -> context.getString(R.string.loc_detail_54)
                            },
                            onClick = { rewardMenuExpanded = true },
                            enabled = !isSavingAccounting,
                            style = AppButtonStyle.SECONDARY,
                            fullWidth = false, heightDp = 44, fontSizeSp = 13
                        )
                        DropdownMenu(expanded = rewardMenuExpanded, onDismissRequest = { rewardMenuExpanded = false }) {
                            ManualRewardKind.values().forEach { kind ->
                                DropdownMenuItem(
                                    text = { Text(kind.name) },
                                    onClick = {
                                    accountingRewardKind = kind
                                    if (kind !in setOf(ManualRewardKind.USD, ManualRewardKind.MANUAL_USD_VALUATION)) {
                                    }
                                    rewardMenuExpanded = false
                                }
                                )
                            }
                        }
                    }
                    if (accountingRewardKind == ManualRewardKind.TOKEN ||
                        accountingRewardKind == ManualRewardKind.POINTS ||
                        accountingRewardKind == ManualRewardKind.UNISSUED) {
                        AppTextField(
                            value = accountingRewardUnit,
                            onValueChange = { accountingRewardUnit = it.take(32) },
                            placeholder = context.getString(R.string.loc_detail_55),
                            enabled = !isSavingAccounting
                        )
                    }
                    if (accountingAllocationMode == ManualRewardAllocationMode.EVEN_SPLIT_TOTAL &&
                        accountingRewardKind != ManualRewardKind.UNKNOWN) {
                        AppTextField(
                            value = accountingRewardAmount,
                            onValueChange = { accountingRewardAmount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(24) },
                            placeholder = context.getString(R.string.loc_detail_56),
                            enabled = !isSavingAccounting
                        )
                    }
                    Text(
                        if (project.communityDelisted) context.getString(R.string.loc_detail_57)
                        else context.getString(R.string.loc_detail_58),
                        color = if (project.communityDelisted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall)
                    selectedWallets.forEach { wallet ->
                        val selectedNetwork = accountingWalletNetworks[wallet.id].orEmpty()
                        var networkMenuExpanded by remember(wallet.id) { mutableStateOf(false) }
                        if (wallet.ecosystem == WalletEcosystem.EVM) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(context.getString(R.string.loc_detail_59), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(8.dp))
                                Box {
                                    AppTextButton(onClick = { networkMenuExpanded = true }, enabled = !isSavingAccounting) {
                                        Text(
                                            when (selectedNetwork) {
                                                "ethereum" -> "Ethereum"
                                                "bsc" -> "BNB Chain"
                                                "polygon" -> "Polygon"
                                                "arbitrum" -> "Arbitrum"
                                                "optimism" -> "Optimism"
                                                "base" -> "Base"
                                                "avalanche" -> "Avalanche"
                                                else -> context.getString(R.string.loc_detail_60)
                                            }
                                        )
                                    }
                                    DropdownMenu(expanded = networkMenuExpanded, onDismissRequest = { networkMenuExpanded = false }) {
                                        listOf(
                                            "ethereum" to "Ethereum",
                                            "bsc" to "BNB Chain",
                                            "polygon" to "Polygon",
                                            "arbitrum" to "Arbitrum",
                                            "optimism" to "Optimism",
                                            "base" to "Base",
                                            "avalanche" to "Avalanche"
                                        ).forEach { (network, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    accountingWalletNetworks = accountingWalletNetworks + (wallet.id to network)
                                                    networkMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(context.getString(R.string.loc_detail_61),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                        }
                        if (!project.communityDelisted && project.communityOfficialId != null &&
                            ManualProjectAccountingPolicy.isTransactionVerificationNetworkSupported(selectedNetwork)) {
                            Text(context.getString(R.string.loc_detail_62),
                                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                        if (!project.communityDelisted && project.communityOfficialId != null &&
                            ManualProjectAccountingPolicy.isTransactionVerificationNetworkSupported(selectedNetwork)) {
                            AppTextField(
                                value = accountingTxHashes[wallet.id].orEmpty(),
                                onValueChange = { value -> accountingTxHashes = accountingTxHashes + (wallet.id to value.trim()) },
                                placeholder = wallet.wallet_name.ifBlank { context.getString(R.string.loc_detail_45) } + " · Tx Hash",
                                enabled = !isSavingAccounting
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    text = context.getString(R.string.loc_detail_63),
                    enabled = !isSavingAccounting && selectedWallets.isNotEmpty(),
                    loading = isSavingAccounting,
                    onClick = {
                    scope.launch {
                        isSavingAccounting = true
                        try {
                            val inputs = selectedWallets.map { wallet ->
                                val gas = BigDecimal(accountingWalletGasAmounts[wallet.id].orEmpty().ifBlank { "0" })
                                val rewardAmountText = if (accountingAllocationMode == ManualRewardAllocationMode.PER_WALLET) {
                                    accountingWalletRewardAmounts[wallet.id].orEmpty()
                                } else accountingRewardAmount
                                val rewardAmount = rewardAmountText.trim().takeIf { it.isNotEmpty() }?.let(::BigDecimal)
                                val reward = when (accountingRewardKind) {
                                    ManualRewardKind.USD -> ManualRewardInput(ManualRewardKind.USD, requireNotNull(rewardAmount) { context.getString(R.string.loc_detail_64) })
                                    ManualRewardKind.TOKEN -> ManualRewardInput(ManualRewardKind.TOKEN, requireNotNull(rewardAmount) { context.getString(R.string.loc_detail_65) }, accountingRewardUnit.trim())
                                    ManualRewardKind.MANUAL_USD_VALUATION -> ManualRewardInput(ManualRewardKind.MANUAL_USD_VALUATION, requireNotNull(rewardAmount) { context.getString(R.string.loc_detail_66) })
                                    ManualRewardKind.POINTS -> ManualRewardInput(ManualRewardKind.POINTS, requireNotNull(rewardAmount) { context.getString(R.string.loc_detail_67) }, accountingRewardUnit.trim())
                                    ManualRewardKind.UNISSUED -> ManualRewardInput(ManualRewardKind.UNISSUED, rewardAmount, accountingRewardUnit.trim().takeIf { it.isNotEmpty() })
                                    ManualRewardKind.UNKNOWN -> ManualRewardInput(ManualRewardKind.UNKNOWN)
                                }
                                ManualWalletAccountingInput(
                                    walletId = wallet.id,
                                    walletAddress = wallet.address,
                                    network = accountingWalletNetworks[wallet.id].orEmpty().ifBlank { wallet.chain.trim().lowercase() },
                                    gasCostUsd = gas,
                                    txHash = accountingTxHashes[wallet.id].orEmpty(),
                                    reward = if (accountingAllocationMode == ManualRewardAllocationMode.PER_WALLET) reward else null,
                                    publicContributionOptIn = !project.communityDelisted && ManualProjectAccountingPolicy.isEligibleForDefaultCommunityContribution(
                                        project.communityOfficialId,
                                        accountingWalletNetworks[wallet.id].orEmpty().ifBlank { wallet.chain.trim().lowercase() },
                                        accountingTxHashes[wallet.id].orEmpty()
                                    )
                                )
                            }
                            val totalReward = if (accountingAllocationMode == ManualRewardAllocationMode.EVEN_SPLIT_TOTAL) {
                                val amount = accountingRewardAmount.trim().takeIf { it.isNotEmpty() }?.let(::BigDecimal)
                                when (accountingRewardKind) {
                                    ManualRewardKind.USD -> ManualRewardInput(ManualRewardKind.USD, requireNotNull(amount) { context.getString(R.string.loc_detail_68) })
                                    ManualRewardKind.TOKEN -> ManualRewardInput(ManualRewardKind.TOKEN, requireNotNull(amount) { context.getString(R.string.loc_detail_69) }, accountingRewardUnit.trim())
                                    ManualRewardKind.MANUAL_USD_VALUATION -> ManualRewardInput(ManualRewardKind.MANUAL_USD_VALUATION, requireNotNull(amount) { context.getString(R.string.loc_detail_70) })
                                    ManualRewardKind.POINTS -> ManualRewardInput(ManualRewardKind.POINTS, requireNotNull(amount) { context.getString(R.string.loc_detail_71) }, accountingRewardUnit.trim())
                                    ManualRewardKind.UNISSUED -> ManualRewardInput(ManualRewardKind.UNISSUED, amount, accountingRewardUnit.trim().takeIf { it.isNotEmpty() })
                                    ManualRewardKind.UNKNOWN -> ManualRewardInput(ManualRewardKind.UNKNOWN)
                                }
                            } else null
                            val batch = ManualProjectAccountingPolicy.prepare(
                                wallets = inputs,
                                allocationMode = accountingAllocationMode,
                                totalReward = totalReward
                            )
                            val saved = CacheStore.recordManualAccountingBatch(
                                accountingBatchId, project.id, project.communityOfficialId, project.name, batch
                            )
                            if (!saved) error(context.getString(R.string.loc_detail_72))
                            showManualAccounting = false
                            Toast.makeText(context,
                                if (project.communityDelisted) context.getString(R.string.loc_detail_73)
                                else if (batch.hasPublicContributionOptIn) context.getString(R.string.loc_detail_74) else context.getString(R.string.loc_detail_75),
                                Toast.LENGTH_LONG).show()
                        } catch (error: Exception) {
                            Toast.makeText(context, error.message ?: context.getString(R.string.loc_detail_76), Toast.LENGTH_LONG).show()
                        } finally {
                            isSavingAccounting = false
                        }
                    }
                })
            },
            dismissButton = {
                AppTextButton(onClick = { if (!isSavingAccounting) showManualAccounting = false }) { Text(context.getString(R.string.loc_detail_77)) }
            }
        )
    }

    editingTask?.let { taskToEdit ->
        AlertDialog(
            onDismissRequest = { editingTask = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(context.getString(R.string.loc_detail_78), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                AppTextField(
                    value = editedTaskName,
                    onValueChange = { if (it.length <= 240) editedTaskName = it },
                    placeholder = context.getString(R.string.loc_detail_79)
                )
            },
            confirmButton = {
                AppButton(
                    text = context.getString(R.string.loc_detail_80),
                    enabled = editedTaskName.isNotBlank() && !state.isBusy,
                    onClick = {
                        projectDetailViewModel.renameTask(
                            project.id, taskToEdit, editedTaskName, projectUsesLocalTasks
                        )
                        editingTask = null
                    }
                )
            },
            dismissButton = { AppTextButton(onClick = { editingTask = null }) { Text(context.getString(R.string.loc_detail_77)) } }
        )
    }

    if (showClaimWalletPicker) {
        AlertDialog(
            onDismissRequest = { showClaimWalletPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(context.getString(R.string.loc_detail_81)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.loc_detail_82),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    claimWallets.forEach { wallet ->
                        Card(
                            modifier = Modifier.fillMaxWidth().appPressFeedback(onClick = {
                            showClaimWalletPicker = false
                            if (isGuest) {
                                val parsed = runCatching { Uri.parse(project.claim_url) }.getOrNull()
                                if (parsed != null && parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank()) {
                                    val url = parsed!!.buildUpon()
                                        .appendQueryParameter("address", wallet.address.ifBlank { wallet.public_key })
                                        .build().toString()
                                    context.startActivity(Intent(context, BrowserActivity::class.java)
                                        .putExtra(BrowserActivity.EXTRA_URL, url)
                                        .putExtra(BrowserActivity.EXTRA_TITLE, "${project.name} · 资格领取")
                )
                                } else Toast.makeText(context, context.getString(R.string.loc_detail_83), Toast.LENGTH_LONG).show()
                            } else projectDetailViewModel.requestClaimUrl(project.id, wallet.id)
                        }, scale = 0.96f, enabled = !state.isClaimLoading),
                            shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(wallet.wallet_name.ifBlank { context.getString(R.string.loc_detail_45) }, color = MaterialTheme.colorScheme.onSurface)
                                Text("${wallet.ecosystem} · ${wallet.address.take(8)}…${wallet.address.takeLast(5)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = { showClaimWalletPicker = false }) { Text(context.getString(R.string.loc_detail_77)) } }
        )
    }

}



@Composable
private fun NativeAssetPeriodSection(
    title: String,
    totals: List<com.airdrophunter.app.data.LedgerAssetPeriodTotal>
) {
    val context = LocalContext.current

    if (totals.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium)
        totals.forEach { total ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${total.network.uppercase(Locale.US)} · ${total.amountNative.stripTrailingZeros().toPlainString()} ${total.symbol}",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    total.usdValuation?.let { "USD估值 $%.2f".format(Locale.US, it) } ?: context.getString(R.string.loc_detail_84),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun InteractionLedgerRow(interaction: UserInteractionEntity) {
    val context = LocalContext.current

    val time = remember(interaction.timestamp) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(interaction.timestamp))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(interaction.network, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
            }
            Text(
                context.getString(R.string.loc_detail_25) + (interaction.expectedGasCostUsd?.let { "USD $%.2f".format(Locale.US, it) } ?: context.getString(R.string.loc_detail_85)),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall
            )
            interaction.expectedRewardUsd?.let {
                Text("预估奖励：USD $%.2f".format(Locale.US, it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.weight(1f).background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        RoundedCornerShape(AppDesignTokens.Radius.Md.dp)
                    ).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(context.getString(R.string.loc_detail_86), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                    Text("−$%.2f".format(Locale.US, interaction.gasCostUsd),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Column(
                    Modifier.weight(1f).background(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        RoundedCornerShape(AppDesignTokens.Radius.Md.dp)
                    ).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(context.getString(R.string.loc_detail_87), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    Text(
                        if (interaction.usdProfitComplete) "+$%.2f".format(Locale.US, interaction.actualRewardUsd) else context.getString(R.string.loc_detail_23),
                        color = if (interaction.usdProfitComplete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
            formatLedgerAssetBreakdown(interaction.assetBreakdownJson).takeIf { it.isNotBlank() }?.let {
                Text("币种明细：$it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            if (interaction.publicContributionOptIn) {
                val statusColor = when (interaction.publicContributionStatus) {
                    "verified" -> MaterialTheme.colorScheme.secondary
                    "rejected" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
                Text(
                    communityContributionStatusLabel(interaction.publicContributionStatus, interaction.publicContributionTier),
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                interaction.publicContributionError?.takeIf {
                    interaction.publicContributionStatus == "rejected" && it.isNotBlank()
                }?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (interaction.txHash.isNotBlank()) {
                Text("交易：${interaction.txHash.take(10)}…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LedgerPeriodMetric(
    modifier: Modifier, label: String, value: Double?, color: Color, emptyLabel: String? = null
) {
    Column(
        modifier.background(MaterialTheme.colorScheme.background, RoundedCornerShape(AppDesignTokens.Radius.Md.dp)).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall, maxLines = 2)
        Text(value?.let { String.format(Locale.US, "$%.2f", it) } ?: emptyLabel, color = color,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProjectMetric(
    modifier: Modifier = Modifier, label: String, value: Double?, emptyLabel: String? = null
) {
    Column(modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(AppDesignTokens.Radius.Md.dp)).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall, maxLines = 2)
        Text(value?.let { String.format(Locale.US, "$%.2f", it) } ?: "估值未完整", color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}
