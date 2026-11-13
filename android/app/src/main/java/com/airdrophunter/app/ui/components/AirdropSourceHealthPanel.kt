package com.airdrophunter.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.data.AirdropHubSourceStatusDto
import com.airdrophunter.app.ui.theme.AppDesignTokens
import java.text.DateFormat
import java.time.Instant
import java.util.Date

@Composable
fun AirdropSourceHealthPanel(
    statuses: List<AirdropHubSourceStatusDto>,
    error: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = statuses.count { it.enabled }
    val failed = statuses.count { it.status == "failed" || it.status == "partial" }
    val successful = statuses.count { it.status in setOf("success", "empty", "stale") }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("免费数据源状态", color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "启用 $active · 正常 $successful · 异常 $failed",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                AppTextButton(onClick = onToggle) { Text(if (expanded) "收起" else "诊断") }
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            if (expanded) {
                statuses.forEach { source ->
                    val statusLabel = when (source.status) {
                        "success" -> "正常"
                        "partial" -> "部分成功"
                        "empty" -> "暂无新记录"
                        "stale" -> "使用缓存"
                        "failed" -> "获取失败"
                        "disabled" -> "未配置"
                        "not_run" -> "等待首次运行"
                        else -> "状态未知"
                    }
                    val chipBackground = when (source.status) {
                        "success" -> MaterialTheme.colorScheme.primaryContainer
                        "partial" -> MaterialTheme.colorScheme.tertiaryContainer
                        "failed" -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val chipForeground = when (source.status) {
                        "success" -> MaterialTheme.colorScheme.onPrimaryContainer
                        "partial" -> MaterialTheme.colorScheme.onTertiaryContainer
                        "failed" -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                source.label,
                                Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(color = chipBackground, shape = RoundedCornerShape(999.dp)) {
                                Text(statusLabel, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = chipForeground, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val timeValue = source.last_success_at ?: source.last_attempt_at
                        val timeLabel = timeValue?.let {
                            runCatching { DateFormat.getDateTimeInstance().format(Date(Instant.parse(it).toEpochMilli())) }.getOrNull()
                        }
                        val timePrefix = if (source.last_success_at != null) "最近成功" else "最近尝试"
                        Text(
                            buildString {
                                append("记录 ${source.last_item_count}")
                                if (timeLabel != null) append(" · $timePrefix：$timeLabel")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                        source.last_error?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                color = if (source.enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
