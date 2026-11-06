package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.ProjectWriteRequest
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun ProjectsScreen(
    modifier: Modifier = Modifier,
    onProjectSelected: (ProjectDto) -> Unit,
    isGuest: Boolean = false,
    onRequireAuth: () -> Unit = {},
    vm: ProjectsViewModel = viewModel()
) {
    var showGuestAuthDialog by remember { mutableStateOf(false) }
    if (isGuest) {
        Box(modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("登录后即可添加和管理自己的空投项目", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Text("游客模式可以浏览行情和空投导航，云端项目需要账户同步。", color = AppColors.TextSecondary)
            }
            FloatingActionButton(onClick = { showGuestAuthDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = AppColors.Primary, contentColor = AppColors.Background) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_project))
            }
        }
        if (showGuestAuthDialog) AlertDialog(
            onDismissRequest = { showGuestAuthDialog = false }, containerColor = AppColors.Surface,
            title = { Text(stringResource(R.string.guest_auth_title), color = AppColors.TextPrimary) },
            text = { Text(stringResource(R.string.guest_auth_message), color = AppColors.TextSecondary) },
            confirmButton = { TextButton(onClick = { showGuestAuthDialog = false; onRequireAuth() }) { Text(stringResource(R.string.guest_auth_login), color = AppColors.Primary) } },
            dismissButton = { TextButton(onClick = { showGuestAuthDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
        return
    }
    val state by vm.uiState.collectAsState()
    val networkError = stringResource(R.string.network_error)
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProjectDto?>(null) }
    var deleting by remember { mutableStateOf<ProjectDto?>(null) }
    LaunchedEffect(Unit) { vm.loadProjects() }
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column {
                    Text(stringResource(R.string.projects_title), style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.projects_subtitle), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.error != null) item { Text(networkError, color = AppColors.Danger); TextButton(onClick = vm::loadProjects) { Text(stringResource(R.string.retry)) } }
            if (state.isLoading && state.projects.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) } }
            else if (state.projects.isEmpty()) item { Text(stringResource(R.string.empty_projects), color = AppColors.TextSecondary, modifier = Modifier.padding(vertical = 32.dp)) }
            items(state.projects, key = { it.id }) { p ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onProjectSelected(p) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (p.description.isNotBlank()) Text(p.description, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            IconButton(onClick = { editing = p; showEditor = true }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.save), tint = AppColors.TextSecondary) }
                            IconButton(onClick = { deleting = p }) { Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = AppColors.Danger) }
                        }
                        StatusLabel(p.status)
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editing = null; showEditor = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = AppColors.Primary, contentColor = AppColors.Background) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_project))
        }
    }
    if (showEditor) ProjectEditor(editing, state.isSaving, onDismiss = { showEditor = false }) { request ->
        val current = editing
        if (current == null) vm.createProject(request) else vm.updateProject(current, request)
        showEditor = false
    }
    deleting?.let { p ->
        AlertDialog(onDismissRequest = { deleting = null }, containerColor = AppColors.Surface,
            title = { Text(stringResource(R.string.delete_project_title), color = AppColors.TextPrimary) },
            text = { Text("${p.name} · ${stringResource(R.string.delete_project_message)}", color = AppColors.TextSecondary) },
            confirmButton = { TextButton(onClick = { vm.deleteProject(p); deleting = null }) { Text(stringResource(R.string.delete), color = AppColors.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun StatusLabel(status: String) {
    val label = when (status) { "tracking" -> stringResource(R.string.status_tracking); "completed" -> stringResource(R.string.status_completed); else -> stringResource(R.string.status_upcoming) }
    Surface(color = AppColors.NavIndicator, shape = RoundedCornerShape(7.dp), modifier = Modifier.padding(top = 10.dp)) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = AppColors.Primary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ProjectEditor(project: ProjectDto?, saving: Boolean, onDismiss: () -> Unit, onSave: (ProjectWriteRequest) -> Unit) {
    var name by remember(project?.id) { mutableStateOf(project?.name.orEmpty()) }
    var description by remember(project?.id) { mutableStateOf(project?.description.orEmpty()) }
    var status by remember(project?.id) { mutableStateOf(project?.status ?: "upcoming") }
    var officialUrl by remember(project?.id) { mutableStateOf(project?.official_url.orEmpty()) }
    var twitterUrl by remember(project?.id) { mutableStateOf(project?.twitter_url.orEmpty()) }
    var deadline by remember(project?.id) { mutableStateOf(project?.deadline.orEmpty()) }
    var expected by remember(project?.id) { mutableStateOf(project?.expected_income?.toString() ?: "0") }
    var gas by remember(project?.id) { mutableStateOf(project?.gas_cost?.toString() ?: "0") }
    var actual by remember(project?.id) { mutableStateOf(project?.actual_income?.toString() ?: "0") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = AppColors.Surface,
        title = { Text(stringResource(R.string.add_project), color = AppColors.TextPrimary) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.project_name)) }, singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.project_description)) }, minLines = 2)
            OutlinedTextField(officialUrl, { officialUrl = it }, label = { Text(stringResource(R.string.official_website)) }, singleLine = true)
            OutlinedTextField(twitterUrl, { twitterUrl = it }, label = { Text(stringResource(R.string.twitter)) }, singleLine = true)
            OutlinedTextField(deadline, { deadline = it }, label = { Text(stringResource(R.string.deadline)) }, singleLine = true)
            OutlinedTextField(expected, { expected = it }, label = { Text(stringResource(R.string.expected_reward)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(gas, { gas = it }, label = { Text(stringResource(R.string.gas_cost)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(actual, { actual = it }, label = { Text(stringResource(R.string.actual_reward)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Text(stringResource(R.string.project_status), color = AppColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("upcoming", "tracking", "completed").forEach { value ->
                    FilterChip(selected = status == value, onClick = { status = value }, label = { Text(when(value) { "tracking" -> stringResource(R.string.status_tracking); "completed" -> stringResource(R.string.status_completed); else -> stringResource(R.string.status_upcoming) }) })
                }
            }
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank() && !saving, onClick = {
            onSave(ProjectWriteRequest(
                name = name.trim(), description = description.trim(), status = status,
                official_url = officialUrl.trim(), twitter_url = twitterUrl.trim(),
                deadline = deadline.trim().ifBlank { null },
                expected_income = expected.toDoubleOrNull() ?: 0.0,
                gas_cost = gas.toDoubleOrNull() ?: 0.0,
                actual_income = actual.toDoubleOrNull() ?: 0.0
            ))
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
