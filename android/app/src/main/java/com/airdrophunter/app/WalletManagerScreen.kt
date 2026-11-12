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
import androidx.compose.material.icons.filled.Edit
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
import kotlinx.coroutines.Job
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
    private var activeJob: Job? = null

    fun load(isGuest: Boolean = false) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
        _state.value = WalletManagerUiState(isLoading = true)
        try {
            val wallets = repository.load(isGuest)
            _state.value = _state.value.copy(wallets = wallets, isLoading = false)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = "服务器繁忙，请稍后重试")
        }
        }
    }

    fun add(request: WalletWriteRequest, isGuest: Boolean = false) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
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
    }

    fun update(wallet: WalletDto, request: WalletWriteRequest, isGuest: Boolean = false) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
        _state.value = _state.value.copy(isSaving = true, error = null)
        try {
            val wallets = repository.update(wallet, request, isGuest, _state.value.wallets)
            _state.value = _state.value.copy(wallets = wallets, isSaving = false, error = null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.value = _state.value.copy(isSaving = false, error = error.message ?: "更新钱包信息失败")
        }
        }
    }

    fun delete(wallet: WalletDto, isGuest: Boolean = false) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
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
}

class WalletManagerViewModelFactory(private val repository: WalletRepository) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletManagerViewModel::class.java)) return WalletManagerViewModel(repository) as T
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialNavigationApi::class)
@Composable
fun WalletManagerScreen(
    modifier: Modifier = Modifier,
    onOpenBrowser: (String, WalletDto, List<WalletDto>, String) -> Unit,
    isGuest: Boolean = false,
    sessionScopeKey: String = "guest",
    vm: WalletManagerViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val walletViewModel: WalletManagerViewModel = vm ?: viewModel(factory = WalletManagerViewModelFactory(application.appContainer.walletRepository))
    val state by walletViewModel.state.collectAsStateWithLifecycle()
    val walletConnectReady by WalletConnectInitializer.ready.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var selectedEcosystem by remember { mutableStateOf(WalletEcosystem.EVM) }
    var walletName by remember { mutableStateOf("") }
    var walletTags by remember { mutableStateOf("") }
    var walletNotes by remember { mutableStateOf("") }
    var editingWallet by remember { mutableStateOf<WalletDto?>(null) }
    var editWalletName by remember { mutableStateOf("") }
    var editWalletTags by remember { mutableStateOf("") }
    var editWalletNotes by remember { mutableStateOf("") }
    var selectedWallet by remember { mutableStateOf<WalletDto?>(null) }
    val addressValidation = remember(selectedEcosystem, address) { WalletAddressValidator.validate(selectedEcosystem, address) }
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

    LaunchedEffect(isGuest, sessionScopeKey) {
        WalletConnectInitializer.ensureInitialized(application)
        selectedWallet = null
        editingWallet = null
        pendingConnectedAccount = null
        showAddDialog = false
        walletName = ""
        walletTags = ""
        walletNotes = ""
        editWalletName = ""
        editWalletTags = ""
        editWalletNotes = ""
        walletViewModel.load(isGuest)
    }

    selectedWallet?.let { wallet ->
        WalletDetailScreen(wallet, onBack = { selectedWallet = null }, isGuest = isGuest)
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
                    "管理公开地址，查看链上交易记录",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = {
                    address = ""
                    selectedEcosystem = WalletEcosystem.EVM
                    walletName = ""
                    walletTags = ""
                    walletNotes = ""
                    showAddDialog = true
                },
                enabled = !state.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("添加钱包")
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
                        "添加公开地址后，可查看链上交易记录并导入待确认账单",
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
                                    if (wallet.tags.orEmpty().isNotEmpty()) {
                                        Text(wallet.tags.orEmpty().joinToString(" · "), color = AppColors.Primary,
                                            style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    if (wallet.notes.orEmpty().isNotBlank()) {
                                        Text(wallet.notes, color = AppColors.TextHelper,
                                            style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        editWalletName = wallet.wallet_name
                                        editWalletTags = wallet.tags.orEmpty().joinToString(", ")
                                        editWalletNotes = wallet.notes.orEmpty()
                                        editingWallet = wallet
                                    },
                                    enabled = !state.isSaving
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑钱包信息", tint = AppColors.Primary)
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

    editingWallet?.let { wallet ->
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) editingWallet = null },
            containerColor = AppColors.Surface,
            title = { Text("编辑钱包信息", color = AppColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(shortAddress(wallet.address), color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = editWalletName,
                        onValueChange = { editWalletName = it },
                        label = { Text("钱包名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editWalletTags,
                        onValueChange = { editWalletTags = it },
                        label = { Text("标签（逗号分隔，最多12个）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editWalletNotes,
                        onValueChange = { editWalletNotes = it },
                        label = { Text("备注") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.error?.let { Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    enabled = editWalletName.isNotBlank() && !state.isSaving &&
                        parseWalletTags(editWalletTags).size <= 12 &&
                        parseWalletTags(editWalletTags).all { it.length <= 24 } &&
                        editWalletNotes.length <= 500,
                    onClick = {
                        walletViewModel.update(
                            wallet,
                            WalletWriteRequest(
                                wallet_name = editWalletName.trim(),
                                chain = wallet.chain,
                                address = wallet.address,
                                tags = parseWalletTags(editWalletTags),
                                notes = editWalletNotes.trim()
                            ),
                            isGuest
                        )
                        editingWallet = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) { Text("保存修改") }
            },
            dismissButton = {
                TextButton(enabled = !state.isSaving, onClick = { editingWallet = null }) { Text("取消") }
            }
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
                    OutlinedButton(
                        enabled = walletConnectReady,
                        onClick = {
                            showAddDialog = false
                            walletNavController.openAppKit()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (walletConnectReady) "连接外部钱包（WalletConnect）" else "钱包连接服务初始化中…")
                    }
                    Text("选择地址所属链", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedEcosystem == WalletEcosystem.EVM, onClick = { selectedEcosystem = WalletEcosystem.EVM }, label = { Text("EVM") })
                        FilterChip(selected = selectedEcosystem == WalletEcosystem.TRON, onClick = { selectedEcosystem = WalletEcosystem.TRON }, label = { Text("TRON") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedEcosystem == WalletEcosystem.SOL, onClick = { selectedEcosystem = WalletEcosystem.SOL }, label = { Text("Solana") })
                        FilterChip(selected = selectedEcosystem == WalletEcosystem.BTC, onClick = { selectedEcosystem = WalletEcosystem.BTC }, label = { Text("Bitcoin") })
                    }
                    Text("先选择链，再输入对应的公开地址；系统不会自动猜测链。", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("$selectedEcosystem 地址") },
                        singleLine = true
                    )
                    Text(
                        addressValidation.message,
                        color = if (addressValidation.valid) AppColors.Primary else if (address.isBlank()) AppColors.TextHelper else AppColors.Danger,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = walletName,
                        onValueChange = { walletName = it },
                        label = { Text("备注名称（可选）") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = walletTags,
                        onValueChange = { walletTags = it },
                        label = { Text("标签（逗号分隔，最多12个）") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = walletNotes,
                        onValueChange = { walletNotes = it },
                        label = { Text("钱包备注（可选）") },
                        minLines = 2,
                        maxLines = 3
                    )
                    Text(
                        when (selectedEcosystem) {
                            WalletEcosystem.TRON -> "TRON：第一版支持真实余额查询"
                            WalletEcosystem.EVM -> "EVM：余额查询接入中"
                            WalletEcosystem.SOL -> "Solana：余额查询接入中"
                            WalletEcosystem.BTC -> "Bitcoin：支持只读交易记录查询"
                            else -> ""
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
                    enabled = addressValidation.valid && !state.isSaving,
                    onClick = {
                        if (!WalletAddressValidator.isValidForChain(selectedEcosystem, address)) return@Button
                        walletViewModel.add(
                            WalletWriteRequest(
                                wallet_name = walletName.trim().ifBlank { "$selectedEcosystem Wallet" },
                                chain = selectedEcosystem,
                                address = address.trim(),
                                tags = parseWalletTags(walletTags),
                                notes = walletNotes.trim()
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

private fun parseWalletTags(raw: String): List<String> =
    raw.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(12)

internal fun shortAddress(address: String): String =
    if (address.length > 13) {
        address.take(6) + "…" + address.takeLast(4)
    } else {
        address
    }
