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
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onOpenSources: () -> Unit, onOpenWallet: () -> Unit, onOpenProjects: () -> Unit) {
    val scope = rememberCoroutineScope()
    val networkError = stringResource(R.string.network_error)
    var market by remember { mutableStateOf<MarketResponse?>(null) }
    var coins by remember { mutableStateOf<List<CoinDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var coinId by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var coinName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    suspend fun refresh() {
        loading = true
        try { market = ApiClient.service.getMarketPrices(); coins = ApiClient.service.getCoins(); error = null }
        catch (_: Exception) { error = networkError }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }
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
            TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading) { Text(stringResource(R.string.retry)) }
        }
        if (error != null && market == null) Text(networkError, color = AppColors.Danger)
        if (loading && market == null) Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
            items(market?.prices.orEmpty(), key = { it.coinpaprika_id }) { price ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border)) {
                    Column(Modifier.width(150.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(price.symbol, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            price.change_24h?.let { Text(String.format(Locale.US, "%.2f%%", it), color = if (it >= 0) AppColors.Success else AppColors.Danger, style = MaterialTheme.typography.labelSmall) }
                        }
                        Text(price.price_usd?.let { String.format(Locale.US, "$%,.2f", it) } ?: "—", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(price.name, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        coins.firstOrNull { it.coinpaprika_id == price.coinpaprika_id }?.let { custom ->
                            TextButton(onClick = { scope.launch { try { ApiClient.service.removeCoin(custom.id); refresh() } catch (_: Exception) { error = networkError } } }, contentPadding = PaddingValues(0.dp)) {
                                Text(stringResource(R.string.remove_coin), color = AppColors.Danger, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        if (market == null && !loading) Text(networkError, color = AppColors.TextSecondary)
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false }, containerColor = AppColors.Surface,
        title = { Text(stringResource(R.string.add_coin), color = AppColors.TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(coinId, { coinId = it }, label = { Text(stringResource(R.string.coin_id_hint)) }, singleLine = true)
            OutlinedTextField(symbol, { symbol = it }, label = { Text(stringResource(R.string.coin_symbol_hint)) }, singleLine = true)
            OutlinedTextField(coinName, { coinName = it }, label = { Text(stringResource(R.string.coin_name_hint)) }, singleLine = true)
            error?.let { Text(networkError, color = AppColors.Danger) }
        } },
        confirmButton = { TextButton(onClick = { scope.launch {
            try { ApiClient.service.addCoin(CoinCreateRequest(coinId.trim(), symbol.trim(), coinName.trim())); showAdd = false; coinId = ""; symbol = ""; coinName = ""; refresh() }
            catch (_: Exception) { error = networkError }
        } }, enabled = coinId.isNotBlank() && symbol.isNotBlank() && coinName.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancel)) } }
    )
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
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = AppColors.Surface,
        title = { Text(stringResource(R.string.wallet_title), color = AppColors.TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.wallet_address_hint)) }, singleLine = true)
            Text(stringResource(R.string.wallet_disclaimer), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            result?.let { Text(if (it.eligible) stringResource(R.string.wallet_eligible) else stringResource(R.string.wallet_not_eligible), color = AppColors.Primary, fontWeight = FontWeight.Bold); Text(String.format(Locale.US, "≈ $%.2f", it.estimated_reward_usd), color = AppColors.TextPrimary) }
            error?.let { Text(networkError, color = AppColors.Danger) }
        } },
        confirmButton = { TextButton(enabled = address.length >= 20 && !loading, onClick = { scope.launch {
            loading = true; error = null
            try { result = ApiClient.service.checkWallet(address.trim()) } catch (_: Exception) { error = networkError }
            loading = false
        } }) { Text(if (loading) stringResource(R.string.loading) else stringResource(R.string.check_now)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
