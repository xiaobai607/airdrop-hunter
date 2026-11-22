package com.airdrophunter.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airdrophunter.app.ui.components.BaseCard
import com.airdrophunter.app.ui.theme.AppDesignTokens

enum class ProfileDestination(val titleRes: Int) {
    SETTINGS(R.string.settings_title),
    SECURITY(R.string.security_title),
    NOTIFICATIONS(R.string.notifications_title)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDestinationDialog(destination: ProfileDestination, appVersion: String = "未知", onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showLanguageDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val selectedLanguageTag = AppLocale.selectedLanguageTag(context)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(destination.titleRes),
                            modifier = Modifier.semantics { heading() },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    when (destination) {
                        ProfileDestination.SETTINGS -> SettingsDestinationContent(appVersion) {
                            showLanguageDialog = true
                        }
                        ProfileDestination.SECURITY -> SecurityDestinationContent()
                        ProfileDestination.NOTIFICATIONS -> NotificationsDestinationContent()
                    }
                }
            }
        }
    }
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LanguageOption(
                        title = stringResource(R.string.language_follow_system),
                        selected = selectedLanguageTag == null,
                        onClick = {
                            AppLocale.setLanguage(context, null)
                            showLanguageDialog = false
                        }
                    )
                    LanguageOption(
                        title = stringResource(R.string.language_chinese),
                        selected = selectedLanguageTag?.startsWith("zh", ignoreCase = true) == true,
                        onClick = {
                            AppLocale.setLanguage(context, "zh-Hans")
                            showLanguageDialog = false
                        }
                    )
                    LanguageOption(
                        title = stringResource(R.string.language_english),
                        selected = selectedLanguageTag?.startsWith("en", ignoreCase = true) == true,
                        onClick = {
                            AppLocale.setLanguage(context, "en")
                            showLanguageDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun LanguageOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
    }
}

@Composable
private fun SettingsDestinationContent(appVersion: String, onChooseLanguage: () -> Unit) {
    val context = LocalContext.current
    val selectedLanguageTag = AppLocale.selectedLanguageTag(context)
    val currentLanguageLabel = when {
        selectedLanguageTag == null -> stringResource(R.string.language_follow_system)
        selectedLanguageTag.startsWith("zh", ignoreCase = true) -> stringResource(R.string.language_chinese)
        else -> stringResource(R.string.language_english)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.settings_preferences),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        DestinationInfoCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsInfoRow(
                    icon = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.language_settings),
                    description = stringResource(R.string.language_change_note),
                    trailing = currentLanguageLabel,
                    onClick = onChooseLanguage
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsInfoRow(
                    icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.browser_toolbar_title),
                    description = stringResource(R.string.browser_toolbar_description)
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.about_app),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        DestinationInfoCard {
            SettingsInfoRow(
                icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "Airdrop Hunter",
                description = stringResource(R.string.about_app_description),
                trailing = "v$appVersion"
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    trailing: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        if (trailing != null) {
            Text(
                trailing,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SecurityDestinationContent() {
    val accounts by WalletConnectSessionDelegate.accounts.collectAsState()
    val origins by DappOriginPermissionStore.authorizedOrigins.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DestinationSectionTitle("安全说明")
            DestinationInfoCard {
                Text(
                    "非托管安全边界",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Airdrop Hunter 不创建、不托管也不保存钱包私钥或助记词。钱包签名和交易确认由外部钱包完成；请只连接可信网站，并在外部钱包中核对每笔请求。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DestinationSectionTitle("外部钱包会话")
            if (accounts.isEmpty()) {
                DestinationInfoCard {
                    Text(
                        "当前没有可读取的外部钱包账户会话",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                accounts.forEach { account ->
                    DestinationInfoCard {
                        Text(
                            account.walletLabel,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "网络：${account.chainId}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Text(
                            account.address,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
            DestinationInfoCard {
                Text(
                    "外部钱包连接由对应钱包 App 管理。当前版本不伪造本地断开状态；如需结束会话，请在 MetaMask、OKX Wallet 等外部钱包的已连接网站/会话管理中撤销。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DestinationSectionTitle("网站授权", Modifier.weight(1f))
                if (origins.isNotEmpty()) {
                    TextButton(onClick = { DappOriginPermissionStore.revokeAll() }) {
                        Text("全部撤销", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (origins.isEmpty()) {
                DestinationInfoCard {
                    Text(
                        "当前没有已授权的网站来源",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                origins.keys.sorted().forEach { origin ->
                    DestinationInfoCard {
                        Text(
                            origin,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "此授权仅在当前进程和对应钱包会话有效；会话变化后自动清除。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        TextButton(
                            onClick = { DappOriginPermissionStore.revoke(origin) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("撤销此网站授权", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        }
                    }
                }
            }
            Text(
                "站点授权保存在内存中，不会跨应用进程持久化。撤销后，该网站不能继续通过本应用读取已授权账户；重新授权必须再次经过用户确认。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun DestinationSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun NotificationsDestinationContent() {
    DestinationInfoCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                "暂无通知",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "当前还没有站内通知。通知列表和已读状态入口已预留；在真实通知服务接入前，不会生成测试通知或擅自开启推送。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Text(
                "未读 0 条",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DestinationInfoCard(content: @Composable ColumnScope.() -> Unit) {
    BaseCard {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}
