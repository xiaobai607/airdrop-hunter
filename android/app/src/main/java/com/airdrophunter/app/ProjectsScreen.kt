package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun ProjectsScreen(
    modifier: Modifier = Modifier,
    onProjectSelected: (com.airdrophunter.app.data.ProjectDto) -> Unit,
    isGuest: Boolean = true,
    onRequireAuth: () -> Unit = {},
    vm: ProjectsMockViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("全部") }
    var showAddHint by remember { mutableStateOf(false) }
    val visibleProjects = if (selectedFilter == "全部") state.projects
        else state.projects.filter { it.filter == selectedFilter }

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("项目", Modifier.weight(1f), color = AppColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { selectedFilter = if (selectedFilter == "全部") "待交互" else "全部" }) {
                Icon(Icons.Default.GridView, "切换项目视图", tint = AppColors.TextSecondary)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.filters.forEach { (label, count) ->
                val selected = selectedFilter == label
                Surface(
                    onClick = { selectedFilter = label },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) AppColors.Primary else ColorTransparent,
                    border = if (selected) null else BorderStroke(1.dp, AppColors.Border)
                ) {
                    Text(
                        "$label $count",
                        Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        color = if (selected) AppColors.Surface else AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(visibleProjects, key = { it.name }) { project ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    border = BorderStroke(1.dp, AppColors.Border),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(project.name, Modifier.weight(1f), color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            val badgeColor = when (project.status) {
                                "已空投" -> AppColors.SuccessBackground
                                "已提交" -> AppColors.MutedBackground
                                else -> AppColors.TealTint
                            }
                            Surface(color = badgeColor, shape = RoundedCornerShape(999.dp)) {
                                Text(project.status, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    color = if (project.status == "已空投") AppColors.Success else AppColors.Primary,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        LinearProgressIndicator(
                            progress = { project.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = AppColors.Primary,
                            trackColor = AppColors.Border,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("待办 ${project.todo} 项 · 截止 ${project.deadline}",
                                Modifier.weight(1f), color = AppColors.TextHelper,
                                style = MaterialTheme.typography.labelSmall)
                            Text(project.wallet, color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (visibleProjects.isEmpty()) {
                item { Text("暂无项目", color = AppColors.TextHelper, modifier = Modifier.padding(20.dp)) }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        FloatingActionButton(
            onClick = { if (isGuest) onRequireAuth() else showAddHint = true },
            modifier = Modifier.padding(end = 20.dp, bottom = 20.dp).size(58.dp),
            shape = RoundedCornerShape(50),
            containerColor = AppColors.Primary,
            contentColor = AppColors.Surface
        ) { Icon(Icons.Default.Add, "添加项目") }
    }

    if (showAddHint) AlertDialog(
        onDismissRequest = { showAddHint = false },
        containerColor = AppColors.Surface,
        title = { Text("添加项目", color = AppColors.TextPrimary) },
        text = { Text("当前展示的是设计验收用 Mock 项目数据。", color = AppColors.TextSecondary) },
        confirmButton = { TextButton(onClick = { showAddHint = false }) { Text("知道了") } }
    )
}

private val ColorTransparent = androidx.compose.ui.graphics.Color.Transparent