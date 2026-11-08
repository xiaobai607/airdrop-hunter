package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WalletManagerUiState(
    val wallets: List<WalletDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

class WalletManagerViewModel(private val repository: WalletRepository) : ViewModel() {
    private val _state = MutableStateFlow(WalletManagerUiState(isLoading = true))
    val state = _state.asStateFlow()

    fun load(isGuest: Boolean = true) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            _state.value = _state.value.copy(wallets = repository.load(isGuest), isLoading = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = "本地地址读取失败")
        }
    }

    fun add(request: WalletWriteRequest, isGuest: Boolean = true) = viewModelScope.launch {
        _state.value = _state.value.copy(isSaving = true, error = null)
        try {
            _state.value = _state.value.copy(wallets = repository.add(request, isGuest), isSaving = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = _state.value.copy(isSaving = false, error = "地址保存失败")
        }
    }

    fun delete(wallet: WalletDto, isGuest: Boolean = true) = viewModelScope.launch {
        _state.value = _state.value.copy(isSaving = true, error = null)
        try {
            _state.value = _state.value.copy(
                wallets = repository.delete(wallet, isGuest, _state.value.wallets),
                isSaving = false,
                error = null
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = _state.value.copy(isSaving = false, error = "地址删除失败")
        }
    }
}

class WalletManagerViewModelFactory(private val repository: WalletRepository) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletManagerViewModel::class.java)) {
            return WalletManagerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun detectEcosystem(address: String): String? =
    when (AddressDetector.detect(address.trim())) {
        DetectedNetwork.EVM -> WalletEcosystem.EVM
        DetectedNetwork.TRON -> WalletEcosystem.TRON
        DetectedNetwork.SOL -> WalletEcosystem.SOL
        DetectedNetwork.BITCOIN -> WalletEcosystem.BITCOIN
        else -> null
    }

@Composable
fun WalletManagerScreen(
    modifier: Modifier = Modifier,
    onOpenBrowser: (String, WalletDto, List<WalletDto>, String) -> Unit,
    isGuest: Boolean = true,
    vm: WalletManagerViewModel? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as AirdropHunterApplication
    val walletVm: WalletManagerViewModel = vm ?: viewModel(factory = WalletManagerViewModelFactory(app.appContainer.walletRepository))
    val state by walletVm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var showAdd by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf(WalletAddressPurpose.MAIN_INTERACTION) }
    var purposeExpanded by remember { mutableStateOf(false) }
    var selectedEvmNetwork by remember { mutableStateOf("Ethereum") }
    var networkExpanded by remember { mutableStateOf(false) }
    var walletToDelete by remember { mutableStateOf<WalletDto?>(null) }

