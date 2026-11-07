package com.airdrophunter.app

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.ui.theme.AppColors
import java.util.Locale

@Composable
fun ProjectDetailScreen(project: ProjectDto, modifier: Modifier = Modifier, isGuest: Boolean = false,
    vm: ProjectDetailViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val twitterTitle = stringResource(R.string.twitter)
    var taskName by remember(project.id) { mutableStateOf("") }
    val completed = state.tasks.count { it.is_completed }
    val progress = if (state.tasks.isEmpty()) 0f else completed.toFloat() / state.tasks.size

    LaunchedEffect(project.id, isGuest) { vm.load(project.id, isGuest) }

    LazyColumn(
        modifier.fillMaxSize().background(AppColors.Background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border), shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(project.name, color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (project.description.isNotBlank()) Text(project.description,
                            color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectMetric(Modifier.weight(1f), "预期奖励", project.expected_income)
                        ProjectMetric(Modifier.weight(1f), "Gas 费用", project.gas_cost)
                        ProjectMetric(Modifier.weight(1f), "实际收益", project.actual_income)
                    }
                    project.deadline?.let { Text("${stringResource(R.string.deadline)}: $it",
                        color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (project.official_url.startsWith("https://")) OutlinedButton(onClick = {
                            context.startActivity(Intent(context, BrowserActivity::class.java)
                                .putExtra(BrowserActivity.EXTRA_URL, project.official_url)
                                .putExtra(BrowserActivity.EXTRA_TITLE, project.name))
                        }) { Text(stringResource(R.string.official_website)) }
                        if (project.twitter_url.startsWith("https://")) OutlinedButton(onClick = {
                            context.startActivity(Intent(context, BrowserActivity::class.java)
                                .putExtra(BrowserActivity.EXTRA_URL, project.twitter_url)
                                .putExtra(BrowserActivity.EXTRA_TITLE, twitterTitle))
                        }) { Text(stringResource(R.string.twitter)) }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border), shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("任务清单", color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("按步骤完成项目交互", color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Text("$completed / ${state.tasks.size}", color = AppColors.Primary,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(7.dp), color = AppColors.Primary,
                        trackColor = AppColors.Border,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    OutlinedTextField(value = taskName, onValueChange = { taskName = it },
                        modifier = Modifier.fillMaxWidth(), placeholder = { Text("添加一个待办任务…") },
                        singleLine = true, shape = RoundedCornerShape(13.dp))
                    Button(enabled = taskName.isNotBlank() && !state.isBusy,
                        onClick = { vm.addTask(project.id, taskName.trim(), isGuest); taskName = "" },
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                        Text("＋ 添加任务", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (state.isLoading && state.tasks.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        }
        items(state.tasks.sortedBy { it.step_order }, key = { it.id }) { task ->
            Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border), shape = RoundedCornerShape(15.dp),
                elevation = CardDefaults.cardElevation(0.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = AppColors.NavIndicator) {
                        Text(task.step_order.toString(), Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    }
                    Checkbox(checked = task.is_completed,
                        onCheckedChange = { checked -> vm.setCompleted(project.id, task, checked, isGuest) })
                    Text(task.task_name, Modifier.weight(1f),
                        color = if (task.is_completed) AppColors.TextHelper else AppColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.deleteTask(project.id, task, isGuest) }) {
                        Text("删除", color = AppColors.Danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectMetric(modifier: Modifier = Modifier, label: String, value: Double) {
    Column(modifier.background(Color(0xFFF1F8F5), RoundedCornerShape(14.dp)).padding(horizontal = 9.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(String.format(Locale.US, "$%.2f", value), color = Color(0xFF16845B),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
