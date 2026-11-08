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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.material.BottomSheetNavigator
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.ModalBottomSheetLayout
import com.reown.appkit.client.AppKit
import com.reown.appkit.ui.AppKitTheme
import com.reown.appkit.ui.appKitGraph
import com.reown.appkit.ui.openAppKit
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
        DetectedNetwork.BITCOIN -> WalletEcosystem.BITCOIN
        else -> null
    }

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialNavigationApi::class)
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
    var selectedEvmNetwork by remember { mutableStateOf("Ethereum") }
    var evmNetworkMenuExpanded by remember { mutableStateOf(false) }
    var selectedWallet by remember { mutableStateOf<WalletDto?>(null) }
    var walletToDelete by remember { mutableStateOf<WalletDto?>(null) }
    val clipboard = LocalClipboardManager.current
    val ecosystem = remember(address) { detectEcosystem(address.trim()) }
    val connectedAccounts by WalletConnectSessionDelegate.accounts.collectAsStateWithLifecycle()
    var pendingConnectedAccount by remember { mutableStateOf<WalletConnectAccount?>(null) }
    var connectedWalletName by remember { mutableStateOf("") }
    val walletSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    val walletSheetNavigator = remember(walletSheetState) { BottomSheetNavigator(walletSheetState) }
    val walletNavController = rememberNavController(walletSheetNavigator)

    LaunchedEffect(isGuest) {
        walletViewModel.load(isGuest)
    }

    selectedWallet?.let { wallet ->
        WalletDetailScreen(wallet, onBack = { selectedWallet = null }, onOpenBrowser = onOpenBrowser, wallets = state.wallets)
        return
    }

    AppKitTheme(mode = AppKitTheme.Mode.LIGHT) {
    ModalBottomSheetLayout(
        bottomSheetNavigator = walletSheetNavigator,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
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
                                IconButton(onClick = { clipboard.setText(AnnotatedString(wallet.address)) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "复制地址", tint = AppColors.Primary)
                                }
                                IconButton(
                                    onClick = { walletToDelete = wallet },
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

    walletToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { walletToDelete = null },
            containerColor = AppColors.Surface,
            title = { Text("确认删除钱包？") },
            text = { Text("将从钱包列表中删除“${target.wallet_name}”。此操作不会影响链上资产或区块链地址。", color = AppColors.TextSecondary) },
            confirmButton = {
                TextButton(enabled = !state.isSaving, onClick = { walletViewModel.delete(target, isGuest); walletToDelete = null }) { Text("确认删除", color = AppColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { walletToDelete = null }) { Text("取消") } }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) showAddDialog = false },
            containerColor = AppColors.Surface,
            title = { Text("添加公开钱包地址") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "推荐使用外部钱包安全连接。Airdrop Hunter 只读取钱包公开地址，不会获取私钥或助记词。",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("钱包地址") },
                        singleLine = true
                    )
                    if (ecosystem == WalletEcosystem.EVM) {
                        Box {
                            OutlinedButton(onClick = { evmNetworkMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("EVM 网络：$selectedEvmNetwork")
                            }
                            DropdownMenu(expanded = evmNetworkMenuExpanded, onDismissRequest = { evmNetworkMenuExpanded = false }) {
                                listOf("Ethereum", "BNB Chain", "Polygon", "Arbitrum", "Optimism", "Base", "Avalanche").forEach { network ->
                                    DropdownMenuItem(text = { Text(network) }, onClick = {
                                        selectedEvmNetwork = network
                                        evmNetworkMenuExpanded = false
                                    })
                                }
                            }
                        }
                    }
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
                            WalletEcosystem.SOL -> "Solana：支持 SOL 与 SPL 代币余额及可用行情估值"
                            WalletEcosystem.BITCOIN -> "Bitcoin：支持 Legacy、SegWit 地址及 BTC 余额估值"
                            else -> "支持 EVM、TRON、Solana 和 Bitcoin 公开地址"
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
                                chain = if (detectedEcosystem == WalletEcosystem.EVM) selectedEvmNetwork else detectedEcosystem,
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

    pendingConnectedAccount?.let { connected ->
        AlertDialog(
            onDismissRequest = { pendingConnectedAccount = null },
            containerColor = AppColors.Surface,
            title = { Text("外部钱包连接成功") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已从 WalletConnect 会话读取公开地址：", color = AppColors.TextSecondary)
                    Text(connected.address, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("来源：${connected.walletLabel} · EVM 链 ${connected.chainId}", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("保存只记录公开地址，不授予网站交易或签名权限。每笔交易仍需在外部钱包单独确认。", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = connectedWalletName,
                        onValueChange = { connectedWalletName = it },
                        label = { Text("钱包备注名称") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        walletViewModel.add(
                            WalletWriteRequest(
                                wallet_name = connectedWalletName.trim().ifBlank { connected.walletLabel },
                                chain = WalletEcosystem.EVM,
                                address = connected.address
                            ),
                            isGuest
                        )
                        pendingConnectedAccount = null
                    },
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) { Text("保存公开地址") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConnectedAccount = null }) { Text("暂不保存") }
            }
        )
    }

    NavHost(walletNavController, startDestination = "wallet_manager_host") {
        composable("wallet_manager_host") { }
        appKitGraph(walletNavController)
    }
    }
    }
}

internal fun shortAddress(address: String): String =
    if (address.length > 13) {
        address.take(6) + "…" + address.takeLast(4)
    } else {
        address
    }
