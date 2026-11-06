package com.airdrophunter.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.MarketPrice
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
    vm: HomeViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSearch by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }

    LaunchedEffect(isGuest) { vm.load(isGuest) }

    Column(
        modifier.fillMaxSize().background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("首页", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium,
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("空投聚合大厅", color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("浏览从公开数据源同步的项目", color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("›", color = AppColors.Primary, style = MaterialTheme.typography.headlineMedium)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("快捷入口", color = AppColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val entries = listOf(
                Triple("空投导航", "打开常用空投信息网站", Icons.Default.Explore),
                Triple("安全查钱包", "通过公链网络验证公开地址", Icons.Default.Shield),
                Triple("我的项目", "查看已添加的项目与任务", Icons.Default.FolderOpen)
            )
            entries.forEachIndexed { index, entry ->
                val action = when (index) {
                    0 -> onOpenSources
                    1 -> onOpenWallet
                    else -> onOpenProjects
                }
                Card(
                    onClick = action,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    border = BorderStroke(1.dp, AppColors.Border),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(9.dp), color = AppColors.TealTint,
                            modifier = Modifier.size(38.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(entry.third, entry.first, tint = AppColors.Primary,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.first, color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(entry.second, color = AppColors.TextHelper,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text("›", color = AppColors.TextHelper, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("行情看板", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "全部",
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "功能开发中", Toast.LENGTH_SHORT).show()
                    }.padding(6.dp),
                    color = AppColors.Primary, style = MaterialTheme.typography.labelLarge
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                when {
                    state.isLoading && state.market == null -> Box(
                        Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = AppColors.Primary) }
                    state.market?.prices.isNullOrEmpty() -> Text(
                        if (state.hasError) "行情暂不可用，请稍后重试" else "暂无行情数据",
                        Modifier.padding(18.dp), color = AppColors.TextSecondary
                    )
                    else -> Column {
                        state.market?.prices.orEmpty().forEachIndexed { index, price ->
                            MarketRow(price) {
                                Toast.makeText(context, "功能开发中", Toast.LENGTH_SHORT).show()
                            }
                            if (index != state.market?.prices?.lastIndex) {
                                HorizontalDivider(color = AppColors.Border, thickness = 1.dp)
                            }
                        }
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
        text = { Text("搜索功能正在开发中。", color = AppColors.TextSecondary) },
        confirmButton = { TextButton(onClick = { showSearch = false }) { Text("知道了") } }
    )
    if (showNotifications) AlertDialog(
        onDismissRequest = { showNotifications = false },
        containerColor = AppColors.Surface,
        title = { Text("消息通知", color = AppColors.TextPrimary) },
        text = { Text("通知功能正在开发中。", color = AppColors.TextSecondary) },
        confirmButton = { TextButton(onClick = { showNotifications = false }) { Text("关闭") } }
    )
}

@Composable
private fun MarketRow(price: MarketPrice, onClick: () -> Unit) {
    val value = price.price_usd?.let {
        if (it >= 1) String.format(Locale.US, "$%,.2f", it)
        else String.format(Locale.US, "$%.6f", it)
    } ?: "—"
    val change = price.change_24h?.let { String.format(Locale.US, "%+.2f%%", it) } ?: "—"
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(price.symbol, color = AppColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(price.name, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = AppColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(change, color = when {
                price.change_24h == null -> AppColors.TextHelper
                price.change_24h >= 0 -> AppColors.Success
                else -> AppColors.Danger
            }, style = MaterialTheme.typography.labelSmall)
        }
    }
}
