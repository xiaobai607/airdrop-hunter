package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    isGuest: Boolean = true,
    onRequireAuth: () -> Unit = {},
    vm: ProfileMockViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier.fillMaxSize().background(AppColors.Background)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(shape = CircleShape, color = AppColors.MutedBackground, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("L", color = AppColors.TextSecondary, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (isGuest) "未登录" else "账户中心", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (isGuest) "游客模式" else "已登录", color = AppColors.TextHelper,
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            border = BorderStroke(1.dp, AppColors.Border),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth().height(100.dp)) {
                    StatCell("跟踪项目", state.tracked, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(AppColors.Border))
                    StatCell("已完成", state.completed, Modifier.weight(1f))
                }
                HorizontalDivider(color = AppColors.Border)
                Row(Modifier.fillMaxWidth().height(100.dp)) {
                    StatCell("累计收益", state.earnings, Modifier.weight(1f), "USD")
                    Box(Modifier.width(1.dp).fillMaxHeight().background(AppColors.Border))
                    StatCell("交互天数", state.days, Modifier.weight(1f))
                }
            }
        }

        Button(
            onClick = { if (isGuest) onRequireAuth() else onLogout() },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.Surface)
        ) {
            Text(if (isGuest) "登录/注册" else "退出登录", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("设置", color = AppColors.TextHelper, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            SettingRow("钱包管理", Icons.Default.Wallet)
            SettingRow("消息通知", Icons.Default.NotificationsNone, badge = "3")
            SettingRow("安全设置", Icons.Default.Security)
            SettingRow("关于", Icons.Default.Info)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier, suffix: String = "") {
    Column(
        modifier.fillMaxHeight().padding(horizontal = 14.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = AppColors.TextPrimary, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            if (suffix.isNotBlank()) Text(suffix, color = AppColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String? = null
) {
    Row(
        Modifier.fillMaxWidth().height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(21.dp))
        Text(title, Modifier.weight(1f), color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium)
        if (badge != null) {
            Surface(color = AppColors.TealTint, shape = RoundedCornerShape(999.dp)) {
                Text(badge, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = AppColors.Primary, style = MaterialTheme.typography.labelSmall)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextHelper)
    }
}