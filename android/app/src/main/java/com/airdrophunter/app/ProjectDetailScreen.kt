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
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.components.AppTextButton
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
            assignmentError = error.message ?: "读取项目钱包关联失败"
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
                .putExtra(BrowserActivity.EXTRA_TITLE, "${project.name} · 资格领取"))
        } else Toast.makeText(context, "领取链接无效，已阻止打开", Toast.LENGTH_LONG).show()
        projectDetailViewModel.consumeClaimUrl()
    }
    LaunchedEffect(state.claimError) {
        state.claimError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    LazyColumn(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(project.name, color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (project.communityDelisted) {
                            Surface(color = Color(0xFFFFE8E8), shape = RoundedCornerShape(999.dp)) {
                                Text("社区已下架 · 私人模式", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = Color(0xFFB42318), style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (project.description.isNotBlank()) Text(project.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectMetric(Modifier.weight(1f), "预期奖励", project.expected_income)
                        ProjectMetric(Modifier.weight(1f), "Gas 费用", project.gas_cost)
                        ProjectMetric(Modifier.weight(1f), "实际收益", project.actual_income)
                    }
                    project.deadline?.let { Text("${stringResource(R.string.deadline)}: $it",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (project.claim_url.startsWith("https://", ignoreCase = true)) {
                            AppButton(
                                text = "选择钱包并领取",
                                onClick = {
                                    scope.launch {
                                        isLoadingClaimWallets = true
                                        try {
                                            val availableWallets = application.appContainer.walletRepository.load(isGuest)
                                            claimWallets = availableWallets.sortedByDescending { it.id in assignedWalletIds }
                                            if (claimWallets.isEmpty()) Toast.makeText(context, "请先在钱包管理中添加公开地址", Toast.LENGTH_LONG).show()
                                            else showClaimWalletPicker = true
                                        } catch (error: Exception) {
                                            Toast.makeText(context, error.message ?: "读取钱包失败", Toast.LENGTH_LONG).show()
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
                        text = "＋ 记一笔",
                        enabled = !isLoadingAccountingWallets && !isSavingAccounting,
                        loading = isLoadingAccountingWallets,
                        onClick = {
                            scope.launch {
                                isLoadingAccountingWallets = true
                                try {
                                    accountingWallets = application.appContainer.walletRepository.load(isGuest)
                                        .sortedByDescending { it.id in assignedWalletIds }
                                    if (accountingWallets.isEmpty()) {
                                        Toast.makeText(context, "请先添加公开钱包地址，再进行记账", Toast.LENGTH_LONG).show()
                                    } else {
                                        selectedAccountingWalletIds = assignedWalletIds
                                            .intersect(accountingWallets.map { it.id }.toSet())
                                            .ifEmpty { accountingWallets.map { it.id }.toSet() }
                                        accountingTxHashes = emptyMap()
                                        accountingBatchId = UUID.randomUUID().toString()
                                        showManualAccounting = true
                                    }
                                } catch (error: Exception) {
                                    Toast.makeText(context, error.message ?: "读取钱包失败", Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoadingAccountingWallets = false
                                }
                            }
                        },
                    )
                    if (project.communityOfficialId != null && !project.communityDelisted && onOpenCommunity != null) {
                        AppButton(
                            text = "查看全网大盘数据",
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
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("项目关联钱包", color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("可为同一项目绑定多个公开地址；领取时优先显示已关联钱包",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${assignedWalletIds.size} 个", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    when {
                        isLoadingAssignments -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                        assignmentWallets.isEmpty() -> Text("还没有钱包地址，请先在钱包管理中添加。", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
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
                            text = "保存钱包关联",
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
                                        Toast.makeText(context, "项目钱包关联已保存", Toast.LENGTH_SHORT).show()
                                    } catch (error: Exception) {
                                        assignmentError = error.message ?: "保存项目钱包关联失败"
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
            val periodGas = periodInteractions.sumOf { it.gasCostUsd }
            val periodUsdComplete = periodInteractions.all { it.usdProfitComplete }
            val periodReward = if (periodUsdComplete) periodInteractions.sumOf { it.actualRewardUsd } else null
            val periodNet = periodReward?.minus(periodGas)
            val gasAssetTotals = remember(periodInteractions) { aggregateLedgerAssetPeriodTotals(periodInteractions, "gas") }
            val rewardAssetTotals = remember(periodInteractions) { aggregateLedgerAssetPeriodTotals(periodInteractions, "reward") }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("个人项目账本", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectMetric(Modifier.weight(1f), "累计 Gas", userProject?.totalGasCostUsd ?: 0.0)
                        ProjectMetric(Modifier.weight(1f), "实际收益",
                            userProject?.let { if (it.usdProfitComplete) it.totalActualRewardUsd else null } ?: 0.0)
                        ProjectMetric(Modifier.weight(1f), "净利润",
                            userProject?.let { if (it.usdProfitComplete) it.netProfitUsd else null } ?: 0.0)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        LedgerPeriod.values().forEach { option ->
                            FilterChip(
                                selected = ledgerPeriod == option,
                                onClick = { ledgerPeriod = option },
                                label = { Text(option.label) },
                                shape = RoundedCornerShape(999.dp),
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
                            "预估 Gas：" + (estimateTotals.expectedGasCostUsd?.let { "USD $%.2f".format(Locale.US, it.toDouble()) } ?: "暂无完整估值"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            "预估奖励：" + (estimateTotals.expectedRewardUsd?.let { "USD $%.2f".format(Locale.US, it.toDouble()) } ?: "暂无完整估值"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LedgerPeriodMetric(Modifier.weight(1f), "实际 Gas", periodGas, MaterialTheme.colorScheme.error)
                        LedgerPeriodMetric(Modifier.weight(1f), "实际收益", periodReward, MaterialTheme.colorScheme.secondary)
                        LedgerPeriodMetric(Modifier.weight(1f), "净盈亏", periodNet,
                            if ((periodNet ?: 0.0) >= 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                    }
                    if (gasAssetTotals.isNotEmpty() || rewardAssetTotals.isNotEmpty()) {
                        Text(
                            "原生币种明细（按网络分别统计，不跨币种相加）",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                        NativeAssetPeriodSection("Gas 原生币种", gasAssetTotals)
                        NativeAssetPeriodSection("实际奖励原生币种", rewardAssetTotals)
                    }
                    Text(
                        "上方金额为 USD 估值汇总；原生币种数量独立展示，估值不完整时不会显示合计。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("任务清单", color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("按步骤完成项目交互", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Text("$completed / ${state.tasks.size}", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(7.dp), color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    AppTextField(taskName, { taskName = it }, "添加一个待办任务…")
                    AppButton(
                        text = "＋ 添加任务",
                        enabled = taskName.isNotBlank() && !state.isBusy,
                        onClick = { projectDetailViewModel.addTask(project.id, taskName.trim(), projectUsesLocalTasks); taskName = "" }
                    )
                }
            }
        }
        if (state.isLoading && state.tasks.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("个人交互流水", color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (interactions.isEmpty()) {
                    Text("完成交互并手动确认记账后，真实 Gas 和收益会显示在这里。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(interactions, key = { "interaction:${it.id}" }) { interaction ->
            InteractionLedgerRow(interaction)
        }
        items(state.tasks.sortedBy { it.step_order }, key = { it.id }) { task ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(15.dp),
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
                    }) { Text("编辑", color = MaterialTheme.colorScheme.primary) }
                    AppTextButton(onClick = { projectDetailViewModel.deleteTask(project.id, task, projectUsesLocalTasks) }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
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
            title = { Text("记一笔 · " + project.name, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("项目已锁定：" + project.name, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                    Text("选择钱包并分别填写 Gas 和奖励；总奖励模式会按钱包均分。",
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
                                Text(wallet.wallet_name.ifBlank { "钱包" }, color = MaterialTheme.colorScheme.onSurface)
                                Text(wallet.chain + " · " + wallet.address.take(8) + "…" + wallet.address.takeLast(5),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (wallet.id in selectedAccountingWalletIds) {
                            AppTextField(
                                value = accountingWalletGasAmounts[wallet.id].orEmpty(),
                                onValueChange = { accountingWalletGasAmounts = accountingWalletGasAmounts + (wallet.id to it.filter { ch -> ch.isDigit() || ch == '.' }.take(18)) },
                                placeholder = wallet.wallet_name.ifBlank { "钱包" } + " · Gas（USD）",
                                enabled = !isSavingAccounting
                            )
                            if (accountingAllocationMode == ManualRewardAllocationMode.PER_WALLET &&
                                accountingRewardKind != ManualRewardKind.UNKNOWN) {
                                AppTextField(
                                    value = accountingWalletRewardAmounts[wallet.id].orEmpty(),
                                    onValueChange = { accountingWalletRewardAmounts = accountingWalletRewardAmounts + (wallet.id to it.filter { ch -> ch.isDigit() || ch == '.' }.take(24)) },
                                    placeholder = wallet.wallet_name.ifBlank { "钱包" } + " · 实际奖励数量",
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
                        Text("逐钱包填写奖励", color = MaterialTheme.colorScheme.onSurface)
                        RadioButton(
                            selected = accountingAllocationMode == ManualRewardAllocationMode.EVEN_SPLIT_TOTAL,
                            onClick = { accountingAllocationMode = ManualRewardAllocationMode.EVEN_SPLIT_TOTAL },
                            enabled = !isSavingAccounting
                        )
                        Text("总奖励均分", color = MaterialTheme.colorScheme.onSurface)
                    }
                    var rewardMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        AppButton(
                            text = "奖励类型：" + when (accountingRewardKind) {
                                ManualRewardKind.USD -> "USD"
                                ManualRewardKind.TOKEN -> "代币"
                                ManualRewardKind.MANUAL_USD_VALUATION -> "手动 USD 估值"
                                ManualRewardKind.POINTS -> "积分"
                                ManualRewardKind.UNISSUED -> "未发放"
                                ManualRewardKind.UNKNOWN -> "未知"
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
                            placeholder = "代币/积分名称",
                            enabled = !isSavingAccounting
                        )
                    }
                    if (accountingAllocationMode == ManualRewardAllocationMode.EVEN_SPLIT_TOTAL &&
                        accountingRewardKind != ManualRewardKind.UNKNOWN) {
                        AppTextField(
                            value = accountingRewardAmount,
                            onValueChange = { accountingRewardAmount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(24) },
                            placeholder = "本次活动总奖励数量",
                            enabled = !isSavingAccounting
                        )
                    }
                    Text(
                        if (project.communityDelisted) "该项目已被社区目录明确标记为下架。仍可正常记账、编辑和查看历史；本次及后续新记录只保存在本机，不再贡献公共统计。"
                        else "社区目录项目默认参与脱敏统计；未填写 Tx Hash 的记录按用户自报计入，填写后必须通过链上核验。私人项目只进入个人账本。",
                        color = if (project.communityDelisted) Color(0xFFB42318) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall)
                    selectedWallets.forEach { wallet ->
                        val selectedNetwork = accountingWalletNetworks[wallet.id].orEmpty()
                        var networkMenuExpanded by remember(wallet.id) { mutableStateOf(false) }
                        if (wallet.ecosystem == WalletEcosystem.EVM) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("核验网络", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                                                else -> "请选择网络"
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
                            Text("该钱包网络暂不支持链上核验；符合条件的社区项目记录仍按用户自报参与统计，私人项目只保存在个人账本。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                        }
                        if (!project.communityDelisted && project.communityOfficialId != null &&
                            ManualProjectAccountingPolicy.isTransactionVerificationNetworkSupported(selectedNetwork)) {
                            Text("未填写 Tx Hash 时按用户自报计入公共汇总；填写后系统会先核验交易，再决定是否计入。",
                                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                        if (!project.communityDelisted && project.communityOfficialId != null &&
                            ManualProjectAccountingPolicy.isTransactionVerificationNetworkSupported(selectedNetwork)) {
                            AppTextField(
                                value = accountingTxHashes[wallet.id].orEmpty(),
                                onValueChange = { value -> accountingTxHashes = accountingTxHashes + (wallet.id to value.trim()) },
                                placeholder = wallet.wallet_name.ifBlank { "钱包" } + " · Tx Hash",
                                enabled = !isSavingAccounting
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    text = "保存记账",
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
                                    ManualRewardKind.USD -> ManualRewardInput(ManualRewardKind.USD, requireNotNull(rewardAmount) { "请填写 USD 奖励" })
                                    ManualRewardKind.TOKEN -> ManualRewardInput(ManualRewardKind.TOKEN, requireNotNull(rewardAmount) { "请填写代币数量" }, accountingRewardUnit.trim())
                                    ManualRewardKind.MANUAL_USD_VALUATION -> ManualRewardInput(ManualRewardKind.MANUAL_USD_VALUATION, requireNotNull(rewardAmount) { "请填写手动 USD 估值" })
                                    ManualRewardKind.POINTS -> ManualRewardInput(ManualRewardKind.POINTS, requireNotNull(rewardAmount) { "请填写积分数量" }, accountingRewardUnit.trim())
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
                                    ManualRewardKind.USD -> ManualRewardInput(ManualRewardKind.USD, requireNotNull(amount) { "请填写 USD 总奖励" })
                                    ManualRewardKind.TOKEN -> ManualRewardInput(ManualRewardKind.TOKEN, requireNotNull(amount) { "请填写代币总数量" }, accountingRewardUnit.trim())
                                    ManualRewardKind.MANUAL_USD_VALUATION -> ManualRewardInput(ManualRewardKind.MANUAL_USD_VALUATION, requireNotNull(amount) { "请填写手动 USD 总估值" })
                                    ManualRewardKind.POINTS -> ManualRewardInput(ManualRewardKind.POINTS, requireNotNull(amount) { "请填写积分总数量" }, accountingRewardUnit.trim())
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
                            if (!saved) error("记账未保存，请检查是否存在重复交易或部分重复提交")
                            showManualAccounting = false
                            Toast.makeText(context,
                                if (project.communityDelisted) "记账成功；该项目已下架，记录仅保存在本机"
                                else if (batch.hasPublicContributionOptIn) "记账成功；符合条件的社区数据已进入脱敏统计流程" else "记账成功",
                                Toast.LENGTH_LONG).show()
                        } catch (error: Exception) {
                            Toast.makeText(context, error.message ?: "记账失败，请检查输入", Toast.LENGTH_LONG).show()
                        } finally {
                            isSavingAccounting = false
                        }
                    }
                })
            },
            dismissButton = {
                AppTextButton(onClick = { if (!isSavingAccounting) showManualAccounting = false }) { Text("取消") }
            }
        )
    }

    editingTask?.let { taskToEdit ->
        AlertDialog(
            onDismissRequest = { editingTask = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("编辑任务", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                AppTextField(
                    value = editedTaskName,
                    onValueChange = { if (it.length <= 240) editedTaskName = it },
                    placeholder = "任务名称"
                )
            },
            confirmButton = {
                AppButton(
                    text = "保存",
                    enabled = editedTaskName.isNotBlank() && !state.isBusy,
                    onClick = {
                        projectDetailViewModel.renameTask(
                            project.id, taskToEdit, editedTaskName, projectUsesLocalTasks
                        )
                        editingTask = null
                    }
                )
            },
            dismissButton = { AppTextButton(onClick = { editingTask = null }) { Text("取消") } }
        )
    }

    if (showClaimWalletPicker) {
        AlertDialog(
            onDismissRequest = { showClaimWalletPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("选择领取钱包") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将使用所选钱包的公开地址打开项目资格页面，不会自动签名或发送交易。",
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
                                        .putExtra(BrowserActivity.EXTRA_TITLE, "${project.name} · 资格领取"))
                                } else Toast.makeText(context, "领取链接无效", Toast.LENGTH_LONG).show()
                            } else projectDetailViewModel.requestClaimUrl(project.id, wallet.id)
                        }, scale = 0.96f, enabled = !state.isClaimLoading),
                            shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(wallet.wallet_name.ifBlank { "钱包" }, color = MaterialTheme.colorScheme.onSurface)
                                Text("${wallet.ecosystem} · ${wallet.address.take(8)}…${wallet.address.takeLast(5)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = { showClaimWalletPicker = false }) { Text("取消") } }
        )
    }

}



@Composable
private fun NativeAssetPeriodSection(
    title: String,
    totals: List<com.airdrophunter.app.data.LedgerAssetPeriodTotal>
) {
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
                    total.usdValuation?.let { "USD估值 $%.2f".format(Locale.US, it) } ?: "USD估值不完整",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun InteractionLedgerRow(interaction: UserInteractionEntity) {
    val time = remember(interaction.timestamp) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(interaction.timestamp))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(interaction.network, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "预估 Gas：" + (interaction.expectedGasCostUsd?.let { "USD $%.2f".format(Locale.US, it) } ?: "暂无估值"),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall
            )
            interaction.expectedRewardUsd?.let {
                Text("预估奖励：USD $%.2f".format(Locale.US, it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("实际 Gas USD估值 $%.2f".format(Locale.US, interaction.gasCostUsd),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text("实际收益 USD估值 +$%.2f".format(Locale.US, interaction.actualRewardUsd),
                    color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
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
private fun LedgerPeriodMetric(modifier: Modifier, label: String, value: Double?, color: Color) {
    Column(
        modifier.background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(value?.let { String.format(Locale.US, "$%.2f", it) } ?: "估值未完整", color = color,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ProjectMetric(modifier: Modifier = Modifier, label: String, value: Double) {
    Column(modifier.background(Color(0xFFF1F8F5), RoundedCornerShape(14.dp)).padding(horizontal = 9.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(String.format(Locale.US, "$%.2f", value), color = Color(0xFF16845B),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
