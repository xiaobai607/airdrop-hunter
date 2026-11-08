package com.airdrophunter.app

import android.widget.Toast
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.officialIdentifier
import com.airdrophunter.app.data.CacheStore
import androidx.compose.runtime.collectAsState
import com.airdrophunter.app.ui.theme.AppColors
import java.util.Locale

@Composable
fun ProjectsScreen(
    modifier: Modifier = Modifier,
    onProjectSelected: (ProjectDto) -> Unit,
    isGuest: Boolean = true,
    onRequireAuth: () -> Unit = {},
    onOpenCommunity: (ProjectDto) -> Unit = {},
    vm: ProjectsViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val projectsViewModel: ProjectsViewModel = vm ?: viewModel(
        factory = ProjectsViewModelFactory(application.appContainer.projectsRepository)
    )
    val state by projectsViewModel.uiState.collectAsStateWithLifecycle()
    val pnlRecords by CacheStore.observePnlRecords().collectAsState(initial = emptyList())
    val savedProjects by CacheStore.observeUserProjects().collectAsState(initial = emptyList())
    val allRatings by CacheStore.observeAllProjectRatings().collectAsState(initial = emptyList())
    var selectedFilter by remember { mutableStateOf("全部") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var newProjectDescription by remember { mutableStateOf("") }
    var newProjectOfficialUrl by remember { mutableStateOf("") }
    var newProjectClaimUrl by remember { mutableStateOf("") }

    LaunchedEffect(isGuest) { projectsViewModel.loadProjects(isGuest) }

    val savedProjectDtos = savedProjects.map { saved ->
        ProjectDto(
            id = saved.projectLocalId,
            name = saved.projectName,
            description = "",
            status = saved.status,
            official_url = CommunityProjectCatalog.findByOfficialId(saved.projectOfficialId)?.officialUrl.orEmpty(),
            communityOfficialId = saved.projectOfficialId
        )
    }
    val visibleProjects = (savedProjectDtos + state.projects).distinctBy {
        it.communityOfficialId?.let { officialId -> "official:$officialId" } ?: "local:${it.id}"
    }
    val filterOptions = listOf("全部" to visibleProjects.size) +
        listOf(
            "进行中" to visibleProjects.count { it.status == "tracking" },
            "待开始" to visibleProjects.count { it.status == "upcoming" },
            "已完成" to visibleProjects.count { it.status == "completed" }
        )
    val filtered = when (selectedFilter) {
        "进行中" -> visibleProjects.filter { it.status == "tracking" }
        "待开始" -> visibleProjects.filter { it.status == "upcoming" }
        "已完成" -> visibleProjects.filter { it.status == "completed" }
        else -> visibleProjects
    }

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("项目", Modifier.weight(1f), color = AppColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { selectedFilter = if (selectedFilter == "全部") "进行中" else "全部" }) {
                Icon(Icons.Default.FolderOpen, "筛选项目", tint = AppColors.TextSecondary)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            filterOptions.forEach { (label, count) ->
                val selected = selectedFilter == label
                Surface(
                    onClick = { selectedFilter = label },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) AppColors.Primary else AppColors.Surface,
                    border = if (selected) null else BorderStroke(1.dp, AppColors.Border)
                ) {
                    Text(
                        "$label $count",
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        color = if (selected) AppColors.Surface else AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        when {
            state.isLoading && state.projects.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AppColors.Primary) }
            state.error != null && state.projects.isEmpty() -> EmptyProjects(
                modifier = Modifier.weight(1f),
                title = "暂时无法加载项目",
                subtitle = "服务器繁忙，请稍后重试。"
            )
            filtered.isEmpty() -> EmptyProjects(
                modifier = Modifier.weight(1f),
                title = "还没有添加项目",
                subtitle = if (isGuest) "添加的项目只保存在这台设备上。" else "添加项目后，这里会显示你的项目与任务进度。"
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { project ->
                    val pnl = pnlRecords.firstOrNull { it.projectLocalId == project.id }
                    val gasTotal = pnl?.totalGasCostUsd ?: 0.0
                    val rewardTotal = pnl?.totalRewardUsd ?: 0.0
                    val netProfit = pnl?.netProfitUsd ?: 0.0
                    val projectRatings = allRatings.filter { it.projectOfficialId == project.communityOfficialId }
                    val communityAverage = if (projectRatings.isEmpty()) 4.9 else projectRatings.map { it.score }.average()
                    val positiveRate = if (projectRatings.isEmpty()) 96 else projectRatings.count { it.score >= 4 } * 100 / projectRatings.size
                    val communityLabel = when {
                        positiveRate >= 95 -> "好评如潮"
                        positiveRate >= 85 -> "特别好评"
                        positiveRate >= 75 -> "多半好评"
                        else -> "褒贬不一"
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onProjectSelected(project) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        border = BorderStroke(1.dp, AppColors.Border),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(project.name, Modifier.weight(1f), color = AppColors.TextPrimary,
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                    val statusLabel = when (project.status) { "tracking" -> "进行中"; "completed" -> "已完成"; else -> "待开始" }
                                    Surface(color = AppColors.TealTint, shape = RoundedCornerShape(999.dp)) {
                                        Text(statusLabel, Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            color = AppColors.Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Text(String.format(Locale.US, "累计消耗 $%.2f  ·  累计收益 $%.2f", gasTotal, rewardTotal),
                                    color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                Text(String.format(Locale.US, "真实净盈亏  %s$%.2f",
                                    if (netProfit >= 0) "+" else "-", kotlin.math.abs(netProfit)),
                                    color = if (netProfit >= 0) AppColors.Success else AppColors.Danger,
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                Text("去交互  ›", color = AppColors.Primary, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                                        if (project.official_url.startsWith("https://")) {
                                            context.startActivity(Intent(context, BrowserActivity::class.java)
                                                .putExtra(BrowserActivity.EXTRA_URL, project.official_url)
                                                .putExtra(BrowserActivity.EXTRA_TITLE, project.name))
                                        } else onProjectSelected(project)
                                    })
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(onClick = {
                                    if (project.communityOfficialId != null) onOpenCommunity(project)
                                    else Toast.makeText(context, "这是纯本地项目，尚未关联社区标准项目", Toast.LENGTH_SHORT).show()
                                }, shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFFFFF4E5), border = BorderStroke(1.dp, Color(0xFFFFD9A8))) {
                                    Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (project.communityOfficialId != null) "微社区" else "本地项目", color = Color(0xFF9A5B12), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFA629), modifier = Modifier.size(13.dp))
                                        Text(String.format(Locale.US, "%.1f", communityAverage), color = Color(0xFF9A5B12),
                                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(communityLabel, color = Color(0xFFB7791F), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }


    if (showAddDialog) AlertDialog(
        onDismissRequest = { showAddDialog = false },
        containerColor = AppColors.Surface,
        title = { Text("添加项目", color = AppColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(newProjectName, { newProjectName = it }, label = { Text("项目名称") }, singleLine = true)
                OutlinedTextField(newProjectDescription, { newProjectDescription = it }, label = { Text("项目说明") })
                OutlinedTextField(newProjectOfficialUrl, { newProjectOfficialUrl = it }, label = { Text("项目官网 HTTPS 链接") }, singleLine = true)
                OutlinedTextField(newProjectClaimUrl, { newProjectClaimUrl = it }, label = { Text("官方资格查询 HTTPS 链接（可选）") }, singleLine = true)
                Text("资格查询链接请填写项目方官方页面，不要填写来历不明的第三方链接。",
                    color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = AppColors.Danger) }
            }
        },
        confirmButton = {
            Button(enabled = newProjectName.isNotBlank() && !state.isSaving,
                onClick = {
                    projectsViewModel.createProject(com.airdrophunter.app.data.ProjectWriteRequest(
                        name = newProjectName.trim(),
                        description = newProjectDescription.trim(),
                        status = "tracking",
                        official_url = newProjectOfficialUrl.trim(),
                        claim_url = newProjectClaimUrl.trim()
                    ), isGuest)
                    showAddDialog = false
                    newProjectName = ""; newProjectDescription = ""; newProjectOfficialUrl = ""; newProjectClaimUrl = ""
                }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) { Text("保存项目") }
        },
        dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.padding(end = 20.dp, bottom = 20.dp).size(58.dp),
            shape = RoundedCornerShape(50),
            containerColor = AppColors.Primary,
            contentColor = AppColors.Surface
        ) { Icon(Icons.Default.Add, "添加项目") }
    }
}

@Composable
private fun EmptyProjects(modifier: Modifier = Modifier, title: String, subtitle: String) {
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null,
            tint = AppColors.TextHelper, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}
