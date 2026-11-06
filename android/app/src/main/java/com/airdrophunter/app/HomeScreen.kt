package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CoinCreateRequest
import com.airdrophunter.app.data.WalletCheckResponse
import com.airdrophunter.app.ui.components.BaseCard
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSources: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenProjects: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val networkError = stringResource(R.string.network_error)
    var showAdd by remember { mutableStateOf(false) }
    var coinId by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var coinName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(stringResource(R.string.quick_airdrop), Icons.Default.Explore, Modifier.weight(1f), onOpenSources)
            QuickAction(stringResource(R.string.quick_wallet), Icons.Default.AccountBalanceWallet, Modifier.weight(1f), onOpenWallet)
            QuickAction(stringResource(R.string.quick_projects), Icons.Default.FolderOpen, Modifier.weight(1f), onOpenProjects)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.market_title), color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.market_provider), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.add_coin), tint = AppColors.Primary) }
            TextButton(onClick = vm::refresh, enabled = !state.isRefreshing) { Text(stringResource(R.string.retry)) }
        }
        if (state.hasError && state.market == null) Text(networkError, color = AppColors.Danger)
        if (state.market == null && state.isLoading) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(4) { SkeletonMarketCard() } }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
                items(state.market?.prices.orEmpty(), key = { it.coinpaprika_id }) { price ->
                    BaseCard(modifier = Modifier.width(150.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(price.symbol, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                price.change_24h?.let { Text(String.format(Locale.US, "%.2f%%", it), color = if (it >= 0) AppColors.Success else AppColors.Danger, style = MaterialTheme.typography.labelSmall) }
                            }
                            Text(price.price_usd?.let { String.format(Locale.US, "$%,.2f", it) } ?: "—", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(price.name, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            state.coins.firstOrNull { it.coinpaprika_id == price.coinpaprika_id }?.let { custom ->
                                TextButton(onClick = { vm.removeCoin(custom) }, contentPadding = PaddingValues(0.dp)) {
                                    Text(stringResource(R.string.remove_coin), color = AppColors.Danger, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.hasError && state.market != null) Text(networkError, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false }, containerColor = AppColors.Surface,
        title = { Text(stringResource(R.string.add_coin), color = AppColors.TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(coinId, { coinId = it }, label = { Text(stringResource(R.string.coin_id_hint)) }, singleLine = true)
            OutlinedTextField(symbol, { symbol = it }, label = { Text(stringResource(R.string.coin_symbol_hint)) }, singleLine = true)
            OutlinedTextField(coinName, { coinName = it }, label = { Text(stringResource(R.string.coin_name_hint)) }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = {
            vm.addCoin(CoinCreateRequest(coinId.trim(), symbol.trim(), coinName.trim()))
            showAdd = false; coinId = ""; symbol = ""; coinName = ""
        }, enabled = coinId.isNotBlank() && symbol.isNotBlank() && coinName.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun SkeletonMarketCard() {
    BaseCard(modifier = Modifier.width(150.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.fillMaxWidth(.45f).height(14.dp), color = AppColors.Border, shape = RoundedCornerShape(5.dp)) {}
            Surface(Modifier.fillMaxWidth().height(22.dp), color = AppColors.Border, shape = RoundedCornerShape(5.dp)) {}
            Surface(Modifier.fillMaxWidth(.7f).height(12.dp), color = AppColors.Border, shape = RoundedCornerShape(5.dp)) {}
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(94.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border)) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, tint = AppColors.Primary, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, color = AppColors.TextPrimary, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}

@Composable
fun WalletCheckDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val networkError = stringResource(R.string.network_error)
    var address by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<WalletCheckResponse?>(null) }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = AppColors.Surface,
        title = { Text(stringResource(R.string.wallet_title), color = AppColors.TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.wallet_address_hint)) }, singleLine = true)
            Text(stringResource(R.string.wallet_disclaimer), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            result?.let { Text(if (it.eligible) stringResource(R.string.wallet_eligible) else stringResource(R.string.wallet_not_eligible), color = AppColors.Primary, fontWeight = FontWeight.Bold); Text(String.format(Locale.US, "≈ $%.2f", it.estimated_reward_usd), color = AppColors.TextPrimary) }
            if (error) Text(networkError, color = AppColors.Danger)
        } },
        confirmButton = { TextButton(enabled = address.length >= 20 && !loading, onClick = { scope.launch {
            loading = true; error = false
            try { result = ApiClient.service.checkWallet(address.trim()) } catch (_: Exception) { error = true }
            loading = false
        } }) { Text(if (loading) stringResource(R.string.loading) else stringResource(R.string.check_now)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
