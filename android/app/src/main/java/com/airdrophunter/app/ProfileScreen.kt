package com.airdrophunter.app
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun ProfileScreen(modifier: Modifier = Modifier, onLogout: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(Modifier.size(62.dp), shape = CircleShape, color = AppColors.SurfaceElevated) { Icon(Icons.Default.AccountCircle, "头像占位", tint = AppColors.Primary, modifier = Modifier.padding(7.dp)) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("我的账户", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Airdrop Hunter 用户中心", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    }
                }
            }
        }
        item { Column { Text("收益总览", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary); Text("演示数据 · 后续接入真实统计", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("今日收益", "+\$12.80", Modifier.weight(1f), AppColors.Success)
            Metric("本周收益", "+\$86.40", Modifier.weight(1f), AppColors.Success)
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("总 Gas 费", "\$24.60", Modifier.weight(1f))
            Metric("累计收益", "+\$328.50", Modifier.weight(1f), AppColors.Success)
        } }
        item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border)) {
            Column(Modifier.padding(16.dp)) {
                Text("数据说明", color = AppColors.TextPrimary)
                Text("当前金额仅用于展示界面，不代表真实钱包余额或实际收益。", color = AppColors.TextSecondary)
            }
        } }
        item { Button(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppColors.SurfaceElevated, contentColor = AppColors.Danger)) { Text("退出登录") } }
    }
}
@Composable
private fun Metric(title: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = AppColors.TextPrimary) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("USD · 示例", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}
