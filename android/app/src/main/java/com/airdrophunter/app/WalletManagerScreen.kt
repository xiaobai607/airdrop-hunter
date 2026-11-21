package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import android.content.Context
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
            _state.value = _state.value.copy(isLoading = false, error = "wallet_error_server_busy")
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
            _state.value = _state.value.copy(isSaving = false, error = "wallet_error_save")
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
            _state.value = _state.value.copy(isSaving = false, error = "wallet_error_update")
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
            _state.value = _state.value.copy(isSaving = false, error = "wallet_error_delete")
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
    onConnectLaunchHandled: () -> Unit = {}
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
                    Text(context.getString(R.string.common_retry))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    context.getString(R.string.wallet_manager_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    context.getString(R.string.wallet_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AppButton(
                text = context.getString(R.string.wallet_add),
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
                    Text(context.getString(R.string.wallet_connected), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(context.getString(R.string.wallet_connected_readonly), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                AppTextButton(onClick = {
                    AppKit.disconnect(
                        onSuccess = { WalletConnectSessionDelegate.clearSession() },
                        onError = { error ->
                            android.widget.Toast.makeText(context, context.getString(R.string.wallet_disconnect_failed), android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }) { Text(context.getString(R.string.wallet_disconnect), color = MaterialTheme.colorScheme.error) }
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
                    Text(context.getString(R.string.wallet_list_error), color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(state.error?.let { localizedWalletError(context, it) }.orEmpty(), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    AppButton(
                        text = context.getString(R.string.common_retry),
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
                    Text(context.getString(R.string.wallet_empty), color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        context.getString(R.string.wallet_empty_help),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    AppButton(context.getString(R.string.wallet_add), onClick = {
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
                                        wallet.wallet_name.ifBlank { context.getString(R.string.wallet_default_name) },
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
                                }
                                AppIconButton(
                                    icon = Icons.Default.Edit,
                                    contentDescription = context.getString(R.string.wallet_edit),
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
                                    contentDescription = context.getString(R.string.wallet_delete),
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
            title = { Text(context.getString(R.string.wallet_edit), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(shortAddress(wallet.address), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                    AppTextField(editWalletName, { editWalletName = it }, context.getString(R.string.wallet_name))
                    AppTextField(editWalletTags, { editWalletTags = it }, context.getString(R.string.wallet_tags_hint))
                    AppTextField(editWalletNotes, { editWalletNotes = it }, context.getString(R.string.wallet_notes), singleLine = false)
                    state.error?.let { Text(localizedWalletError(context, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                AppButton(
                    text = context.getString(R.string.wallet_save_changes),
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
                AppTextButton(enabled = !state.isSaving, onClick = { editingWallet = null }) { Text(context.getString(R.string.cancel)) }
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
                Text(context.getString(R.string.wallet_add_public), style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    context.getString(R.string.wallet_external_connect_safety),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                AppButton(
                    text = when {
                        walletConnectReady -> context.getString(R.string.wallet_connect_button)
                        walletConnectError != null -> context.getString(R.string.wallet_connect_retry)
                        else -> context.getString(R.string.wallet_connect_initializing)
                    },
                    onClick = {
                        if (walletConnectReady) {
                            showAddDialog = false
                            walletNavController.openAppKit()
                        } else {
                            WalletConnectInitializer.ensureInitialized(application)
                        }
                    },
                    enabled = walletConnectReady || walletConnectError != null,
                    style = AppButtonStyle.SECONDARY
                )
                Text(context.getString(R.string.wallet_choose_chain), color = MaterialTheme.colorScheme.onSurface,
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
                Text(context.getString(R.string.wallet_chain_help),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                AppTextField(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = context.getString(R.string.wallet_address_hint_format, selectedEcosystem),
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
                    placeholder = context.getString(R.string.wallet_optional_name))
                AppTextField(value = walletTags, onValueChange = { walletTags = it },
                    placeholder = context.getString(R.string.wallet_tags_hint))
                AppTextField(value = walletNotes, onValueChange = { walletNotes = it },
                    placeholder = context.getString(R.string.wallet_optional_note), singleLine = false)
                Text(
                    when (selectedEcosystem) {
                        WalletEcosystem.TRON -> context.getString(R.string.wallet_chain_tron)
                        WalletEcosystem.EVM -> context.getString(R.string.wallet_chain_evm)
                        WalletEcosystem.SOL -> context.getString(R.string.wallet_chain_solana)
                        WalletEcosystem.BTC -> context.getString(R.string.wallet_chain_bitcoin)
                        else -> ""
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    if (isGuest) context.getString(R.string.wallet_address_local) else context.getString(R.string.wallet_public_only),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                AppButton(
                    text = context.getString(R.string.common_save),
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
                AppButton(context.getString(R.string.cancel), onClick = { showAddDialog = false },
                    style = AppButtonStyle.TEXT, enabled = !state.isSaving)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    pendingConnectedAccount?.let { connected ->
        AlertDialog(
            onDismissRequest = { pendingConnectedAccount = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(context.getString(R.string.wallet_connect_success)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.wallet_connected_address_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(connected.address, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(context.getString(R.string.wallet_connection_source, connected.walletLabel, connected.chainId), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(context.getString(R.string.wallet_signing_boundary), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    AppTextField(connectedWalletName, { connectedWalletName = it }, context.getString(R.string.wallet_note_name))
                }
            },
            confirmButton = {
                AppButton(
                    text = context.getString(R.string.wallet_save_public_address),
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
                AppTextButton(onClick = { pendingConnectedAccount = null }) { Text(context.getString(R.string.common_not_now)) }
            }
        )
    }

    AppKitTheme(mode = AppKitTheme.Mode.LIGHT) {
        NavHost(walletNavController, startDestination = "wallet_manager_host") {
            composable("wallet_manager_host") { }
            appKitGraph(walletNavController)
        }
    }
    }
}

private fun localizedWalletError(context: Context, code: String): String = when (code) {
    "wallet_error_server_busy" -> context.getString(R.string.wallet_error_server_busy)
    "wallet_error_save" -> context.getString(R.string.wallet_error_save)
    "wallet_error_update" -> context.getString(R.string.wallet_error_update)
    "wallet_error_delete" -> context.getString(R.string.wallet_error_delete)
    "wallet_error_catalog_duplicate" -> context.getString(R.string.wallet_catalog_duplicate)
    else -> when {
        code.startsWith("wallet_error_catalog_unavailable:") ->
            context.getString(R.string.wallet_catalog_unavailable, code.substringAfter(":"))
        code.startsWith("wallet_error_catalog_incomplete:") -> {
            val counts = code.substringAfter(":").split(":")
            val loaded = counts.getOrNull(0)?.toIntOrNull() ?: 0
            val expected = counts.getOrNull(1)?.toIntOrNull() ?: 0
            context.getString(R.string.wallet_catalog_incomplete, loaded, expected)
        }
        else -> code
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
