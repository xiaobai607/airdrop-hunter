package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class WalletManagerUiState(
    val wallets: List<WalletDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

class WalletManagerViewModel(private val repository: WalletRepository) : ViewModel() {
    private val _state = MutableStateFlow(WalletManagerUiState(isLoading = true))
    val state = _state.asStateFlow()

    fun load(isGuest: Boolean = false) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val wallets = repository.load(isGuest)
            _state.value = _state.value.copy(wallets = wallets, isLoading = false)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = "服务器繁忙，请稍后重试")
        }
    }

    fun add(request: WalletWriteRequest, isGuest: Boolean = false) = viewModelScope.launch {
        _state.value = _state.value.copy(isSaving = true, error = null)
        try {
            val wallets = repository.add(request, isGuest)
            _state.value = _state.value.copy(wallets = wallets, isSaving = false)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _state.value = _state.value.copy(isSaving = false, error = "保存失败，请检查地址或稍后重试")
        }
    }

    fun delete(wallet: WalletDto, isGuest: Boolean = false) = viewModelScope.launch {
        _state.value = _state.value.copy(isSaving = true, error = null)
        try {
            val wallets = repository.delete(wallet, isGuest, _state.value.wallets)
            _state.value = _state.value.copy(wallets = wallets, isSaving = false, error = null)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _state.value = _state.value.copy(isSaving = false, error = "删除失败，请稍后重试")
        }
    }
}

class WalletManagerViewModelFactory(private val repository: WalletRepository) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletManagerViewModel::class.java)) return WalletManagerViewModel(repository) as T
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun detectEcosystem(address: String): String? =
    when (AddressDetector.detect(address)) {
        DetectedNetwork.EVM -> WalletEcosystem.EVM
        DetectedNetwork.TRON -> WalletEcosystem.TRON
        DetectedNetwork.SOL -> WalletEcosystem.SOL
        else -> null
    }

@Composable
fun WalletManagerScreen(
    modifier: Modifier = Modifier,
    onOpenBrowser: (String, WalletDto, List<WalletDto>, String) -> Unit,
    isGuest: Boolean = false,
    vm: WalletManagerViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val walletViewModel: WalletManagerViewModel = vm ?: viewModel(factory = WalletManagerViewModelFactory(application.appContainer.walletRepository))
    val state by walletViewModel.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var walletName by remember { mutableStateOf("") }
    var selectedWallet by remember { mutableStateOf<WalletDto?>(null) }
    val ecosystem = remember(address) { detectEcosystem(address.trim()) }

    LaunchedEffect(isGuest) {
        walletViewModel.load(isGuest)
    }

    selectedWallet?.let { wallet ->
        WalletDetailScreen(wallet, onBack = { selectedWallet = null })
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "我的钱包",
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "管理地址，查看真实链上资产",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = {
                    address = ""
                    walletName = ""
                    showAddDialog = true
                },
                enabled = !state.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("添加钱包")
            }
        }

        state.error?.let { message ->
            Text(
                message,
                modifier = Modifier.padding(horizontal = 18.dp),
                color = AppColors.Danger
            )
        }

        when {
            state.isLoading && state.wallets.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
            }
            state.wallets.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "还没有添加钱包",
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "添加公开地址后，即可查询对应网络的真实资产",
                        color = AppColors.TextSecondary
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.wallets, key = { it.id }) { wallet ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedWallet = wallet },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                            border = BorderStroke(1.dp, AppColors.Border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DynamicChainIcon(wallet.ecosystem, Modifier.size(42.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        wallet.wallet_name.ifBlank { "钱包" },
                                        color = AppColors.TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        shortAddress(wallet.address),
                                        color = AppColors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        WalletEcosystem.label(wallet.ecosystem),
                                        color = AppColors.TextHelper,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                IconButton(
                                    onClick = { walletViewModel.delete(wallet, isGuest) },
                                    enabled = !state.isSaving
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "删除钱包",
                                        tint = AppColors.Danger
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) showAddDialog = false },
            containerColor = AppColors.Surface,
            title = { Text("添加公开钱包地址") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("钱包地址") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = walletName,
                        onValueChange = { walletName = it },
                        label = { Text("备注名称（可选）") },
                        singleLine = true
                    )
                    Text(
                        when (ecosystem) {
                            WalletEcosystem.TRON -> "TRON：第一版支持真实余额查询"
                            WalletEcosystem.EVM -> "EVM：余额查询接入中"
                            WalletEcosystem.SOL -> "Solana：余额查询接入中"
                            else -> "支持 EVM、TRON 和 Solana 公开地址"
                        },
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (isGuest) "地址仅保存在本地设备。" else "仅保存公开地址，不保存私钥或助记词。",
                        color = AppColors.TextHelper,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = ecosystem != null && !state.isSaving,
                    onClick = {
                        val detectedEcosystem = ecosystem ?: return@Button
                        walletViewModel.add(
                            WalletWriteRequest(
                                wallet_name = walletName.trim().ifBlank { "$detectedEcosystem Wallet" },
                                chain = detectedEcosystem,
                                address = address.trim()
                            ),
                            isGuest
                        )
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) {
                    Text(if (state.isSaving) "保存中…" else "保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    enabled = !state.isSaving
                ) {
                    Text("取消")
                }
            }
        )
    }
}

internal fun shortAddress(address: String): String =
    if (address.length > 13) {
        address.take(6) + "…" + address.takeLast(4)
    } else {
        address
    }
