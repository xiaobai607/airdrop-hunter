package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
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
    var selectedWallet by remember { mutableStateOf<WalletDto?>(null) }
    val ecosystem = remember(address) { detectEcosystem(address.trim()) }
    var networkFilter by remember { mutableStateOf("全部") }
    val filterOptions = listOf("全部", "EVM", "TRON", "Solana", "Bitcoin")
    val targetEcosystem = when (networkFilter) {
        "EVM" -> WalletEcosystem.EVM
        "TRON" -> WalletEcosystem.TRON
        "Solana" -> WalletEcosystem.SOL
        "Bitcoin" -> WalletEcosystem.BITCOIN
        else -> null
    }
    val filteredWallets = remember(state.wallets, targetEcosystem) {
        state.wallets.filter { targetEcosystem == null || it.ecosystem == targetEcosystem }
    }
    val connectedAccounts by WalletConnectSessionDelegate.accounts.collectAsStateWithLifecycle()
    var pendingConnectedAccount by remember { mutableStateOf<WalletConnectAccount?>(null) }
    var connectedWalletName by remember { mutableStateOf("") }
    val walletSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    val walletSheetNavigator = remember(walletSheetState) { BottomSheetNavigator(walletSheetState) }
    val walletNavController = rememberNavController(walletSheetNavigator)

    LaunchedEffect(connectedAccounts, state.wallets) {
        val unsaved = connectedAccounts.firstOrNull { connected ->
            state.wallets.none { it.address.equals(connected.address, ignoreCase = true) }
        }
        if (unsaved != null && pendingConnectedAccount == null) {
            pendingConnectedAccount = unsaved
            connectedWalletName = unsaved.walletLabel
        }
    }

    LaunchedEffect(isGuest) {
        walletViewModel.load(isGuest)
    }

    selectedWallet?.let { wallet ->
        WalletDetailScreen(wallet, onBack = { selectedWallet = null })
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
                Text("添加地址")
            }
        }

        if (connectedAccounts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("WalletConnect 已连接", color = AppColors.Primary, fontWeight = FontWeight.SemiBold)
                    Text("已读取外部钱包公开地址；连接不代表允许交易", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = {
                    AppKit.disconnect(
                        onSuccess = { WalletConnectSessionDelegate.clearSession() },
                        onError = { error ->
                            android.widget.Toast.makeText(context, "断开连接失败", android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }) { Text("断开连接", color = AppColors.Danger) }
            }
        }


        if (state.wallets.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("空投地址库", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("仅管理公开地址，不保存助记词或私钥", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${state.wallets.size} 个地址", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "EVM 地址可跨 Ethereum、BNB Chain、Polygon、Arbitrum、Optimism、Base 等网络使用；TRON、Solana 和 Bitcoin 地址分别管理。",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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
                        "添加空投交互地址后，可查看支持网络的资产与交易记录",
                        color = AppColors.TextSecondary
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filterOptions.forEach { option ->
                                val count = when (option) {
                                    "EVM" -> state.wallets.count { it.ecosystem == WalletEcosystem.EVM }
                                    "TRON" -> state.wallets.count { it.ecosystem == WalletEcosystem.TRON }
                                    "Solana" -> state.wallets.count { it.ecosystem == WalletEcosystem.SOL }
                                    "Bitcoin" -> state.wallets.count { it.ecosystem == WalletEcosystem.BITCOIN }
                                    else -> state.wallets.size
                                }
                                FilterChip(
                                    selected = networkFilter == option,
                                    onClick = { networkFilter = option },
                                    label = { Text("$option  $count") }
                                )
                            }
                        }
                    }
                    if (filteredWallets.isEmpty()) {
                        item {
                            Text(
                                "这个网络还没有添加地址。点击右上角「添加地址」即可录入。",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    items(filteredWallets, key = { it.id }) { wallet ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                            border = BorderStroke(1.dp, AppColors.Border)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DynamicChainIcon(wallet.ecosystem, Modifier.size(42.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(
                                    Modifier.weight(1f).clickable { selectedWallet = wallet },
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        wallet.wallet_name.ifBlank { "未命名地址" },
                                        color = AppColors.TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        WalletEcosystem.label(wallet.ecosystem),
                                        color = AppColors.Primary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        shortAddress(wallet.address),
                                        color = AppColors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "查看余额与链上交易",
                                        color = AppColors.TextHelper,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    TextButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("钱包地址", wallet.address))
                                            android.widget.Toast.makeText(context, "地址已复制", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) { Text("复制") }
                                    IconButton(
                                        onClick = { walletViewModel.delete(wallet, isGuest) },
                                        enabled = !state.isSaving
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除地址", tint = AppColors.Danger)
                                    }
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
            title = { Text("添加公开地址") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "粘贴要用于空投交互的公开地址，系统会自动识别网络。你也可以稍后连接外部钱包；两种方式互不依赖。",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
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
                            WalletEcosystem.EVM -> "EVM：已接入 Ethereum 原生余额及多链交易查询"
                            WalletEcosystem.SOL -> "Solana：已接入 SOL 与 SPL / Token-2022 余额查询"
                            WalletEcosystem.BITCOIN -> "Bitcoin：真实余额与交易查询已接入"
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
                    TextButton(
                        onClick = {
                            showAddDialog = false
                            walletNavController.openAppKit()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("可选：连接外部钱包读取地址")
                    }
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
