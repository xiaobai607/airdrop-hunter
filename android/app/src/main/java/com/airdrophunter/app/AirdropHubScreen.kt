package com.airdrophunter.app

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun AirdropHubScreen(
    modifier: Modifier = Modifier,
    vm: AirdropHubViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val hubViewModel: AirdropHubViewModel = vm ?: viewModel(
        factory = AirdropHubViewModelFactory(application.appContainer.airdropHubRepository)
    )
    val state by hubViewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("查看最新官方空投公告", color = AppColors.TextHelper,
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = hubViewModel::refresh, enabled = !state.isRefreshing) {
                Text(if (state.isRefreshing) "刷新中…" else "刷新", color = AppColors.Primary)
            }
        }

        when {
            state.isLoading && state.projects.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AppColors.Primary) }

            state.error != null && state.projects.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("暂时无法加载空投公告", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("请检查网络后重试。", color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = hubViewModel::refresh, enabled = !state.isRefreshing,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                    Text("重新加载")
                }
            }

            state.projects.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center
            ) {
                Text("暂时没有新的空投公告", color = AppColors.TextHelper)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("${state.projects.size} 条空投公告",
                        color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                items(state.projects, key = { it.id }) { item ->
                    Card(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(context, BrowserActivity::class.java)
                                        .putExtra(BrowserActivity.EXTRA_URL, item.link)
                                        .putExtra(BrowserActivity.EXTRA_TITLE, item.name)
                                )
                            }.onFailure {
                                Toast.makeText(context, "无法打开官方公告", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        border = BorderStroke(1.dp, AppColors.Border),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, Modifier.weight(1f), color = AppColors.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold)
                                Surface(color = AppColors.TealTint, shape = RoundedCornerShape(999.dp)) {
                                    Text("官方公告", Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        color = AppColors.Primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text("来自 Binance 官方空投公告中心",
                                color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("查看公告 ↗", color = AppColors.Primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                Text("Binance", color = AppColors.TextHelper,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
