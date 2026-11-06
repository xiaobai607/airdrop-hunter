package com.airdrophunter.app

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ProjectDetailScreen(project: ProjectDto, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val twitterTitle = stringResource(R.string.twitter)
    val networkError = stringResource(R.string.network_error)
    var tasks by remember(project.id) { mutableStateOf<List<TaskDto>>(emptyList()) }
    var taskName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var walletResult by remember { mutableStateOf<WalletCheckResponse?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(project.id) { try { tasks = ApiClient.service.getTasks(project.id) } catch (_: Exception) { error = true } }
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
            Text(stringResource(R.string.task_list), color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(taskName, { taskName = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.task_hint)) }, singleLine = true)
                Button(enabled = taskName.isNotBlank() && !busy, onClick = { scope.launch {
                    busy = true
                    try { val t = ApiClient.service.createTask(project.id, TaskCreateRequest(taskName.trim())); tasks = tasks + t; taskName = ""; error = false }
                    catch (_: Exception) { error = true }
                    busy = false
                } }) { Text(stringResource(R.string.add_task)) }
            }
        }
        items(tasks, key = { it.id }) { task ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = task.is_completed, onCheckedChange = { checked -> scope.launch {
                    try { val updated = ApiClient.service.updateTask(project.id, task.id, TaskUpdateRequest(checked)); tasks = tasks.map { if (it.id == updated.id) updated else it } }
                    catch (_: Exception) { error = true }
                } })
                Text(task.task_name, Modifier.weight(1f), color = AppColors.TextPrimary)
                TextButton(onClick = { scope.launch { try { ApiClient.service.deleteTask(project.id, task.id); tasks = tasks.filterNot { it.id == task.id } } catch (_: Exception) { error = true } } }) { Text(stringResource(R.string.delete), color = AppColors.Danger) }
            }
        }
        item {
            Divider(color = AppColors.Border)
            Text(stringResource(R.string.wallet_address), color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wallet_address_hint)) }, singleLine = true)
            Button(enabled = address.length >= 20 && !busy, onClick = { scope.launch {
                busy = true
                try { walletResult = ApiClient.service.checkWallet(address.trim()); error = false }
                catch (_: Exception) { error = true }
                busy = false
            } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.check_now)) }
            walletResult?.let { result ->
                Text(if (result.eligible) stringResource(R.string.wallet_eligible) else stringResource(R.string.wallet_not_eligible), color = AppColors.Primary, fontWeight = FontWeight.Bold)
                Text(String.format(Locale.US, "≈ $%.2f USD", result.estimated_reward_usd), color = AppColors.TextPrimary)
            }
            if (error) Text(networkError, color = AppColors.Danger)
        }
    }
}
