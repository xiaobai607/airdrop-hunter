package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.data.WalletWriteRequest
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private data class WalletNetwork(val key: String, val label: String, val symbol: String)
private val walletNetworks = listOf(
    WalletNetwork("Ethereum", "Ethereum · ERC20", "E"),
    WalletNetwork("Tron", "Tron · TRC20", "T"),
    WalletNetwork("Solana", "Solana", "S"),
    WalletNetwork("Bitcoin", "Bitcoin", "B")
)

data class WalletManagerUiState(val wallets: List<WalletDto> = emptyList(), val isLoading: Boolean = false, val isSaving: Boolean = false, val error: String? = null)

class WalletManagerViewModel : ViewModel() {
    private val _state = MutableStateFlow(WalletManagerUiState(isLoading = true))
    val state = _state.asStateFlow()
    fun load() = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try { _state.value = _state.value.copy(wallets = ApiClient.service.getWallets(), isLoading = false) }
        catch (_: Exception) { _state.value = _state.value.copy(isLoading = false, error = "服务器繁忙，请稍后重试") }
    }
    fun add(request: WalletWriteRequest) = viewModelScope.launch {
        _state.value = _state.value.copy(isSaving = true, error = null)
        try {
            ApiClient.service.addWallet(request)
            _state.value = _state.value.copy(wallets = ApiClient.service.getWallets(), isSaving = false)
        } catch (_: Exception) { _state.value = _state.value.copy(isSaving = false, error = "地址格式与所选网络不匹配，或服务器繁忙") }
    }
    fun delete(wallet: WalletDto) = viewModelScope.launch {
        try {
            ApiClient.service.deleteWallet(wallet.id)
            _state.value = _state.value.copy(wallets = _state.value.wallets.filterNot { it.id == wallet.id })
        } catch (_: Exception) { _state.value = _state.value.copy(error = "删除失败，请稍后重试") }
    }
}

@Composable
fun WalletManagerScreen(modifier: Modifier = Modifier, onOpenBrowser: (String, WalletDto, List<WalletDto>, String) -> Unit, vm: WalletManagerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedNetwork by remember { mutableStateOf<WalletNetwork?>(null) }
    var showNetworkPicker by remember { mutableStateOf(false) }
    var showAddForm by remember { mutableStateOf(false) }
    var showEligibility by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load() }

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的钱包", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("仅管理公开地址，不保存私钥或助记词", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { showNetworkPicker = true }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("添加钱包")
            }
        }
        OutlinedButton(onClick = { showEligibility = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("选择项目和钱包，查询空投资格")
        }
        state.error?.let { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 4.dp), color = AppColors.Danger) }
        when {
            state.isLoading && state.wallets.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
            state.wallets.isEmpty() -> Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("还没有添加钱包", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("添加常用网络的公开地址，方便后续统一查询", color = AppColors.TextSecondary)
                state.error?.let { Text(it, color = AppColors.Danger) }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.wallets, key = { it.id }) { wallet ->
                    val network = walletNetworks.firstOrNull { it.key == wallet.chain || it.symbol == wallet.network_icon }
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        border = BorderStroke(1.dp, AppColors.Border), elevation = CardDefaults.cardElevation(0.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChainIcon(wallet.chain, wallet.network_icon, Modifier.size(44.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(wallet.chain, color = AppColors.Primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(wallet.wallet_name, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Text(shortAddress(wallet.address), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.delete(wallet) }) { Icon(Icons.Default.DeleteOutline, "删除钱包", tint = AppColors.Danger) }
                        }
                    }
                }
            }
        }
    }

    if (showNetworkPicker) AlertDialog(onDismissRequest = { showNetworkPicker = false }, containerColor = AppColors.Surface,
        title = { Text("选择网络 / 链", color = AppColors.TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            walletNetworks.forEach { network ->
                Surface(onClick = { selectedNetwork = network; showNetworkPicker = false; showAddForm = true },
                    color = AppColors.Surface, border = BorderStroke(1.dp, AppColors.Border), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChainIcon(network.key, network.symbol, Modifier.size(40.dp)); Spacer(Modifier.width(12.dp))
                        Text(network.label, color = AppColors.TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = { showNetworkPicker = false }) { Text("取消") } })

    if (showAddForm && selectedNetwork != null) {
        var walletName by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAddForm = false }, containerColor = AppColors.Surface,
            title = { Text("添加 ${selectedNetwork!!.label} 钱包", color = AppColors.TextPrimary) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(walletName, { walletName = it }, label = { Text("自定义名称，例如：主号") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("公开钱包地址") }, singleLine = true)
                Text("地址长度超过 20 位即可提交；网络格式由服务端校验。", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = AppColors.Danger) }
            } },
            confirmButton = { Button(enabled = address.trim().length > 20 && walletName.isNotBlank() && !state.isSaving,
                onClick = { vm.add(WalletWriteRequest(walletName.trim(), selectedNetwork!!.key, address.trim())); showAddForm = false },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) { Text(if (state.isSaving) "保存中…" else "保存") } },
            dismissButton = { TextButton(onClick = { showAddForm = false }) { Text("取消") } })
    }
    if (showEligibility) WalletCheckDialog(onDismiss = { showEligibility = false }, onOpenBrowser = { url, selectedWallet, allWallets, title ->
        showEligibility = false
        onOpenBrowser(url, selectedWallet, allWallets, title)
    })
}

internal fun shortAddress(address: String): String = if (address.length > 13) address.take(6) + "…" + address.takeLast(5) else address
