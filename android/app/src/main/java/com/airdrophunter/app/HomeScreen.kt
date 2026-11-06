package com.airdrophunter.app

import android.widget.Toast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.ui.theme.AppColors
import java.util.Locale

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSources: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenAirdropHub: () -> Unit = {},
    isGuest: Boolean = true,
    onRequireAuth: () -> Unit = {},
    vm: DashboardMockViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSearch by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().background(AppColors.Background)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("指挥中心", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium,
                color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showSearch = true }) {
                Icon(Icons.Default.Search, "搜索", tint = AppColors.TextSecondary)
            }
            IconButton(onClick = { showNotifications = true }) {
                Icon(Icons.Default.NotificationsNone, "通知", tint = AppColors.TextSecondary)
            }
        }

        Card(
            onClick = onOpenAirdropHub,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            border = BorderStroke(1.dp, AppColors.Border),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本周空投窗口", Modifier.weight(1f), color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Surface(color = AppColors.TealTint, shape = RoundedCornerShape(999.dp)) {
                        Text("进行中", Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            color = AppColors.Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.weekTotal.toString(), color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                    Text("个待交互机会", Modifier.padding(bottom = 9.dp), color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(state.weekTotal) { index ->
                        Box(Modifier.weight(1f).height(7.dp).background(
                            if (index < state.weekDone) AppColors.Primary else AppColors.Border,
                            RoundedCornerShape(99.dp)
                        ))
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.weekRange, Modifier.weight(1f), color = AppColors.TextHelper,
                        style = MaterialTheme.typography.labelSmall)
                    Text("${state.weekDone} / ${state.weekTotal}", color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("入口")
            state.entries.forEach { entry ->
                val icon = when (entry.iconKey) {
                    "shield" -> Icons.Default.Shield
                    "folder" -> Icons.Default.FolderOpen
                    else -> Icons.Default.Explore
                }
                val action = when (entry.iconKey) {
                    "shield" -> onOpenWallet
                    "folder" -> onOpenProjects
                    else -> onOpenSources
                }
                Card(
                    onClick = action,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    border = BorderStroke(1.dp, AppColors.Border),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = RoundedCornerShape(11.dp), color = AppColors.TealTint, modifier = Modifier.size(46.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, entry.title, tint = AppColors.Primary, modifier = Modifier.size(23.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(entry.title, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                            Text(entry.subtitle, color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("›", color = AppColors.TextHelper, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("行情看板", modifier = Modifier.clickable { Toast.makeText(context, "行情详情页正在开发中", Toast.LENGTH_SHORT).show() }, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("全部", modifier = Modifier.clickable { Toast.makeText(context, "行情详情页正在开发中", Toast.LENGTH_SHORT).show() }, color = AppColors.Primary, style = MaterialTheme.typography.labelLarge)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column {
                    state.markets.forEachIndexed { index, coin ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(coin.symbol, Modifier.weight(1f), color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Sparkline(coin.spark, coin.positive, Modifier.width(76.dp).height(26.dp))
                            Column(Modifier.weight(1.35f), horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(coin.price, color = AppColors.TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(coin.change, color = if (coin.positive) AppColors.Success else AppColors.Danger,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (index != state.markets.lastIndex) HorizontalDivider(color = AppColors.Border, thickness = 1.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showSearch) AlertDialog(
        onDismissRequest = { showSearch = false },
        containerColor = AppColors.Surface,
        title = { Text("搜索", color = AppColors.TextPrimary) },
        text = { Text("搜索入口将在后续版本接入。", color = AppColors.TextSecondary) },
        confirmButton = { TextButton(onClick = { showSearch = false }) { Text("知道了") } }
    )
    if (showNotifications) AlertDialog(
        onDismissRequest = { showNotifications = false },
        containerColor = AppColors.Surface,
        title = { Text("消息通知", color = AppColors.TextPrimary) },
        text = { Text("当前有 3 条模拟提醒。", color = AppColors.TextSecondary) },
        confirmButton = { TextButton(onClick = { showNotifications = false }) { Text("关闭") } }
    )
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold)
}

@Composable
private fun Sparkline(values: List<Float>, positive: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val color = if (positive) AppColors.Success else AppColors.Danger
        val step = size.width / (values.size - 1)
        for (i in 0 until values.lastIndex) {
            drawLine(
                color = color,
                start = Offset(i * step, size.height * (1f - values[i])),
                end = Offset((i + 1) * step, size.height * (1f - values[i + 1])),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}