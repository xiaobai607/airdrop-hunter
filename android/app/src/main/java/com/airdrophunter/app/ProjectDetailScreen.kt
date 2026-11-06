package com.airdrophunter.app

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
fun ProjectDetailScreen(project: ProjectDto, modifier: Modifier = Modifier, vm: ProjectDetailViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val networkError = stringResource(R.string.network_error)
    val twitterTitle = stringResource(R.string.twitter)
    var taskName by remember(project.id) { mutableStateOf("") }
    var address by remember(project.id) { mutableStateOf("") }
    val completed = state.tasks.count { it.is_completed }
    val progress = if (state.tasks.isEmpty()) 0f else completed.toFloat() / state.tasks.size

    LaunchedEffect(project.id) { vm.load(project.id) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(project.name, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (project.description.isNotBlank()) Text(project.description, color = AppColors.TextSecondary)
                    Text("${stringResource(R.string.expected_reward)}: ${String.format(Locale.US, "$%,.2f", project.expected_income)}", color = AppColors.TextPrimary)
                    Text("${stringResource(R.string.gas_cost)}: ${String.format(Locale.US, "$%,.2f", project.gas_cost)}", color = AppColors.TextSecondary)
                    Text("${stringResource(R.string.actual_reward)}: ${String.format(Locale.US, "$%,.2f", project.actual_income)}", color = AppColors.Success)
                    project.deadline?.let { Text("${stringResource(R.string.deadline)}: $it", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (project.official_url.startsWith("https://")) OutlinedButton(onClick = { context.startActivity(Intent(context, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, project.official_url).putExtra(BrowserActivity.EXTRA_TITLE, project.name)) }) { Text(stringResource(R.string.official_website)) }
                        if (project.twitter_url.startsWith("https://")) OutlinedButton(onClick = { context.startActivity(Intent(context, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, project.twitter_url).putExtra(BrowserActivity.EXTRA_TITLE, twitterTitle)) }) { Text(stringResource(R.string.twitter)) }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.task_list), color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.task_progress, completed, state.tasks.size), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = AppColors.Primary, trackColor = AppColors.Border)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(taskName, { taskName = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.task_hint)) }, singleLine = true)
                    Button(enabled = taskName.isNotBlank() && !state.isBusy, onClick = { vm.addTask(project.id, taskName); taskName = "" }) { Text(stringResource(R.string.add_task)) }
                }
            }
        }
        if (state.isLoading && state.tasks.isEmpty()) item { CircularProgressIndicator(modifier = Modifier.size(22.dp), color = AppColors.Primary) }
        items(state.tasks.sortedBy { it.step_order }, key = { it.id }) { task ->
            Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = AppColors.NavIndicator) {
                        Text(task.step_order.toString(), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    }
                    Checkbox(checked = task.is_completed, onCheckedChange = { checked -> vm.setCompleted(project.id, task, checked) })
                    Text(task.task_name, Modifier.weight(1f), color = AppColors.TextPrimary)
                    TextButton(onClick = { vm.deleteTask(project.id, task) }) { Text(stringResource(R.string.delete), color = AppColors.Danger) }
                }
            }
        }
        item {
            Divider(color = AppColors.Border)
            Text(stringResource(R.string.wallet_address), color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wallet_address_hint)) }, singleLine = true)
            Button(enabled = address.length >= 20 && !state.isBusy, onClick = { vm.checkWallet(address) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.check_now)) }
            state.walletResult?.let { result ->
                Text(if (result.eligible) stringResource(R.string.wallet_eligible) else stringResource(R.string.wallet_not_eligible), color = AppColors.Primary, fontWeight = FontWeight.Bold)
                Text(String.format(Locale.US, "≈ $%.2f USD", result.estimated_reward_usd), color = AppColors.TextPrimary)
            }
            if (state.hasError) Text(networkError, color = AppColors.Danger)
        }
    }
}
