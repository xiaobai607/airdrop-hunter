package com.airdrophunter.app

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airdrophunter.app.ui.components.BaseCard

enum class ProfileDestination(val title: String) {
    SETTINGS("设置"),
    SECURITY("安全中心"),
    NOTIFICATIONS("消息通知")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDestinationDialog(destination: ProfileDestination, appVersion: String = "未知", onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            destination.title,
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
                        ProfileDestination.SETTINGS -> SettingsDestinationContent(appVersion)
                        ProfileDestination.SECURITY -> SecurityDestinationContent()
                        ProfileDestination.NOTIFICATIONS -> NotificationsDestinationContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDestinationContent(appVersion: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "偏好与工具",
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        DestinationInfoCard {
            SettingsInfoRow(
                icon = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "浏览器工具栏",
                description = "工具显示与排序可在浏览器页面的“添加”入口管理"
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "关于应用",
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        DestinationInfoCard {
            SettingsInfoRow(
                icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "Airdrop Hunter",
                description = "空投项目管理 · 钱包追踪 · 个人记账",
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
    trailing: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
