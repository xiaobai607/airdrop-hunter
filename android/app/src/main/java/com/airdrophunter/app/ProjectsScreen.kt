package com.airdrophunter.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.ui.theme.AppColors
import java.util.Locale

@Composable
fun ProjectsScreen(
    modifier: Modifier = Modifier,
    onProjectSelected: (ProjectDto) -> Unit,
    isGuest: Boolean = true,
    onRequireAuth: () -> Unit = {},
    vm: ProjectsViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("全部") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var newProjectDescription by remember { mutableStateOf("") }
    var newProjectOfficialUrl by remember { mutableStateOf("") }
    var newProjectClaimUrl by remember { mutableStateOf("") }

    LaunchedEffect(isGuest) { vm.loadProjects(isGuest) }

    val filterOptions = listOf("全部" to state.projects.size) +
        listOf(
            "进行中" to state.projects.count { it.status == "tracking" },
            "待开始" to state.projects.count { it.status == "upcoming" },
            "已完成" to state.projects.count { it.status == "completed" }
        )
    val filtered = when (selectedFilter) {
        "进行中" -> state.projects.filter { it.status == "tracking" }
        "待开始" -> state.projects.filter { it.status == "upcoming" }
        "已完成" -> state.projects.filter { it.status == "completed" }
        else -> state.projects
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
                    Card(
                        onClick = { onProjectSelected(project) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        border = BorderStroke(1.dp, AppColors.Border),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(project.name, Modifier.weight(1f), color = AppColors.TextPrimary,
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                val label = when (project.status) {
                                    "tracking" -> "进行中"
                                    "completed" -> "已完成"
                                    else -> "待开始"
                                }
                                Surface(color = AppColors.TealTint, shape = RoundedCornerShape(999.dp)) {
                                    Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        color = AppColors.Primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (project.description.isNotBlank()) {
                                Text(project.description, color = AppColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            project.deadline?.takeIf { it.isNotBlank() }?.let {
                                Text("截止时间：$it", color = AppColors.TextHelper,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            if (project.expected_income > 0 || project.actual_income > 0) {
                                Text(
                                    String.format(Locale.US, "实际收益 $%.2f · 预期 $%.2f",
                                        project.actual_income, project.expected_income),
                                    color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall
                                )
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
                    vm.createProject(com.airdrophunter.app.data.ProjectWriteRequest(
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
