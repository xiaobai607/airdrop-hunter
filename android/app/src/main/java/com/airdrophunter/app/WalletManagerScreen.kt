package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.AppTextField
import com.airdrophunter.app.ui.components.appPressFeedback
import com.airdrophunter.app.ui.components.AppHaptic
import com.airdrophunter.app.ui.components.AppChip
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.StaggeredListItem
import com.airdrophunter.app.ui.components.AppIconButton
import com.airdrophunter.app.ui.components.AppTextButton
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
    val addWalletSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
        sheetShape = RoundedCornerShape(topStart = AppDesignTokens.Radius.Xl.dp, topEnd = AppDesignTokens.Radius.Xl.dp)
    ) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "我的钱包",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "管理公开地址，查看链上交易记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AppButton(
                text = "添加钱包",
                onClick = {
                    address = ""
                    selectedEcosystem = WalletEcosystem.EVM
                    walletName = ""
                    walletTags = ""
                    walletNotes = ""
                    showAddDialog = true
                },
                modifier = Modifier.width(124.dp),
                style = AppButtonStyle.SECONDARY,
                enabled = !state.isSaving,
                fullWidth = false,
                heightDp = 44,
                leadingIcon = Icons.Default.Add,
                fontSizeSp = 14
            )
        }

        if (connectedAccounts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("WalletConnect 已连接", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text("已读取外部钱包公开地址；连接不代表允许交易", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                AppTextButton(onClick = {
                    AppKit.disconnect(
                        onSuccess = { WalletConnectSessionDelegate.clearSession() },
                        onError = { error ->
                            android.widget.Toast.makeText(context, "断开连接失败", android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }) { Text("断开连接", color = MaterialTheme.colorScheme.error) }
            }
        }

        state.error?.let { message ->
            Text(
                message,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error
            )
        }

        when {
            state.isLoading && state.wallets.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.wallets.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("还没有添加钱包", color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "添加公开地址后，可查看链上交易记录并导入待确认账单",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    AppButton("添加钱包", onClick = {
                        address = ""
                        selectedEcosystem = WalletEcosystem.EVM
                        walletName = ""
                        walletTags = ""
                        walletNotes = ""
                        showAddDialog = true
                    }, modifier = Modifier.widthIn(max = 220.dp))
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(state.wallets, key = { _, wallet -> wallet.id }) { index, wallet ->
                        StaggeredListItem(wallet.id, index) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .appPressFeedback(onClick = { selectedWallet = wallet }, scale = 0.96f),
                            shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp).heightIn(min = 72.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DynamicChainIcon(wallet.ecosystem, Modifier.size(40.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        wallet.wallet_name.ifBlank { "钱包" },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        shortAddress(wallet.address),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        WalletEcosystem.label(wallet.ecosystem),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (wallet.tags.orEmpty().isNotEmpty()) {
                                        Text(wallet.tags.orEmpty().joinToString(" · "), color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    if (wallet.notes.orEmpty().isNotBlank()) {
                                        Text(wallet.notes, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                            style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                    }
                                }
                                AppIconButton(
                                    icon = Icons.Default.Edit,
                                    contentDescription = "编辑钱包信息",
                                    onClick = {
                                        editWalletName = wallet.wallet_name
                                        editWalletTags = wallet.tags.orEmpty().joinToString(", ")
                                        editWalletNotes = wallet.notes.orEmpty()
                                        editingWallet = wallet
                                    },
                                    enabled = !state.isSaving
                                )
                                AppIconButton(
                                    icon = Icons.Default.DeleteOutline,
                                    contentDescription = "删除钱包",
                                    onClick = { walletViewModel.delete(wallet, isGuest) },
                                    enabled = !state.isSaving,
                                    haptic = AppHaptic.WARNING
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
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("编辑钱包信息", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(shortAddress(wallet.address), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                    AppTextField(editWalletName, { editWalletName = it }, "钱包名称")
                    AppTextField(editWalletTags, { editWalletTags = it }, "标签（逗号分隔，最多12个）")
                    AppTextField(editWalletNotes, { editWalletNotes = it }, "备注", singleLine = false)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                AppButton(
                    text = "保存修改",
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
                    enabled = editWalletName.isNotBlank() && !state.isSaving &&
                        parseWalletTags(editWalletTags).size <= 12 &&
                        parseWalletTags(editWalletTags).all { it.length <= 24 } &&
                        editWalletNotes.length <= 500,
                    loading = state.isSaving
                )
            },
            dismissButton = {
                AppTextButton(enabled = !state.isSaving, onClick = { editingWallet = null }) { Text("取消") }
            }
        )
    }

    if (showAddDialog) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { if (!state.isSaving) showAddDialog = false },
            sheetState = addWalletSheetState,
            shape = RoundedCornerShape(
                topStart = AppDesignTokens.Radius.Xl.dp,
                topEnd = AppDesignTokens.Radius.Xl.dp
            ),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.88f).imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("添加公开钱包地址", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "推荐使用外部钱包安全连接。Airdrop Hunter 只读取钱包公开地址，不会获取私钥或助记词。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                AppButton(
                    text = if (walletConnectReady) "连接外部钱包（WalletConnect）" else "钱包连接服务初始化中…",
                    onClick = {
                        showAddDialog = false
                        walletNavController.openAppKit()
                    },
                    enabled = walletConnectReady,
                    style = AppButtonStyle.SECONDARY
                )
                Text("选择地址所属链", color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        WalletEcosystem.EVM to "EVM",
                        WalletEcosystem.TRON to "TRON",
                        WalletEcosystem.SOL to "Solana",
                        WalletEcosystem.BTC to "Bitcoin"
                    ).forEach { (ecosystem, label) ->
                        AppChip(
                            text = label,
                            selected = selectedEcosystem == ecosystem,
                            onClick = { selectedEcosystem = ecosystem }
                        )
                    }
                }
                Text("先选择链，再输入对应的公开地址；系统不会自动猜测链。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                AppTextField(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = "$selectedEcosystem 地址",
                    isError = address.isNotBlank() && !addressValidation.valid
                )
                Text(
                    addressValidation.message,
                    color = if (addressValidation.valid) MaterialTheme.colorScheme.primary
                    else if (address.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                AppTextField(value = walletName, onValueChange = { walletName = it },
                    placeholder = "备注名称（可选）")
                AppTextField(value = walletTags, onValueChange = { walletTags = it },
                    placeholder = "标签（逗号分隔，最多12个）")
                AppTextField(value = walletNotes, onValueChange = { walletNotes = it },
                    placeholder = "钱包备注（可选）", singleLine = false)
                Text(
                    when (selectedEcosystem) {
                        WalletEcosystem.TRON -> "TRON：第一版支持真实余额查询"
                        WalletEcosystem.EVM -> "EVM：余额查询接入中"
                        WalletEcosystem.SOL -> "Solana：余额查询接入中"
                        WalletEcosystem.BTC -> "Bitcoin：支持只读交易记录查询"
                        else -> ""
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    if (isGuest) "地址仅保存在本地设备。" else "仅保存公开地址，不保存私钥或助记词。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                AppButton(
                    text = "保存",
                    onClick = {
                        if (!WalletAddressValidator.isValidForChain(selectedEcosystem, address)) return@AppButton
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
                    enabled = addressValidation.valid && !state.isSaving,
                    loading = state.isSaving
                )
                AppButton("取消", onClick = { showAddDialog = false },
                    style = AppButtonStyle.TEXT, enabled = !state.isSaving)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    pendingConnectedAccount?.let { connected ->
        AlertDialog(
            onDismissRequest = { pendingConnectedAccount = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("外部钱包连接成功") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已从 WalletConnect 会话读取公开地址：", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(connected.address, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("来源：${connected.walletLabel} · EVM 链 ${connected.chainId}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text("保存只记录公开地址，不授予网站交易或签名权限。每笔交易仍需在外部钱包单独确认。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    AppTextField(connectedWalletName, { connectedWalletName = it }, "钱包备注名称")
                }
            },
            confirmButton = {
                AppButton(
                    text = "保存公开地址",
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
                    loading = state.isSaving
                )
            },
            dismissButton = {
                AppTextButton(onClick = { pendingConnectedAccount = null }) { Text("暂不保存") }
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
