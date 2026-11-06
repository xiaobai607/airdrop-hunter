package com.airdrophunter.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.ui.theme.AppColors
import java.util.Locale

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    isGuest: Boolean = true,
    onRequireAuth: () -> Unit = {},
    vm: ProfileViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(isGuest) { vm.load(isGuest) }
    val stats = if (isGuest) com.airdrophunter.app.data.UserStatsDto() else state.stats

    Column(
        modifier.fillMaxSize().background(AppColors.Background)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            Modifier.clickable { if (isGuest) onRequireAuth() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Surface(shape = CircleShape, color = AppColors.MutedBackground, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (isGuest) "G" else "U", color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (isGuest) "游客模式" else "账户中心", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (isGuest) "登录后同步个人数据" else "数据来自你的账户记录",
                    color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
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
                    StatCell("跟踪项目", stats.tracked_projects.toString(), Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(AppColors.Border))
                    StatCell("已完成", stats.completed_projects.toString(), Modifier.weight(1f))
                }
                HorizontalDivider(color = AppColors.Border)
                Row(Modifier.fillMaxWidth().height(100.dp)) {
                    StatCell("累计实际收益",
                        String.format(Locale.US, "%.2f", stats.total_actual_income),
                        Modifier.weight(1f), "USD")
                    Box(Modifier.width(1.dp).fillMaxHeight().background(AppColors.Border))
                    StatCell("交互天数", stats.interaction_days.toString(), Modifier.weight(1f))
                }
            }
        }

        if (state.hasError && !isGuest) {
            Text("统计数据暂时无法加载，服务器繁忙。", color = AppColors.Danger,
                style = MaterialTheme.typography.bodySmall)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("设置", color = AppColors.TextHelper, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            SettingRow("钱包管理", Icons.Default.Wallet) {
                Toast.makeText(context, "钱包管理功能正在开发中", Toast.LENGTH_SHORT).show()
            }
            SettingRow("消息通知", Icons.Default.NotificationsNone) {
                Toast.makeText(context, "消息通知功能正在开发中", Toast.LENGTH_SHORT).show()
            }
            SettingRow("安全设置", Icons.Default.Security) {
                Toast.makeText(context, "安全设置功能正在开发中", Toast.LENGTH_SHORT).show()
            }
            SettingRow("关于", Icons.Default.Info) {
                Toast.makeText(context, "关于页面正在开发中", Toast.LENGTH_SHORT).show()
            }
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
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(21.dp))
        Text(title, Modifier.weight(1f), color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextHelper)
    }
}
