package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.theme.AppDesignTokens

enum class ProfileDestination(val title: String) {
    SETTINGS("设置"),
    SECURITY("安全中心"),
    NOTIFICATIONS("消息通知")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDestinationDialog(destination: ProfileDestination, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(destination.title, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Surface)
                )
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (destination) {
                        ProfileDestination.SETTINGS -> SettingsDestinationContent()
                        ProfileDestination.SECURITY -> SecurityDestinationContent()
                        ProfileDestination.NOTIFICATIONS -> NotificationsDestinationContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDestinationContent() {
    Text("应用偏好", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    DestinationInfoRow("外观与主题", "主题和显示偏好设置入口，具体控制项正在开发中")
    DestinationInfoRow("语言", "多语言切换能力正在开发中")
    DestinationInfoRow("浏览器与工具栏", "网页工具栏的显示与排序可在浏览器工具栏的“添加”入口管理")
    Text("数据与诊断", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    DestinationInfoRow("缓存管理", "缓存清理与详细状态控制正在开发中")
    DestinationInfoRow("诊断信息", "当前版本不会自动上传个人账本或钱包私密信息；诊断导出功能正在开发中")
}

@Composable
private fun SecurityDestinationContent() {
    val accounts by WalletConnectSessionDelegate.accounts.collectAsState()
    val origins by DappOriginPermissionStore.authorizedOrigins.collectAsState()

    DestinationInfoCard {
        Text("非托管安全边界", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(
            "Airdrop Hunter 不创建、不托管也不保存钱包私钥或助记词。钱包签名和交易确认由外部钱包完成；请只连接可信网站，并在外部钱包中核对每笔请求。",
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }

    Text("外部钱包会话", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (accounts.isEmpty()) {
        DestinationInfoCard {
            Text("当前没有可读取的外部钱包账户会话", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        accounts.forEach { account ->
            DestinationInfoCard {
                Text(account.walletLabel, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("网络：${account.chainId}", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(account.address, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    DestinationInfoCard {
        Text(
            "外部钱包连接由对应钱包 App 管理。当前版本不伪造本地断开状态；如需结束会话，请在 MetaMask、OKX Wallet 等外部钱包的已连接网站/会话管理中撤销。",
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("网站授权", Modifier.weight(1f), color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (origins.isNotEmpty()) TextButton(onClick = { DappOriginPermissionStore.revokeAll() }) { Text("全部撤销") }
    }
    if (origins.isEmpty()) {
        DestinationInfoCard {
            Text("当前没有已授权的网站来源", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        origins.keys.sorted().forEach { origin ->
            DestinationInfoCard {
                Text(origin, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("此授权仅在当前进程和对应钱包会话有效；会话变化后自动清除。", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { DappOriginPermissionStore.revoke(origin) }, modifier = Modifier.align(Alignment.End)) {
                    Text("撤销此网站授权", color = AppColors.Danger)
                }
            }
        }
    }
    Text(
        "站点授权保存在内存中，不会跨应用进程持久化。撤销后，该网站不能继续通过本应用读取已授权账户；重新授权必须再次经过用户确认。",
        color = AppColors.TextHelper,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun NotificationsDestinationContent() {
    DestinationInfoCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("暂无通知", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "当前还没有站内通知。通知列表和已读状态入口已预留；在真实通知服务接入前，不会生成测试通知或擅自开启推送。",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Text("未读 0 条", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DestinationInfoRow(title: String, description: String) {
    DestinationInfoCard {
        Text(title, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Text(description, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text("开发中", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DestinationInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.Border),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            content = content
        )
    }
}