    val ecosystem = remember(address) { detectEcosystem(address) }
    LaunchedEffect(isGuest) { walletVm.load(isGuest) }

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("空投地址管理", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("管理交互地址、用途和参与项目；只保存公开地址",
                    color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    address = ""; name = ""; note = ""; tagsText = ""
                    purpose = WalletAddressPurpose.MAIN_INTERACTION
                    selectedEvmNetwork = "Ethereum"
                    showAdd = true
                },
                enabled = !state.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添加地址")
            }
        }

        state.error?.let {
            Text(it, Modifier.padding(horizontal = 18.dp), color = AppColors.Danger)
        }

        when {
            state.isLoading && state.wallets.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AppColors.Primary) }

            state.wallets.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("还没有地址", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("添加用于空投交互的公开地址，可设置用途和标签。",
                    color = AppColors.TextSecondary)
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.wallets, key = { it.id }) { wallet ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        border = BorderStroke(1.dp, AppColors.Border)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DynamicChainIcon(wallet.ecosystem, Modifier.size(38.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(wallet.wallet_name.ifBlank { "地址" }, color = AppColors.TextPrimary,
                                        fontWeight = FontWeight.SemiBold)
                                    Text("${wallet.chain} · ${WalletAddressPurpose.label(wallet.purpose)}",
                                        color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(wallet.address))
                                    android.widget.Toast.makeText(context, "地址已复制", android.widget.Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Default.ContentCopy, "复制地址", tint = AppColors.Primary) }
                                IconButton(onClick = { walletToDelete = wallet }, enabled = !state.isSaving) {
                                    Icon(Icons.Default.DeleteOutline, "删除地址", tint = AppColors.Danger)
                                }
                            }
                            Text(wallet.address, color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall)
                            wallet.note?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
                            }
                            val tags = wallet.tags.orEmpty()
                            if (tags.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    tags.take(5).forEach { tag ->
                                        Surface(color = AppColors.NavIndicator, shape = RoundedCornerShape(7.dp)) {
                                            Text(tag, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                color = AppColors.Primary, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            val projectCount = wallet.projectIds.orEmpty().size
                            if (projectCount > 0) {
                                Text("已关联项目：$projectCount 个", color = AppColors.TextHelper,
                                    style = MaterialTheme.typography.labelSmall)
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
            title = { Text("确认删除地址？") },
            text = { Text("将从本机地址管理中删除“${target.wallet_name}”。不会影响链上地址或资产。", color = AppColors.TextSecondary) },
            confirmButton = {
                TextButton(enabled = !state.isSaving, onClick = {
                    walletVm.delete(target, isGuest)
                    walletToDelete = null
                }) { Text("确认删除", color = AppColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { walletToDelete = null }) { Text("取消") } }
        )
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) showAdd = false },
            containerColor = AppColors.Surface,
            title = { Text("添加空投地址") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("只输入公开地址。APP 不会要求助记词、私钥，也不会发起签名或交易。",
                        color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = address, onValueChange = { address = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("公开地址") }, singleLine = true)
                    if (ecosystem == WalletEcosystem.EVM) {
                        Box {
                            OutlinedButton(onClick = { networkExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("选择网络：$selectedEvmNetwork")
                            }
                            DropdownMenu(expanded = networkExpanded, onDismissRequest = { networkExpanded = false }) {
                                listOf("Ethereum", "BNB Chain", "Polygon", "Arbitrum", "Optimism", "Base", "Avalanche").forEach { network ->
                                    DropdownMenuItem(text = { Text(network) }, onClick = {
                                        selectedEvmNetwork = network
                                        networkExpanded = false
                                    })
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("地址名称") }, singleLine = true)
                    Box {
                        OutlinedButton(onClick = { purposeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("用途：${WalletAddressPurpose.label(purpose)}")
                        }
                        DropdownMenu(expanded = purposeExpanded, onDismissRequest = { purposeExpanded = false }) {
                            WalletAddressPurpose.values.forEach { value ->
                                DropdownMenuItem(text = { Text(WalletAddressPurpose.label(value)) }, onClick = {
                                    purpose = value
                                    purposeExpanded = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("备注（可选）" )}, minLines = 2)
                    OutlinedTextField(value = tagsText, onValueChange = { tagsText = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("标签（逗号分隔，可选）") }, singleLine = true)
                    Text(
                        when (ecosystem) {
                            WalletEcosystem.BITCOIN -> "已识别 Bitcoin 地址格式"
                            WalletEcosystem.EVM -> "已识别 EVM 地址；同一地址可分别登记不同网络"
                            WalletEcosystem.TRON -> "已识别 TRON 地址"
                            WalletEcosystem.SOL -> "已识别 Solana 地址"
                            else -> "支持 EVM、TRON、Solana 和 Bitcoin 主网地址"
                        },
                        color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                Button(
                    enabled = ecosystem != null && !state.isSaving,
                    onClick = {
                        val chain = if (ecosystem == WalletEcosystem.EVM) selectedEvmNetwork else ecosystem!!
                        walletVm.add(
                            WalletWriteRequest(
                                wallet_name = name.trim().ifBlank { "${WalletEcosystem.label(ecosystem!!)}地址" },
                                chain = chain,
                                address = address.trim(),
                                note = note,
                                purpose = purpose,
                                tags = tagsText.split(",", "，").map(String::trim).filter(String::isNotBlank)
                            ),
                            isGuest
                        )
                        showAdd = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) { Text(if (state.isSaving) "保存中…" else "保存地址") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }, enabled = !state.isSaving) { Text("取消") }
            }
        )
    }
}
