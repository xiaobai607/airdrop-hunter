package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.StatsDto
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ProfileScreen(modifier: Modifier = Modifier, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<StatsDto?>(null) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { try { stats = ApiClient.service.getStats() } catch (_: Exception) { error = true } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.earnings_title), style = MaterialTheme.typography.titleMedium, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        if (error) item {
            Text(stringResource(R.string.stats_unavailable), color = AppColors.Danger)
            TextButton(onClick = { scope.launch { try { stats = ApiClient.service.getStats(); error = false } catch (_: Exception) { error = true } } }) { Text(stringResource(R.string.retry)) }
        }
        val s = stats
        if (s != null) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(stringResource(R.string.actual_income), s.total_actual_income, Modifier.weight(1f), true)
                MetricCard(stringResource(R.string.expected_income), s.total_expected_income, Modifier.weight(1f))
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(stringResource(R.string.gas_total), s.total_gas_cost, Modifier.weight(1f))
                MetricCard(stringResource(R.string.net_income), s.net_realized_income, Modifier.weight(1f), true)
            } }
            item { Text("${stringResource(R.string.project_count)}: ${s.project_count}", color = AppColors.TextSecondary) }
        } else if (!error) item { CircularProgressIndicator(color = AppColors.Primary) }
        item { Button(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Surface, contentColor = AppColors.Danger)) { Text(stringResource(R.string.logout)) } }
    }
}

@Composable
private fun MetricCard(label: String, value: Double, modifier: Modifier, positive: Boolean = false) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(label, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(String.format(Locale.US, "$%,.2f", value), color = if (positive) AppColors.Success else AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("USD", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}
