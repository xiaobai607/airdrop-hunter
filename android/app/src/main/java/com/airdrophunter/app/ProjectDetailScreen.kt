package com.airdrophunter.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.airdrophunter.app.data.TaskDto
import com.airdrophunter.app.data.WalletDto
import kotlinx.coroutines.launch
import com.airdrophunter.app.ui.theme.AppColors
import java.util.Locale
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
        modifier.fillMaxSize().background(AppColors.Background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border), shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(project.name, color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (project.description.isNotBlank()) Text(project.description,
                            color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectMetric(Modifier.weight(1f), "预期奖励", project.expected_income)
                        ProjectMetric(Modifier.weight(1f), "Gas 费用", project.gas_cost)
                        ProjectMetric(Modifier.weight(1f), "实际收益", project.actual_income)
                    }
                    project.deadline?.let { Text("${stringResource(R.string.deadline)}: $it",
                        color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (project.claim_url.startsWith("https://", ignoreCase = true)) {
                            Button(enabled = !isLoadingClaimWallets && !state.isClaimLoading, onClick = {
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
                            }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                                Text(if (isLoadingClaimWallets || state.isClaimLoading) "准备中…" else "选择钱包并领取")
                            }
                        }
                        if (project.official_url.startsWith("https://")) OutlinedButton(onClick = {
                            context.startActivity(Intent(context, BrowserActivity::class.java)
                                .putExtra(BrowserActivity.EXTRA_URL, project.official_url)
                                .putExtra(BrowserActivity.EXTRA_TITLE, project.name))
                        }) { Text(stringResource(R.string.official_website)) }
                        if (project.twitter_url.startsWith("https://")) OutlinedButton(onClick = {
                            context.startActivity(Intent(context, BrowserActivity::class.java)
                                .putExtra(BrowserActivity.EXTRA_URL, project.twitter_url)
                                .putExtra(BrowserActivity.EXTRA_TITLE, twitterTitle))
                        }) { Text(stringResource(R.string.twitter)) }
                    }
                    if (project.communityOfficialId != null && onOpenCommunity != null) {
                        OutlinedButton(
                            onClick = { onOpenCommunity(project) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("查看全网大盘数据") }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("项目关联钱包", color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("可为同一项目绑定多个公开地址；领取时优先显示已关联钱包",
                                color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${assignedWalletIds.size} 个", color = AppColors.Primary,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    when {
                        isLoadingAssignments -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppColors.Primary)
                        assignmentWallets.isEmpty() -> Text("还没有钱包地址，请先在钱包管理中添加。", color = AppColors.TextHelper,
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
                                    Text(wallet.wallet_name, color = AppColors.TextPrimary,
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${wallet.ecosystem} · ${wallet.address.take(8)}…${wallet.address.takeLast(5)}",
                                        color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    assignmentError?.let {
                        Text(it, color = AppColors.Danger, style = MaterialTheme.typography.labelSmall)
                    }
                    if (assignedWalletIds != savedAssignedWalletIds) {
                        Button(
                            enabled = !isSavingAssignments && !isLoadingAssignments,
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
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) {
                            Text(if (isSavingAssignments) "保存中…" else "保存钱包关联")
                        }
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
            val periodGas = periodInteractions.sumOf { it.gasCostUsd }
            val periodReward = periodInteractions.sumOf { it.actualRewardUsd }
            val periodNet = periodReward - periodGas
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("个人项目账本", Modifier.weight(1f), color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${userProject?.interactionCount ?: 0} 次交互", color = AppColors.TextHelper,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectMetric(Modifier.weight(1f), "累计 Gas", userProject?.totalGasCostUsd ?: 0.0)
                        ProjectMetric(Modifier.weight(1f), "实际收益", userProject?.totalActualRewardUsd ?: 0.0)
                        ProjectMetric(Modifier.weight(1f), "净利润", userProject?.netProfitUsd ?: 0.0)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        LedgerPeriod.values().forEach { option ->
                            FilterChip(
                                selected = ledgerPeriod == option,
                                onClick = { ledgerPeriod = option },
                                label = { Text(option.label) },
                                shape = RoundedCornerShape(999.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.TealTint,
                                    selectedLabelColor = AppColors.Primary,
                                    labelColor = AppColors.TextSecondary
                                )
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${periodInteractions.size} 笔", color = AppColors.TextHelper,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LedgerPeriodMetric(Modifier.weight(1f), "Gas 消耗", periodGas, AppColors.Danger)
                        LedgerPeriodMetric(Modifier.weight(1f), "实际收益", periodReward, AppColors.Success)
                        LedgerPeriodMetric(Modifier.weight(1f), "净盈亏", periodNet,
                            if (periodNet >= 0.0) AppColors.Success else AppColors.Danger)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border), shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("任务清单", color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("按步骤完成项目交互", color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Text("$completed / ${state.tasks.size}", color = AppColors.Primary,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(7.dp), color = AppColors.Primary,
                        trackColor = AppColors.Border,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    OutlinedTextField(value = taskName, onValueChange = { taskName = it },
                        modifier = Modifier.fillMaxWidth(), placeholder = { Text("添加一个待办任务…") },
                        singleLine = true, shape = RoundedCornerShape(13.dp))
                    Button(enabled = taskName.isNotBlank() && !state.isBusy,
                        onClick = { projectDetailViewModel.addTask(project.id, taskName.trim(), projectUsesLocalTasks); taskName = "" },
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                        Text("＋ 添加任务", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (state.isLoading && state.tasks.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("个人交互流水", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (interactions.isEmpty()) {
                    Text("完成交互并手动确认记账后，真实 Gas 和收益会显示在这里。",
                        color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(interactions, key = { "interaction:${it.id}" }) { interaction ->
            InteractionLedgerRow(interaction)
        }
        items(state.tasks.sortedBy { it.step_order }, key = { it.id }) { task ->
            Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border), shape = RoundedCornerShape(15.dp),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = AppColors.NavIndicator) {
                        Text(task.step_order.toString(), Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    }
                    Checkbox(checked = task.is_completed,
                        onCheckedChange = { checked -> projectDetailViewModel.setCompleted(project.id, task, checked, projectUsesLocalTasks) })
                    Text(task.task_name, Modifier.weight(1f),
                        color = if (task.is_completed) AppColors.TextHelper else AppColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        editingTask = task
                        editedTaskName = task.task_name
                    }) { Text("编辑", color = AppColors.Primary) }
                    TextButton(onClick = { projectDetailViewModel.deleteTask(project.id, task, projectUsesLocalTasks) }) {
                        Text("删除", color = AppColors.Danger)
                    }
                }
            }
        }
    }
    editingTask?.let { taskToEdit ->
        AlertDialog(
            onDismissRequest = { editingTask = null },
            containerColor = AppColors.Surface,
            title = { Text("编辑任务", color = AppColors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = editedTaskName,
                    onValueChange = { if (it.length <= 240) editedTaskName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("任务名称") }
                )
            },
            confirmButton = {
                Button(
                    enabled = editedTaskName.isNotBlank() && !state.isBusy,
                    onClick = {
                        projectDetailViewModel.renameTask(
                            project.id, taskToEdit, editedTaskName, projectUsesLocalTasks
                        )
                        editingTask = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingTask = null }) { Text("取消") } }
        )
    }

    if (showClaimWalletPicker) {
        AlertDialog(
            onDismissRequest = { showClaimWalletPicker = false },
            containerColor = AppColors.Surface,
            title = { Text("选择领取钱包") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将使用所选钱包的公开地址打开项目资格页面，不会自动签名或发送交易。",
                        color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    claimWallets.forEach { wallet ->
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !state.isClaimLoading, onClick = {
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
                        }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(wallet.wallet_name.ifBlank { "钱包" }, color = AppColors.TextPrimary)
                                Text("${wallet.ecosystem} · ${wallet.address.take(8)}…${wallet.address.takeLast(5)}",
                                    color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showClaimWalletPicker = false }) { Text("取消") } }
        )
    }

}


@Composable
private fun InteractionLedgerRow(interaction: UserInteractionEntity) {
    val time = remember(interaction.timestamp) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(interaction.timestamp))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.Border),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time, color = AppColors.TextPrimary, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(interaction.network, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gas $%.2f".format(Locale.US, interaction.gasCostUsd),
                    color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
                Text("实际收益 +$%.2f".format(Locale.US, interaction.actualRewardUsd),
                    color = AppColors.Success, style = MaterialTheme.typography.bodySmall)
            }
            if (interaction.txHash.isNotBlank()) {
                Text("交易：${interaction.txHash.take(10)}…",
                    color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LedgerPeriodMetric(modifier: Modifier, label: String, value: Double, color: Color) {
    Column(
        modifier.background(AppColors.Background, RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(String.format(Locale.US, "$%.2f", value), color = color,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ProjectMetric(modifier: Modifier = Modifier, label: String, value: Double) {
    Column(modifier.background(Color(0xFFF1F8F5), RoundedCornerShape(14.dp)).padding(horizontal = 9.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(String.format(Locale.US, "$%.2f", value), color = Color(0xFF16845B),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
