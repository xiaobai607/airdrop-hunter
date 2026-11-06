package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.airdrophunter.app.data.WalletEcosystem
import com.airdrophunter.app.data.WalletWriteRequest
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WalletManagerUiState(val wallets: List<WalletDto> = emptyList(), val isLoading: Boolean = false, val isSaving: Boolean = false, val error: String? = null)

class WalletManagerViewModel : ViewModel() {
    private val _state = MutableStateFlow(WalletManagerUiState(isLoading = true))
    val state = _state.asStateFlow()
    private val gson = com.google.gson.Gson()
    private val walletListType = object : com.google.gson.reflect.TypeToken<List<WalletDto>>() {}.type

    fun load(isGuest: Boolean = false) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        if (isGuest) {
            val local = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.airdrophunter.app.data.CacheStore.readLocal("wallets")?.let { runCatching { gson.fromJson<List<WalletDto>>(it, walletListType) }.getOrNull() }
            }.orEmpty()
            _state.value = _state.value.copy(wallets = local, isLoading = false)
            return@launch
        }
        try {
            val wallets = ApiClient.service.getWallets()
            _state.value = _state.value.copy(wallets = wallets, isLoading = false)
            withContext(kotlinx.coroutines.Dispatchers.IO) { com.airdrophunter.app.data.CacheStore.write("wallets", gson.toJson(wallets)) }
        } catch (_: Exception) { _state.value = _state.value.copy(isLoading = false, error = "服务器繁忙，请稍后重试") }
    }

    fun add(request: WalletWriteRequest, isGuest: Boolean = false) = viewModelScope.launch {
        _state.value = _state.value.copy(isSaving = true, error = null)
        try {
            if (isGuest) {
                val wallet = WalletDto(
                    id = java.util.UUID.randomUUID().toString(), wallet_name = request.wallet_name,
                    chain = request.chain, network_icon = when (request.chain) { "EVM" -> "E"; "TRON" -> "T"; else -> "S" },
                    address = request.address
                )
                val wallets = _state.value.wallets.filterNot {
                    if (wallet.ecosystem == WalletEcosystem.EVM) it.address.equals(request.address, true)
                    else it.address == request.address
                } + wallet
                withContext(kotlinx.coroutines.Dispatchers.IO) { com.airdrophunter.app.data.CacheStore.writeLocal("wallets", gson.toJson(wallets)) }
                _state.value = _state.value.copy(wallets = wallets, isSaving = false)
            } else {
                ApiClient.service.addWallet(request)
                val wallets = ApiClient.service.getWallets()
                _state.value = _state.value.copy(wallets = wallets, isSaving = false)
                withContext(kotlinx.coroutines.Dispatchers.IO) { com.airdrophunter.app.data.CacheStore.write("wallets", gson.toJson(wallets)) }
            }
        } catch (_: Exception) { _state.value = _state.value.copy(isSaving = false, error = "保存失败，请检查地址或稍后重试") }
    }

    fun delete(wallet: WalletDto, isGuest: Boolean = false) = viewModelScope.launch {
        try {
            val wallets = _state.value.wallets.filterNot { it.id == wallet.id }
            if (isGuest) withContext(kotlinx.coroutines.Dispatchers.IO) { com.airdrophunter.app.data.CacheStore.writeLocal("wallets", gson.toJson(wallets)) }
            else {
                ApiClient.service.deleteWallet(wallet.id)
                withContext(kotlinx.coroutines.Dispatchers.IO) { com.airdrophunter.app.data.CacheStore.write("wallets", gson.toJson(wallets)) }
            }
            _state.value = _state.value.copy(wallets = wallets, error = null)
        } catch (_: Exception) { _state.value = _state.value.copy(error = "删除失败，请稍后重试") }
    }
}

private fun detectEcosystem(address: String): String? {
    val value = address.trim()
    return when {
        Regex("^0x[a-fA-F0-9]{40}$").matches(value) -> WalletEcosystem.EVM
        Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$").matches(value) -> WalletEcosystem.TRON
        Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$").matches(value) -> WalletEcosystem.SOL
        else -> null
    }
}

