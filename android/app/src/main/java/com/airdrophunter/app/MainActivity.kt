package com.airdrophunter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.ProjectDto

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AirdropHunterApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun AirdropHunterApp(projectsViewModel: ProjectsViewModel = viewModel()) {
    val uiState by projectsViewModel.uiState.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<ProjectDto?>(null) }
    var deletingProject by remember { mutableStateOf<ProjectDto?>(null) }

    LaunchedEffect(Unit) {
        projectsViewModel.loadProjects()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Airdrop Hunter") }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("你的空投管理中心", style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Text("项目数据保存在云端数据库，可新增、编辑和删除。")
            }
            item {
                Button(
                    onClick = {
                        editingProject = null
                        showEditor = true
                    },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("＋ 添加项目")
                }
            }
            if (uiState.message != null) {
                item { Text(uiState.message!!, color = MaterialTheme.colorScheme.primary) }
            }
            if (uiState.error != null) {
                item { Text("操作失败：${uiState.error}", color = MaterialTheme.colorScheme.error) }
                item { Button(onClick = projectsViewModel::loadProjects) { Text("重新加载") } }
            }
            if (uiState.isLoading && uiState.projects.isEmpty()) {
                item { CircularProgressIndicator() }
            } else if (uiState.projects.isEmpty()) {
                item { Text("还没有项目，点击上方按钮添加第一个项目。") }
            } else {
                items(items = uiState.projects, key = { it.id }) { project ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            if (project.description.isNotBlank()) {
                                Text(project.description, modifier = Modifier.padding(top = 4.dp))
                            }
                            Text(
                                "状态：${statusLabel(project.status)}",
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    enabled = !uiState.isSaving,
                                    onClick = {
                                        editingProject = project
                                        showEditor = true
                                    }
                                ) { Text("编辑") }
                                TextButton(
                                    enabled = !uiState.isSaving,
                                    onClick = { deletingProject = project }
                                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
            if (uiState.isSaving) {
                item { CircularProgressIndicator() }
            }
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
            title = { Text("删除项目") },
            text = { Text("确定删除「${project.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        projectsViewModel.deleteProject(project)
                        deletingProject = null
                    }
                ) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingProject = null }) { Text("取消") }
            }
        )
    }
}

@androidx.compose.runtime.Composable
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
        title = { Text(if (project == null) "添加空投项目" else "编辑空投项目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("项目名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("项目简介") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                Text("项目状态")
                androidx.compose.foundation.layout.Box {
                    TextButton(onClick = { statusMenuExpanded = true }) {
                        Text(statusLabel(status))
                    }
                    DropdownMenu(
                        expanded = statusMenuExpanded,
                        onDismissRequest = { statusMenuExpanded = false }
                    ) {
                        listOf(
                            "upcoming" to "待开始",
                            "tracking" to "进行中",
                            "completed" to "已完成"
                        ).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.second) },
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
            ) { Text(if (saving) "保存中…" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        }
    )
}

private fun statusLabel(status: String): String = when (status) {
    "tracking" -> "进行中"
    "completed" -> "已完成"
    else -> "待开始"
}
