package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import com.airdrophunter.app.data.CommunityContributionSyncScheduler
import androidx.compose.foundation.text.KeyboardOptions
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.data.WalletTransactionDto
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun WalletDetailScreen(
    wallet: WalletDto,
    onBack: () -> Unit,
    vm: WalletDetailViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val walletDetailViewModel: WalletDetailViewModel = vm ?: viewModel(factory = WalletDetailViewModelFactory(application.appContainer.assetRepository, application.appContainer.apiService))
    val state by walletDetailViewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val accountingRepository = remember(application) { application.appContainer.accountingRepository }
    var projects by remember { mutableStateOf<List<ProjectDto>>(emptyList()) }
    var selectedProjectLocalId by remember { mutableStateOf("") }
    var recordingTransaction by remember { mutableStateOf<WalletTransactionDto?>(null) }
    var selectedGasUsd by remember { mutableStateOf("") }
    var actualRewardUsd by remember { mutableStateOf("") }
    var showProjectMenu by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    val selectedProject = projects.firstOrNull { it.id == selectedProjectLocalId }
    val gasValue = selectedGasUsd.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    val rewardValue = actualRewardUsd.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    LaunchedEffect(accountingRepository) {
        projects = accountingRepository.loadProjects()
        selectedProjectLocalId = projects.firstOrNull()?.id.orEmpty()
    }
    LaunchedEffect(wallet.address, wallet.ecosystem) { walletDetailViewModel.loadWallet(wallet.address, wallet.ecosystem) }
    DisposableEffect(walletDetailViewModel) { onDispose { walletDetailViewModel.clear() } }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = AppColors.TextPrimary) }
            Column(Modifier.weight(1f)) {
                Text(wallet.wallet_name.ifBlank { "钱包资产" }, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(shortAddress(wallet.address), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(wallet.address)) }) {
                Icon(Icons.Default.ContentCopy, "复制地址", tint = AppColors.Primary)
            }
        }
        UnifiedAssetContent(
            assets = state.assets,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onRefresh = walletDetailViewModel::refresh,
            transactions = state.transactions,
            transactionsLoading = state.transactionsLoading,
            transactionsError = state.transactionsError,
            transactionsWarning = state.transactionsWarning,
            transactionSupportMessage = state.transactionSupportMessage,
            onRecordTransaction = { transaction ->
                recordingTransaction = transaction
                selectedGasUsd = transaction.gas_cost_usd.orEmpty()
                actualRewardUsd = ""
                if (selectedProjectLocalId.isBlank()) selectedProjectLocalId = projects.firstOrNull()?.id.orEmpty()
            },
            modifier = Modifier.weight(1f)
        )
    }

    recordingTransaction?.let { transaction ->
        AlertDialog(
            onDismissRequest = { if (!isRecording) recordingTransaction = null },
            containerColor = AppColors.Surface,
            title = { Text("确认链上 Gas 记账", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${transaction.chain.uppercase()} · ${transaction.hash.take(12)}…${transaction.hash.takeLast(6)}",
                        color = AppColors.TextHelper,
                        style = MaterialTheme.typography.labelSmall
                    )
                    OutlinedTextField(
                        value = selectedGasUsd,
                        onValueChange = { selectedGasUsd = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("真实 Gas 费用 (USD)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Box {
                        OutlinedButton(onClick = { showProjectMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedProject?.name ?: "选择归属项目", modifier = Modifier.weight(1f), maxLines = 1)
                            Text("选择", color = AppColors.Primary)
                        }
                        DropdownMenu(expanded = showProjectMenu, onDismissRequest = { showProjectMenu = false }) {
                            projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text(project.name) },
                                    onClick = {
                                        selectedProjectLocalId = project.id
                                        showProjectMenu = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = actualRewardUsd,
                        onValueChange = { actualRewardUsd = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("实际收益 (USD，需手动填写)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Text(
                        "费用来自链上交易记录；美元金额按当前币价估值。实际收益不会自动推算，只有你填写并确认后才会进入个人账本。",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (projects.isEmpty()) {
                        Text("请先在「我的项目」中加入或创建项目，再进行记账。", color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isRecording && selectedProject != null && gasValue != null && rewardValue != null,
                    onClick = {
                        val project = selectedProject ?: return@Button
                        val safeGas = gasValue ?: return@Button
                        val safeReward = rewardValue ?: return@Button
                        isRecording = true
                        scope.launch {
                            val ok = runCatching {
                                accountingRepository.recordWalletTransaction(
                                    projectLocalId = project.id,
                                    projectOfficialId = project.communityOfficialId,
                                    projectName = project.name,
                                    txHash = transaction.hash,
                                    walletAddress = wallet.address,
                                    network = transaction.chain,
                                    gasCostUsd = safeGas,
                                    actualRewardUsd = safeReward,
                                    timestamp = transaction.timestamp * 1000L
                                )
                            }.getOrDefault(false)
                            isRecording = false
                            if (ok) {
                                CommunityContributionSyncScheduler.enqueue(context)
                                Toast.makeText(context, "已写入个人项目账本", Toast.LENGTH_SHORT).show()
                                recordingTransaction = null
                            } else {
                                Toast.makeText(context, "这笔交易已记账，或保存失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text(if (isRecording) "保存中…" else "确认入账")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isRecording) recordingTransaction = null }, enabled = !isRecording) {
                    Text("取消")
                }
            }
        )
    }
}
