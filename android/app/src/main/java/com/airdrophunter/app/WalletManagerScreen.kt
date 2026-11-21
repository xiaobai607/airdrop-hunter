package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
                // Capture the account once, read its cache first, then refresh in the background.
                val snapshot = withContext(Dispatchers.IO) { repository.loadCached(isGuest) }
                _state.value = WalletManagerUiState(
                    wallets = snapshot.wallets,
                    isLoading = snapshot.wallets.isEmpty()
                )
                val refreshed = withContext(Dispatchers.IO) { repository.refresh(snapshot, isGuest) }
                _state.value = _state.value.copy(wallets = refreshed, isLoading = false, error = null)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = if (_state.value.wallets.isEmpty()) "服务器繁忙，请稍后重试" else null
                )
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

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialNavigationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WalletManagerScreen(
    modifier: Modifier = Modifier,
    onOpenBrowser: (String, WalletDto, List<WalletDto>, String) -> Unit,
    isGuest: Boolean = false,
    sessionScopeKey: String = "guest",
    vm: WalletManagerViewModel? = null,
    launchConnectOnStart: Boolean = false,
    onConnectLaunchHandled: () -> Unit = {},
    onOpenPendingAccounting: (String?) -> Unit = {},
    onOpenLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val walletViewModel: WalletManagerViewModel = vm ?: viewModel(factory = WalletManagerViewModelFactory(application.appContainer.walletRepository))
    val state by walletViewModel.state.collectAsStateWithLifecycle()
    val walletConnectReady by WalletConnectInitializer.ready.collectAsStateWithLifecycle()
    val walletConnectError by WalletConnectInitializer.error.collectAsStateWithLifecycle()
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
    var ecosystemFilter by remember { mutableStateOf("全部") }
    val filteredWallets = remember(state.wallets, ecosystemFilter) {
        if (ecosystemFilter == "全部") state.wallets
        else state.wallets.filter { it.ecosystem == ecosystemFilter }
    }
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
    var didLaunchConnect by remember { mutableStateOf(false) }

    LaunchedEffect(walletConnectReady, launchConnectOnStart) {
        if (launchConnectOnStart && walletConnectReady && !didLaunchConnect) {
            didLaunchConnect = true
            walletNavController.openAppKit()
            onConnectLaunchHandled()
        }
    }

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
        if (launchConnectOnStart) {
            WalletConnectInitializer.ensureInitialized(application)
        }
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
        WalletDetailScreen(wallet, onBack = { selectedWallet = null }, isGuest = isGuest,
            onOpenPendingAccounting = onOpenPendingAccounting)
        return
    }

    ModalBottomSheetLayout(
        bottomSheetNavigator = walletSheetNavigator,
        sheetShape = RoundedCornerShape(topStart = AppDesignTokens.Radius.Xl.dp, topEnd = AppDesignTokens.Radius.Xl.dp)
    ) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (walletConnectError != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    walletConnectError.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { WalletConnectInitializer.ensureInitialized(application) }) {
                    Text(stringResource(R.string.wallet_ui_1))
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(AppDesignTokens.Spacing.Xxl.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.wallet_ui_2),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.wallet_ui_3),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { walletViewModel.load(isGuest) }, enabled = !state.isLoading && !state.isSaving) {
                    Text(stringResource(R.string.wallet_ui_4))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.wallet_ui_5), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Text("${state.wallets.size}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("个地址", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.wallet_ui_6), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Text("${state.wallets.map { it.ecosystem }.distinct().size}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("种网络类型", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).heightIn(min = 76.dp).appPressFeedback(onClick = {
                        address = ""
                        selectedEcosystem = WalletEcosystem.EVM
                        walletName = ""
                        walletTags = ""
                        walletNotes = ""
                        showAddDialog = true
                    }),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.wallet_ui_7), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.wallet_ui_8), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f).heightIn(min = 76.dp).appPressFeedback(onClick = {
                        if (walletConnectReady) walletNavController.openAppKit()
                        else WalletConnectInitializer.ensureInitialized(application)
                    }),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.wallet_ui_9), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.wallet_ui_10), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().appPressFeedback(onClick = onOpenLibrary),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.wallet_ui_11), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.wallet_ui_12), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(stringResource(R.string.wallet_ui_13), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (connectedAccounts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wallet_ui_14), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.wallet_ui_15), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                AppTextButton(onClick = {
                    AppKit.disconnect(
                        onSuccess = { WalletConnectSessionDelegate.clearSession() },
                        onError = { error ->
                            android.widget.Toast.makeText(context, stringResource(R.string.wallet_ui_16), android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }) { Text(stringResource(R.string.wallet_ui_17), color = MaterialTheme.colorScheme.error) }
            }
        }

        if (state.wallets.isNotEmpty()) {
            state.error?.let { message ->
                Text(
                    message,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        when {
            state.isLoading && state.wallets.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.error != null && state.wallets.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.wallet_ui_18), color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    AppButton(
                        text = stringResource(R.string.wallet_ui_19),
                        onClick = { walletViewModel.load(isGuest) },
                        modifier = Modifier.widthIn(min = 132.dp),
                        enabled = !state.isLoading && !state.isSaving
                    )
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
                    Text(stringResource(R.string.wallet_ui_20), color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.wallet_ui_21),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    AppButton(stringResource(R.string.wallet_ui_22), onClick = {
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.wallet_ui_23),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${filteredWallets.size} / ${state.wallets.size}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("全部", WalletEcosystem.EVM, WalletEcosystem.TRON, WalletEcosystem.SOL, WalletEcosystem.BTC).forEach { filter ->
                                    FilterChip(
                                        selected = ecosystemFilter == filter,
                                        onClick = { ecosystemFilter = filter },
                                        label = { Text(filter) },
                                        shape = RoundedCornerShape(50)
                                    )
                                }
                            }
                        }
                    }
                    if (filteredWallets.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.wallet_ui_24),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    itemsIndexed(filteredWallets, key = { _, wallet -> wallet.id }) { index, wallet ->
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
                                    Text(wallet.wallet_name.ifBlank { stringResource(R.string.wallet_ui_25) }, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text(shortAddress(wallet.address), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    Text(WalletEcosystem.label(wallet.ecosystem), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                                    if (wallet.tags.orEmpty().isNotEmpty()) Text(wallet.tags.orEmpty().joinToString(" · "), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                                AppIconButton(icon = Icons.Default.Edit, contentDescription = stringResource(R.string.wallet_ui_26), onClick = {
                                    editWalletName = wallet.wallet_name
                                    editWalletTags = wallet.tags.orEmpty().joinToString(", ")
                                    editWalletNotes = wallet.notes.orEmpty()
                                    editingWallet = wallet
                                }, enabled = !state.isSaving)
                                AppIconButton(icon = Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.wallet_ui_27), onClick = { walletViewModel.delete(wallet, isGuest) }, enabled = !state.isSaving, haptic = AppHaptic.WARNING)
                            }
                        }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.wallet_ui_28), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Surface(
                                modifier = Modifier.fillMaxWidth().appPressFeedback(onClick = { state.wallets.firstOrNull()?.let { selectedWallet = it } }),
                                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(stringResource(R.string.wallet_ui_29), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                        Text(stringResource(R.string.wallet_ui_30), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(stringResource(R.string.wallet_ui_31), color = MaterialTheme.colorScheme.primary)
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
            title = { Text(stringResource(R.string.wallet_ui_26), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(shortAddress(wallet.address), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                    AppTextField(editWalletName, { editWalletName = it }, stringResource(R.string.wallet_ui_32))
                    AppTextField(editWalletTags, { editWalletTags = it }, stringResource(R.string.wallet_ui_33))
                    AppTextField(editWalletNotes, { editWalletNotes = it }, stringResource(R.string.wallet_ui_34), singleLine = false)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.wallet_ui_35),
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
                AppTextButton(enabled = !state.isSaving, onClick = { editingWallet = null }) { Text(stringResource(R.string.wallet_ui_36)) }
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
                Text(stringResource(R.string.wallet_ui_37), style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.wallet_ui_38),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                AppButton(
                    text = when {
                        walletConnectReady -> stringResource(R.string.wallet_ui_39)
                        walletConnectError != null -> stringResource(R.string.wallet_ui_40)
                        else -> stringResource(R.string.wallet_ui_41)
                    },
                    onClick = {
                        if (walletConnectReady) {
                            showAddDialog = false
                            walletNavController.openAppKit()
                        } else {
                            WalletConnectInitializer.ensureInitialized(application)
                        }
                    },
                    enabled = true,
                    style = AppButtonStyle.SECONDARY
                )
                Text(stringResource(R.string.wallet_ui_42), color = MaterialTheme.colorScheme.onSurface,
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
                Text(stringResource(R.string.wallet_ui_43),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                AppTextField(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = stringResource(R.string.wallet_ui_address_hint, selectedEcosystem),
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
                    placeholder = stringResource(R.string.wallet_ui_44))
                AppTextField(value = walletTags, onValueChange = { walletTags = it },
                    placeholder = stringResource(R.string.wallet_ui_33))
                AppTextField(value = walletNotes, onValueChange = { walletNotes = it },
                    placeholder = stringResource(R.string.wallet_ui_45), singleLine = false)
                Text(
                    when (selectedEcosystem) {
                        WalletEcosystem.TRON -> stringResource(R.string.wallet_ui_46)
                        WalletEcosystem.EVM -> stringResource(R.string.wallet_ui_47)
                        WalletEcosystem.SOL -> stringResource(R.string.wallet_ui_48)
                        WalletEcosystem.BTC -> stringResource(R.string.wallet_ui_49)
                        else -> ""
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    if (isGuest) stringResource(R.string.wallet_ui_50) else stringResource(R.string.wallet_ui_51),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                AppButton(
                    text = stringResource(R.string.wallet_ui_52),
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
                AppButton(stringResource(R.string.wallet_ui_36), onClick = { showAddDialog = false },
                    style = AppButtonStyle.TEXT, enabled = !state.isSaving)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    pendingConnectedAccount?.let { connected ->
        AlertDialog(
            onDismissRequest = { pendingConnectedAccount = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.wallet_ui_53)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.wallet_ui_54), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(connected.address, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.wallet_ui_connection_source, connected.walletLabel, connected.chainId), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.wallet_ui_connection_disclaimer), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    AppTextField(connectedWalletName, { connectedWalletName = it }, stringResource(R.string.wallet_ui_55))
                }
            },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.wallet_ui_56),
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
                AppTextButton(onClick = { pendingConnectedAccount = null }) { Text(stringResource(R.string.wallet_ui_57)) }
            }
        )
    }

    // AppKit's navigation graph is relatively heavy. Compose it only after the
    // user requests an external-wallet connection and initialization has completed.
    if (walletConnectReady) {
        AppKitTheme(mode = AppKitTheme.Mode.LIGHT) {
            NavHost(walletNavController, startDestination = "wallet_manager_host") {
                composable("wallet_manager_host") { }
                appKitGraph(walletNavController)
            }
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
