package com.airdrophunter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(62.dp),
                        shape = CircleShape,
                        color = AppColors.SurfaceElevated
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "头像占位",
                            tint = AppColors.Primary,
                            modifier = Modifier.padding(7.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "我的账户",
                            style = MaterialTheme.typography.titleLarge,
                            color = AppColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Airdrop Hunter 用户中心",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "收益总览",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "演示数据 · 后续接入真实统计",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "今日收益",
                    value = "+\$12.80",
                    subtitle = "USD · 示例",
                    modifier = Modifier.weight(1f),
                    valueColor = AppColors.Success
                )
                MetricCard(
                    title = "本周收益",
                    value = "+\$86.40",
                    subtitle = "USD · 示例",
                    modifier = Modifier.weight(1f),
                    valueColor = AppColors.Success
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "总 Gas 费",
                    value = "\$24.60",
                    subtitle = "USD · 示例",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "累计收益",
                    value = "+\$328.50",
                    subtitle = "USD · 示例",
                    modifier = Modifier.weight(1f),
                    valueColor = AppColors.Success
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "数据说明",
                        style = MaterialTheme.typography.titleSmall,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        "当前金额仅用于展示界面，不代表真实钱包余额或实际收益。后续将根据项目记录、用户确认的领取金额及 Gas 支出生成统计。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = AppColors.TextPrimary
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
        }
    }
}
