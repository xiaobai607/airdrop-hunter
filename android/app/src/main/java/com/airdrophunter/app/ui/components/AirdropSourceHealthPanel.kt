package com.airdrophunter.app.ui.components

import com.airdrophunter.app.R

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
import androidx.compose.ui.res.stringResource
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
    val available = statuses.count { it.status in setOf("success", "empty", "stale") }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.hub_source_status_title), color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.hub_source_counts, active, available, failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                AppTextButton(onClick = onToggle) { Text(if (expanded) stringResource(R.string.hub_source_collapse) else stringResource(R.string.hub_source_diagnose)) }
            }
            val localizedError = when {
                error == "暂时无法读取数据源状态" -> stringResource(R.string.hub_source_status_unavailable)
                error == "数据源暂时不可用，已保留上次数据。" -> stringResource(R.string.hub_source_unavailable_cached)
                error?.startsWith("刚刚刷新过") == true -> stringResource(R.string.hub_source_rate_limited)
                else -> error
            }
            if (localizedError != null) {
                Text(localizedError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (expanded) {
                statuses.forEach { source ->
                    val statusLabel = when (source.status) {
                        "success" -> stringResource(R.string.hub_source_normal)
                        "partial" -> stringResource(R.string.hub_source_partial)
                        "empty" -> stringResource(R.string.hub_source_no_new_records)
                        "stale" -> stringResource(R.string.hub_source_cached)
                        "failed" -> stringResource(R.string.hub_source_failed)
                        "disabled" -> stringResource(R.string.hub_source_not_configured)
                        "not_run" -> stringResource(R.string.hub_source_waiting_first_run)
                        else -> stringResource(R.string.hub_source_unknown)
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
                            Surface(color = chipBackground, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                                Text(statusLabel, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = chipForeground, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val timeValue = source.last_success_at ?: source.last_attempt_at
                        val timeLabel = timeValue?.let {
                            runCatching { DateFormat.getDateTimeInstance().format(Date(Instant.parse(it).toEpochMilli())) }.getOrNull()
                        }
                        val timePrefix = if (source.last_success_at != null) stringResource(R.string.hub_source_last_success) else stringResource(R.string.hub_source_last_attempt)
                        val recordCount = stringResource(R.string.hub_source_record_count, source.last_item_count)
                        Text(
                            buildString {
                                append(recordCount)
                                if (timeLabel != null) append(" · $timePrefix: $timeLabel")
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
