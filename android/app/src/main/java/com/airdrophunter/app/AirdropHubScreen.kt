package com.airdrophunter.app

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(hubViewModel.chains) { chain ->
                val selected = state.selectedChain == chain
                Surface(
                    onClick = { hubViewModel.selectChain(chain) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) AppColors.Primary else AppColors.Surface,
                    border = if (selected) null else BorderStroke(1.dp, AppColors.Border)
                ) {
                    Text(
                        chain,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                        color = if (selected) AppColors.Surface else AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        if (state.sourceError != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                color = AppColors.DangerBackground,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "数据源提醒：${state.sourceError}",
                    modifier = Modifier.padding(12.dp),
                    color = AppColors.Danger,
                    style = MaterialTheme.typography.bodySmall
                )
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
                Text("暂时无法加载空投数据", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(state.error.orEmpty(), color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = hubViewModel::refresh,
                    enabled = !state.isRefreshing,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    if (state.isRefreshing) CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), color = AppColors.Surface, strokeWidth = 2.dp
                    ) else Text("重新拉取数据")
                }
            }

            state.projects.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("当前链暂时没有收录项目", color = AppColors.TextHelper)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${state.projects.size} 个空投项目",
                            Modifier.weight(1f),
                            color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                        TextButton(onClick = hubViewModel::refresh, enabled = !state.isRefreshing) {
                            Text(if (state.isRefreshing) "正在刷新…" else "刷新数据", color = AppColors.Primary)
                        }
                    }
                }
                items(state.projects, key = { it.id }) { project ->
                    Card(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(context, BrowserActivity::class.java)
                                        .putExtra(BrowserActivity.EXTRA_URL, project.link)
                                        .putExtra(BrowserActivity.EXTRA_TITLE, project.name)
                                )
                            }.onFailure {
                                Toast.makeText(context, "无法打开该项目链接", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        border = BorderStroke(1.dp, AppColors.Border),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    project.name,
                                    Modifier.weight(1f),
                                    color = AppColors.TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(color = AppColors.TealTint, shape = RoundedCornerShape(999.dp)) {
                                    Text(
                                        project.chain,
                                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        color = AppColors.Primary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            if (project.description.isNotBlank()) {
                                Text(
                                    project.description,
                                    color = AppColors.TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = AppColors.MutedBackground, shape = RoundedCornerShape(999.dp)) {
                                    Text(
                                        project.status,
                                        Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        color = AppColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text("打开官网  ↗", color = AppColors.Primary,
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
