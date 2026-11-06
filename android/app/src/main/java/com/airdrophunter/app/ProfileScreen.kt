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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.WalletSyncResponse
import com.airdrophunter.app.ui.theme.AppColors
import java.util.Locale

@Composable
fun ProfileScreen(modifier: Modifier = Modifier, onLogout: () -> Unit, vm: ProfileViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val networkError = stringResource(R.string.network_error)
    var publicKey by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.earnings_title), style = MaterialTheme.typography.titleMedium, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        if (state.stats == null && state.isLoading) item { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AppColors.Primary) }
        if (state.hasError && state.stats == null) item {
            Text(stringResource(R.string.stats_unavailable), color = AppColors.Danger)
            TextButton(onClick = vm::refresh) { Text(stringResource(R.string.retry)) }
        }
        state.stats?.let { stats ->
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(stringResource(R.string.actual_income), stats.total_actual_income, Modifier.weight(1f), true)
                MetricCard(stringResource(R.string.expected_income), stats.total_expected_income, Modifier.weight(1f))
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(stringResource(R.string.gas_total), stats.total_gas_cost, Modifier.weight(1f))
                MetricCard(stringResource(R.string.net_income), stats.net_realized_income, Modifier.weight(1f), true)
            } }
            item { Text("${stringResource(R.string.project_count)}: ${stats.project_count}", color = AppColors.TextSecondary) }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.wallet_sync_title), color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(publicKey, { publicKey = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wallet_address_hint)) }, singleLine = true)
                    Text(stringResource(R.string.wallet_disclaimer), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Button(enabled = publicKey.length >= 20 && !state.isSyncing, onClick = { vm.syncWallet(publicKey) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.isSyncing) stringResource(R.string.loading) else stringResource(R.string.sync_wallet))
                    }
                    state.syncResult?.let { result -> WalletSyncSummary(result) }
                    if (state.hasError) Text(networkError, color = AppColors.Danger)
                }
            }
        }
        item { Button(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Surface, contentColor = AppColors.Danger)) { Text(stringResource(R.string.logout)) } }
    }
}

@Composable
private fun WalletSyncSummary(result: WalletSyncResponse) {
    Text("${stringResource(R.string.daily_gas)}: ${String.format(Locale.US, "$%,.2f USD", result.daily_gas_usd)}", color = AppColors.TextPrimary)
    Text("${stringResource(R.string.recent_airdrop_income)}: ${String.format(Locale.US, "$%,.2f USD", result.recent_airdrop_income_usd)}", color = AppColors.Success)
    Text(result.notice, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
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
