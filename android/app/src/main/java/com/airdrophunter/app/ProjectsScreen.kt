package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun ProjectsScreen(
    modifier: Modifier = Modifier,
    projectsViewModel: ProjectsViewModel = viewModel()
) {
    val uiState by projectsViewModel.uiState.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<ProjectDto?>(null) }
    var deletingProject by remember { mutableStateOf<ProjectDto?>(null) }

    LaunchedEffect(Unit) {
        projectsViewModel.loadProjects()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "我的项目",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            "追踪你的空投参与进度",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AppColors.Surface,
                        border = BorderStroke(1.dp, AppColors.Border)
                    ) {
                        Text(
                            "${uiState.projects.size} 个项目",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }

            if (uiState.message != null) {
                item {
                    Text(
                        uiState.message!!,
                        color = AppColors.Success,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (uiState.error != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "操作失败：${uiState.error}",
                            color = AppColors.Danger,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(
                            onClick = projectsViewModel::loadProjects,
                            enabled = !uiState.isLoading
                        ) {
                            Text("重新加载")
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.projects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppColors.Primary)
                    }
                }
            } else if (uiState.projects.isEmpty()) {
                item {
                    EmptyProjectsState()
                }
            } else {
                items(items = uiState.projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        actionsEnabled = !uiState.isSaving,
                        onEdit = {
                            editingProject = project
                            showEditor = true
                        },
                        onDelete = { deletingProject = project }
                    )
                }
            }

            if (uiState.isSaving) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = AppColors.Primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingProject = null
                showEditor = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            shape = CircleShape,
            containerColor = AppColors.Primary,
            contentColor = AppColors.Background
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加项目",
                modifier = Modifier.size(26.dp)
            )
        }
    }

    if (showEditor) {
        ProjectEditorDialog(
            project = editingProject,
            saving = uiState.isSaving,
            onDismiss = { showEditor = false },
            onSave = { name, description, status ->
                val current = editingProject
                if (current == null) {
                    projectsViewModel.createProject(name, description, status)
                } else {
                    projectsViewModel.updateProject(current, name, description, status)
                }
                showEditor = false
            }
        )
    }

    deletingProject?.let { project ->
        AlertDialog(
            onDismissRequest = { deletingProject = null },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("删除项目") },
            text = { Text("确定删除「${project.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        projectsViewModel.deleteProject(project)
                        deletingProject = null
                    }
                ) {
                    Text("确认删除", color = AppColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProject = null }) {
                    Text("取消", color = AppColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ProjectCard(
    project: ProjectDto,
    actionsEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    if (project.description.isNotBlank()) {
                        Text(
                            project.description,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onEdit, enabled = actionsEnabled) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑项目",
                        tint = AppColors.TextSecondary
                    )
                }
                IconButton(onClick = onDelete, enabled = actionsEnabled) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "删除项目",
                        tint = AppColors.Danger
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProjectStatusChip(project.status)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "ID  ${project.id.take(8)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ProjectStatusChip(status: String) {
    val isActive = status == "tracking"
    val isCompleted = status == "completed"
    val label = when (status) {
        "tracking" -> "进行中"
        "completed" -> "已完成"
        else -> "待开始"
    }
    val background = if (isActive) AppColors.SuccessBackground else AppColors.MutedBackground
    val foreground = if (isActive) AppColors.Success else AppColors.TextSecondary

    Surface(
        color = if (isCompleted) AppColors.NavIndicator else background,
        shape = RoundedCornerShape(7.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (isCompleted) AppColors.Primary else foreground
        )
    }
}

@Composable
private fun EmptyProjectsState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = AppColors.SurfaceElevated
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("◇", style = MaterialTheme.typography.headlineLarge, color = AppColors.Primary)
            }
        }
        Text(
            "还没有项目",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextPrimary
        )
        Text(
            "点击右下角 + 添加你的第一个项目",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary
        )
    }
}

@Composable
private fun ProjectEditorDialog(
    project: ProjectDto?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember(project?.id) { mutableStateOf(project?.name.orEmpty()) }
    var description by remember(project?.id) { mutableStateOf(project?.description.orEmpty()) }
    var status by remember(project?.id) { mutableStateOf(project?.status ?: "upcoming") }
    var statusMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                if (project == null) "添加空投项目" else "编辑空投项目",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("项目名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkFieldColors()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("项目简介") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 4,
                    colors = darkFieldColors()
                )
                Text(
                    "项目状态",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextSecondary
                )
                Box {
                    Surface(
                        onClick = { statusMenuExpanded = true },
                        color = AppColors.SurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppColors.Border)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProjectStatusChip(status)
                            Spacer(Modifier.width(10.dp))
                            Text("更改状态", color = AppColors.TextSecondary)
                        }
                    }
                    DropdownMenu(
                        expanded = statusMenuExpanded,
                        onDismissRequest = { statusMenuExpanded = false },
                        containerColor = AppColors.SurfaceElevated
                    ) {
                        listOf(
                            "upcoming" to "待开始",
                            "tracking" to "进行中",
                            "completed" to "已完成"
                        ).forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(option.second, color = AppColors.TextPrimary)
                                },
                                onClick = {
                                    status = option.first
                                    statusMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = { onSave(name.trim(), description.trim(), status) }
            ) {
                Text(
                    if (saving) "保存中…" else "保存",
                    color = AppColors.Primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("取消", color = AppColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary,
    focusedContainerColor = AppColors.Background,
    unfocusedContainerColor = AppColors.Background,
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.Border,
    focusedLabelColor = AppColors.Primary,
    unfocusedLabelColor = AppColors.TextSecondary,
    cursorColor = AppColors.Primary
)
