package com.airdrophunter.app

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import com.airdrophunter.app.data.LocalBackupRepository
import com.airdrophunter.app.data.LocalBackupSnapshot
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.appPressFeedback
import com.airdrophunter.app.ui.components.AppTextField
import java.util.Locale

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    isGuest: Boolean = true,
    onRequireAuth: () -> Unit = {},
    onManageWallets: () -> Unit = {},
    sessionScopeKey: String = "guest",
    vm: ProfileViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val profileViewModel: ProfileViewModel = vm ?: viewModel(
        factory = ProfileViewModelFactory(application.appContainer.profileRepository)
    )
    val state by profileViewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var selectedBackupUri by remember { mutableStateOf<Uri?>(null) }
    var pendingBackup by remember { mutableStateOf<LocalBackupSnapshot?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var activeProfileDestination by remember { mutableStateOf<ProfileDestination?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val appVersion = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "未知"
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = backupPassword
        backupPassword = ""
        backupPasswordConfirm = ""
        if (uri != null) {
            coroutineScope.launch {
                backupBusy = true
                runCatching { LocalBackupRepository.writeBackup(context, uri, password) }
                    .onSuccess { Toast.makeText(context, "备份文件已保存", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(context, it.message ?: "备份失败", Toast.LENGTH_LONG).show() }
                backupBusy = false
            }
        }
    }
    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedBackupUri = uri
            importPassword = ""
            showImportPasswordDialog = true
        } else {
            selectedBackupUri = null
            importPassword = ""
            showImportPasswordDialog = false
        }
    }

    LaunchedEffect(isGuest, sessionScopeKey) { profileViewModel.load(isGuest) }
    val stats = state.stats

    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("我的", color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (isGuest) "G" else "U", color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isGuest) "游客模式" else (state.profile?.username ?: "账户中心"),
                    color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    when {
                        isGuest -> "当前以游客身份使用"
                        state.profile != null -> "ID: " + state.profile!!.uid
                        state.isLoading -> "正在加载账户信息…"
                        else -> "账户信息暂时无法加载"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("数据概览", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
                    StatCell("跟踪项目", stats.tracked_projects.toString(), Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    StatCell(if (stats.usd_profit_complete) "胜率" else "已估值胜率",
                        String.format(Locale.US, "%.1f%%", stats.win_rate), Modifier.weight(1f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
                    StatCell("累计 Gas", String.format(Locale.US, "%.2f", stats.ledger_total_gas_cost),
                        Modifier.weight(1f), "USD")
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    StatCell("实际收益",
                        if (stats.usd_profit_complete) String.format(Locale.US, "%.2f", stats.ledger_total_actual_reward) else "估值未完整",
                        Modifier.weight(1f), if (stats.usd_profit_complete) "USD" else "")
                }
                Text(
                    "参与项目 ${stats.ledger_project_count} · 净收益 ${if (stats.usd_profit_complete) String.format(Locale.US, "%.2f", stats.ledger_net_profit) + " USD" else "估值未完整"} · 已完成 ${stats.completed_projects} · 交互 ${stats.ledger_interaction_days} 天",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("数据迁移", color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                AppButton(
                    text = if (backupBusy) "正在处理…" else "备份我的数据",
                    onClick = { showBackupPasswordDialog = true },
                    enabled = !backupBusy,
                    loading = backupBusy
                )
                AppButton(
                    text = "导入数据",
                    onClick = { openBackupLauncher.launch(arrayOf("*/*")) },
                    enabled = !backupBusy,
                    style = AppButtonStyle.SECONDARY
                )
                Text(
                    "支持 .ahbackup 和 .hunter 文件。备份文件经过密码加密；换机时选择文件并输入相同密码即可恢复，请妥善保存文件和密码。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (isGuest) {
            Text(
                "当前版本暂不开放注册/登录。个人数据同步能力仍在完善中；为避免换机或设备故障造成数据丢失，请定期使用上方加密备份功能保存数据。",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (state.hasError && !isGuest) {
            Text("账户或统计数据暂时无法加载，服务器繁忙。", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("设置与管理", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingRow("设置", Icons.Default.Settings) { activeProfileDestination = ProfileDestination.SETTINGS }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow("安全中心", Icons.Default.Security) { activeProfileDestination = ProfileDestination.SECURITY }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow("消息通知", Icons.Default.NotificationsNone) { activeProfileDestination = ProfileDestination.NOTIFICATIONS }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow("钱包管理", Icons.Default.Wallet, onManageWallets)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow("关于", Icons.Default.Info) { showAboutDialog = true }
                }
            }
        }
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("关于 Airdrop Hunter") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("面向空投项目管理、钱包追踪与个人记账的工具。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("版本 $appVersion", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium)
                    }
                },
                confirmButton = {
                    AppTextButton(onClick = { showAboutDialog = false }) { Text("知道了") }
                }
            )
        }

        if (showBackupPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showBackupPasswordDialog = false; backupPassword = ""; backupPasswordConfirm = "" },
                title = { Text("设置备份密码") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("备份文件会使用此密码加密。换手机导入时必须输入相同密码，密码不会保存在应用中。")
                        AppTextField(
                            value = backupPassword, onValueChange = { backupPassword = it },
                            placeholder = "备份密码（至少8位）",
                            visualTransformation = PasswordVisualTransformation()
                        )
                        AppTextField(
                            value = backupPasswordConfirm, onValueChange = { backupPasswordConfirm = it },
                            placeholder = "再次输入密码",
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                },
                confirmButton = {
                    AppTextButton(
                        enabled = backupPassword.length >= 8 && backupPassword == backupPasswordConfirm && !backupBusy,
                        onClick = {
                            showBackupPasswordDialog = false
                            createBackupLauncher.launch("AirdropHunter-backup.ahbackup")
                        }
                    ) { Text("继续") }
                },
                dismissButton = { AppTextButton(onClick = { showBackupPasswordDialog = false; backupPassword = ""; backupPasswordConfirm = "" }) { Text("取消") } }
            )
        }

        if (showImportPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showImportPasswordDialog = false; importPassword = ""; selectedBackupUri = null },
                title = { Text("输入备份密码") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("请输入创建该备份文件时设置的密码。")
                        AppTextField(
                            value = importPassword, onValueChange = { importPassword = it },
                            placeholder = "备份密码",
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                },
                confirmButton = {
                    AppTextButton(
                        enabled = importPassword.length >= 8 && selectedBackupUri != null && !backupBusy,
                        onClick = {
                            val uri = selectedBackupUri ?: return@AppTextButton
                            val password = importPassword
                            showImportPasswordDialog = false
                            coroutineScope.launch {
                                backupBusy = true
                                runCatching { LocalBackupRepository.readBackup(context, uri, password) }
                                    .onSuccess { pendingBackup = it; showRestoreConfirmDialog = true }
                                    .onFailure { Toast.makeText(context, it.message ?: "备份文件无法读取", Toast.LENGTH_LONG).show() }
                                backupBusy = false
                            }
                            importPassword = ""
                        }
                    ) { Text("验证备份") }
                },
                dismissButton = { AppTextButton(onClick = { showImportPasswordDialog = false; importPassword = ""; selectedBackupUri = null }) { Text("取消") } }
            )
        }

        if (showRestoreConfirmDialog && pendingBackup != null) {
            val backup = pendingBackup!!
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false; pendingBackup = null; selectedBackupUri = null },
                title = { Text("确认导入并替换数据？") },
                text = {
                    Text("备份时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(backup.createdAt))}\n包含 ${backup.totalRecords} 条本地记录。导入后将替换当前设备此会话的数据，此操作不能自动撤销。")
                },
                confirmButton = {
                    AppTextButton(
                        enabled = !backupBusy,
                        onClick = {
                            showRestoreConfirmDialog = false
                            coroutineScope.launch {
                                backupBusy = true
                                runCatching { LocalBackupRepository.restore(backup) }
                                    .onSuccess {
                                        profileViewModel.load(isGuest)
                                        Toast.makeText(context, "数据导入成功", Toast.LENGTH_LONG).show()
                                    }
                                    .onFailure { Toast.makeText(context, it.message ?: "导入失败，原数据未更改", Toast.LENGTH_LONG).show() }
                                backupBusy = false
                                pendingBackup = null
                                selectedBackupUri = null
                            }
                        }
                    ) { Text("确认导入") }
                },
                dismissButton = { AppTextButton(onClick = { showRestoreConfirmDialog = false; pendingBackup = null; selectedBackupUri = null }) { Text("取消") } }
            )
        }
    }
    activeProfileDestination?.let { destination ->
        ProfileDestinationDialog(destination = destination) { activeProfileDestination = null }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier, suffix: String = "") {
    Column(modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (suffix.isNotBlank()) Text(suffix, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(56.dp)
        .appPressFeedback(onClick = onClick, scale = 1f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(AppDesignTokens.Radius.Md.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f))
    }
}
