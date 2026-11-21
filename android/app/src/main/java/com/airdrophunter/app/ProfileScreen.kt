package com.airdrophunter.app

import android.app.Activity
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
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppIconButton
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
    var showLanguageDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var selectedBackupUri by remember { mutableStateOf<Uri?>(null) }
    var pendingBackup by remember { mutableStateOf<LocalBackupSnapshot?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var activeProfileDestination by remember { mutableStateOf<ProfileDestination?>(null) }
    val appVersion = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
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
                    .onSuccess { Toast.makeText(context, context.getString(R.string.loc_profile_01), Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(context, it.message ?: context.getString(R.string.loc_profile_02), Toast.LENGTH_LONG).show() }
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
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.loc_profile_03), modifier = Modifier.weight(1f).semantics { heading() },
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            AppIconButton(
                icon = Icons.Default.Settings,
                contentDescription = context.getString(R.string.loc_profile_04),
                onClick = { activeProfileDestination = ProfileDestination.SETTINGS }
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (isGuest) "G" else "U", color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isGuest) context.getString(R.string.loc_profile_05) else (state.profile?.username ?: context.getString(R.string.loc_profile_06)),
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    when {
                        isGuest -> context.getString(R.string.loc_profile_07)
                        state.profile != null -> "ID: " + state.profile!!.uid
                        state.isLoading -> context.getString(R.string.loc_profile_08)
                        else -> context.getString(R.string.loc_profile_09)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(context.getString(R.string.loc_profile_10), color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column {
                val hasLedgerEntries = stats.total_interactions > 0 || stats.ledger_project_count > 0
                val hasWinRateSample = stats.evaluated_projects > 0
                Row(Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
                    StatCell(context.getString(R.string.loc_profile_11), stats.tracked_projects.toString(), Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    StatCell(
                        if (!hasWinRateSample) context.getString(R.string.loc_profile_12) else if (stats.usd_profit_complete) context.getString(R.string.loc_profile_13) else context.getString(R.string.loc_profile_14),
                        if (hasWinRateSample) String.format(Locale.US, "%.1f%%", stats.win_rate) else context.getString(R.string.loc_profile_15),
                        Modifier.weight(1f)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
                    StatCell(
                        context.getString(R.string.loc_profile_16),
                        if (hasLedgerEntries) String.format(Locale.US, "%.2f", stats.ledger_total_gas_cost) else context.getString(R.string.loc_profile_17),
                        Modifier.weight(1f), if (hasLedgerEntries) "USD" else ""
                    )
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    StatCell(
                        context.getString(R.string.loc_profile_18),
                        when {
                            !hasLedgerEntries -> context.getString(R.string.loc_profile_17)
                            stats.usd_profit_complete -> String.format(Locale.US, "%.2f", stats.ledger_total_actual_reward)
                            else -> context.getString(R.string.loc_profile_19)
                        },
                        Modifier.weight(1f), if (hasLedgerEntries && stats.usd_profit_complete) "USD" else ""
                    )
                }
                val netProfitLabel = when {
                    !hasLedgerEntries -> context.getString(R.string.loc_profile_17)
                    stats.usd_profit_complete -> String.format(Locale.US, "%.2f", stats.ledger_net_profit) + " USD"
                    else -> context.getString(R.string.loc_profile_19)
                }
                Text(
                    "参与项目 ${stats.ledger_project_count} · 净收益 $netProfitLabel · 已完成 ${stats.completed_projects} · 交互 ${stats.ledger_interaction_days} 天",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                Text(context.getString(R.string.loc_profile_20), color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                AppButton(
                    text = if (backupBusy) context.getString(R.string.loc_profile_21) else context.getString(R.string.loc_profile_22),
                    onClick = { showBackupPasswordDialog = true },
                    enabled = !backupBusy,
                    loading = backupBusy
                )
                AppButton(
                    text = context.getString(R.string.loc_profile_23),
                    onClick = { openBackupLauncher.launch(arrayOf("*/*")) },
                    enabled = !backupBusy,
                    style = AppButtonStyle.SECONDARY
                )
                Text(
                    "支持 .ahbackup 和 .hunter 文件。备份文件经过密码加密；换机时选择文件并输入相同密码即可恢复，请妥善保存文件和密码。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        if (isGuest) {
            Text(
                context.getString(R.string.loc_profile_25),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (state.hasError && !isGuest) {
            Text(context.getString(R.string.loc_profile_26), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(context.getString(R.string.loc_profile_27), color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingRow(context.getString(R.string.loc_profile_28), Icons.Default.Settings) { activeProfileDestination = ProfileDestination.SETTINGS }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(context.getString(R.string.language_settings), Icons.Default.Language) { showLanguageDialog = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(context.getString(R.string.loc_profile_29), Icons.Default.Security) { activeProfileDestination = ProfileDestination.SECURITY }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(context.getString(R.string.loc_profile_30), Icons.Default.NotificationsNone) { activeProfileDestination = ProfileDestination.NOTIFICATIONS }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(context.getString(R.string.loc_profile_31), Icons.Default.Wallet, onManageWallets)
                }
            }
        }

        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(R.string.language_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.language_change_note),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppTextButton(
                            onClick = {
                                AppLocale.setLanguage(context, "en")
                                showLanguageDialog = false
                                (context as? Activity)?.recreate()
                            }
                        ) { Text(stringResource(R.string.language_english)) }
                        AppTextButton(
                            onClick = {
                                AppLocale.setLanguage(context, "zh-CN")
                                showLanguageDialog = false
                                (context as? Activity)?.recreate()
                            }
                        ) { Text(stringResource(R.string.language_chinese)) }
                    }
                },
                confirmButton = {
                    AppTextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showBackupPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showBackupPasswordDialog = false; backupPassword = ""; backupPasswordConfirm = "" },
                title = { Text(context.getString(R.string.loc_profile_32)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(context.getString(R.string.loc_profile_33))
                        AppTextField(
                            value = backupPassword, onValueChange = { backupPassword = it },
                            placeholder = context.getString(R.string.loc_profile_34),
                            visualTransformation = PasswordVisualTransformation()
                        )
                        AppTextField(
                            value = backupPasswordConfirm, onValueChange = { backupPasswordConfirm = it },
                            placeholder = context.getString(R.string.loc_profile_35),
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
                    ) { Text(context.getString(R.string.loc_profile_36)) }
                },
                dismissButton = { AppTextButton(onClick = { showBackupPasswordDialog = false; backupPassword = ""; backupPasswordConfirm = "" }) { Text(context.getString(R.string.loc_profile_37)) } }
            )
        }

        if (showImportPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showImportPasswordDialog = false; importPassword = ""; selectedBackupUri = null },
                title = { Text(context.getString(R.string.loc_profile_38)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(context.getString(R.string.loc_profile_39))
                        AppTextField(
                            value = importPassword, onValueChange = { importPassword = it },
                            placeholder = context.getString(R.string.loc_profile_40),
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
                                    .onFailure { Toast.makeText(context, it.message ?: context.getString(R.string.loc_profile_41), Toast.LENGTH_LONG).show() }
                                backupBusy = false
                            }
                            importPassword = ""
                        }
                    ) { Text(context.getString(R.string.loc_profile_42)) }
                },
                dismissButton = { AppTextButton(onClick = { showImportPasswordDialog = false; importPassword = ""; selectedBackupUri = null }) { Text(context.getString(R.string.loc_profile_37)) } }
            )
        }

        if (showRestoreConfirmDialog && pendingBackup != null) {
            val backup = pendingBackup!!
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false; pendingBackup = null; selectedBackupUri = null },
                title = { Text(context.getString(R.string.loc_profile_43)) },
                text = {
                    Text(context.getString(R.string.loc_profile_47, java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(backup.createdAt)), backup.totalRecords))
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
                                        Toast.makeText(context, context.getString(R.string.loc_profile_44), Toast.LENGTH_LONG).show()
                                    }
                                    .onFailure { Toast.makeText(context, it.message ?: context.getString(R.string.loc_profile_45), Toast.LENGTH_LONG).show() }
                                backupBusy = false
                                pendingBackup = null
                                selectedBackupUri = null
                            }
                        }
                    ) { Text(context.getString(R.string.loc_profile_46)) }
                },
                dismissButton = { AppTextButton(onClick = { showRestoreConfirmDialog = false; pendingBackup = null; selectedBackupUri = null }) { Text(context.getString(R.string.loc_profile_37)) } }
            )
        }
    }
    activeProfileDestination?.let { destination ->
        ProfileDestinationDialog(destination = destination, appVersion = appVersion) { activeProfileDestination = null }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier, suffix: String = "") {
    Column(modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(
            text = if (suffix.isBlank()) value else "$value $suffix",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            softWrap = true
        )
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
            fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f))
    }
}