@Composable
fun WalletManagerScreen(modifier: Modifier = Modifier, onOpenBrowser: (String, WalletDto, List<WalletDto>, String) -> Unit, isGuest: Boolean = false, vm: WalletManagerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAddForm by remember { mutableStateOf(false) }
    var showEligibility by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var walletName by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val detected = remember(address) { detectEcosystem(address) }
    LaunchedEffect(isGuest) { vm.load(isGuest) }

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的钱包", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("按生态管理公开地址，一个地址对应一个生态", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { address = ""; walletName = ""; inputError = null; showAddForm = true }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
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
                Text("添加 0x、TRON 或 Solana 公开地址，即可按生态统一管理", color = AppColors.TextSecondary)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.wallets, key = { it.id }) { wallet ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        border = BorderStroke(1.dp, AppColors.Border), elevation = CardDefaults.cardElevation(0.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            DynamicChainIcon(wallet.ecosystem, Modifier.size(42.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(shortAddress(wallet.address), color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(WalletEcosystem.label(wallet.ecosystem), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                if (wallet.ecosystem == WalletEcosystem.EVM) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        listOf("Ethereum", "BNB Chain", "Arbitrum").forEach { chain ->
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                DynamicChainIcon(chain, Modifier.size(15.dp))
                                                Text(when (chain) { "Ethereum" -> "ETH"; "BNB Chain" -> "BSC"; else -> "ARB" },
                                                    color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                } else {
                                    val chainName = if (wallet.ecosystem == WalletEcosystem.TRON) "TRON" else "SOL"
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        DynamicChainIcon(chainName, Modifier.size(16.dp))
                                        Text(chainName, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            IconButton(onClick = { vm.delete(wallet, isGuest) }) { Icon(Icons.Default.DeleteOutline, "删除钱包", tint = AppColors.Danger) }
                        }
                    }
                }
            }
        }
    }

    if (showAddForm) AlertDialog(
        onDismissRequest = { showAddForm = false }, containerColor = AppColors.Surface,
        title = { Text("添加公开钱包地址", color = AppColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(address, { address = it; inputError = null }, label = { Text("粘贴钱包地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(walletName, { walletName = it }, label = { Text("备注名称（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                when (detected) {
                    WalletEcosystem.EVM -> Text("识别为 EVM：同一地址通用于 Ethereum、BSC、Arbitrum 等 EVM 链。", color = AppColors.Primary, style = MaterialTheme.typography.bodySmall)
                    WalletEcosystem.TRON -> Text("识别为 TRON 生态地址。", color = AppColors.Primary, style = MaterialTheme.typography.bodySmall)
                    WalletEcosystem.SOL -> Text("识别为 Solana 生态地址。", color = AppColors.Primary, style = MaterialTheme.typography.bodySmall)
                    else -> Text("支持 0x EVM 地址、T 开头 TRON 地址及 Solana 地址。", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text(if (isGuest) "地址仅保存在本地设备，不会发送到服务器。" else "仅保存公开地址，不保存私钥或助记词。", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                inputError?.let { Text(it, color = AppColors.Danger) }
            }
        },
        confirmButton = {
            Button(enabled = detected != null && !state.isSaving, onClick = {
                val ecosystem = detected ?: return@Button
                val name = walletName.trim().ifBlank { "$ecosystem Wallet" }
                vm.add(WalletWriteRequest(name, ecosystem, address.trim()), isGuest)
                showAddForm = false
            }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) { Text(if (state.isSaving) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(onClick = { showAddForm = false }) { Text("取消") } }
    )
    if (showEligibility) WalletCheckDialog(onDismiss = { showEligibility = false }, isGuest = isGuest, onOpenBrowser = { url, selectedWallet, allWallets, title ->
        showEligibility = false
        onOpenBrowser(url, selectedWallet, allWallets, title)
    })
}

internal fun shortAddress(address: String): String = if (address.length > 13) address.take(6) + "…" + address.takeLast(4) else address
