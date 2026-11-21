package com.airdrophunter.app

import android.widget.Toast
import com.google.gson.Gson
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.appPressFeedback
import com.airdrophunter.app.ui.components.AppHaptic
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppIconButton
import com.airdrophunter.app.ui.components.AppChip
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AppTextField
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.time.Instant

internal data class AccountingFormDraft(
    val selectedProjectLocalId: String? = null,
    val gas: String? = null, val reward: String? = null, val rewardUsdEdited: Boolean? = null,
    val gasNative: String? = null, val gasSymbol: String? = null, val gasDecimals: String? = null,
    val gasValuationTimestamp: String? = null, val gasValuationSource: String? = null,
    val gasL1Native: String? = null, val gasL2Native: String? = null, val gasOperatorNative: String? = null,
    val gasL1Usd: Double? = null, val gasL2Usd: Double? = null, val gasOperatorUsd: Double? = null,
    val gasSplitQuoteTimestamp: String? = null, val gasExecutionLabel: String? = null, val gasSplitSymbol: String? = null,
    val rewardNative: String? = null, val rewardSymbol: String? = null, val rewardDecimals: String? = null
)
internal object AccountingFormDraftCodec {
    private val gson = Gson()
    fun encode(draft: AccountingFormDraft): String = gson.toJson(draft).takeIf { it.length <= 32_768 } ?: "{}"
    fun decode(json: String?): AccountingFormDraft? {
        if (json.isNullOrBlank() || json.length > 32_768) return null
        return runCatching { gson.fromJson(json, AccountingFormDraft::class.java) }.getOrNull()
    }
}

@Composable
fun BrowserAccountingToolbar(
    walletAddress: String,
    chainName: String,
    onCopyAddress: () -> Unit,
    onRefreshPage: () -> Unit,
    onCopyPageUrl: () -> Unit,
    onOpenPageExternally: () -> Unit,
    modifier: Modifier = Modifier,
    initialShowBillList: Boolean = false,
    showDock: Boolean = true,
    onCloseAccounting: () -> Unit = {},
    showWalletTools: Boolean = true,
    walletAddressFilter: String? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val accountingRepository = remember(application) { application.appContainer.accountingRepository }
    val web3Repository = remember(application) { application.appContainer.web3TransactionRepository }
    val pending by accountingRepository.observePendingBills().collectAsStateWithLifecycle(initialValue = emptyList())
    val web3Drafts by web3Repository.observeDrafts().collectAsStateWithLifecycle(initialValue = emptyList())
    val visibleWeb3Drafts = remember(web3Drafts) {
        web3Drafts.filter { it.status !in setOf(Web3DraftStatus.CANCELLED, Web3DraftStatus.ACCOUNTED) }
    }

    // Once a receipt is authoritative, materialize its stable WEB3-* pending bill
    // automatically. The repository checks the deterministic ID before insertion,
    // so recomposition/re-observation does not create another ledger candidate.
    LaunchedEffect(web3Drafts) {
        web3Drafts.asSequence()
            .filter {
                it.status == Web3DraftStatus.CONFIRMED ||
                    it.status == Web3DraftStatus.CONFIRMED_FAILED
            }
            .forEach { draft ->
                runCatching { web3Repository.createPendingAccountingBill(draft.id) }
            }
    }

    // Legacy MOCK fixtures stay stored for compatibility but never enter the real ledger flow.
    val realPending = remember(pending, walletAddressFilter) {
        pending.filterNot { it.txHash.startsWith("MOCK-") }
            .filter { walletAddressFilter.isNullOrBlank() || it.walletAddress.equals(walletAddressFilter, ignoreCase = true) }
    }

    var projects by remember { mutableStateOf<List<ProjectDto>>(emptyList()) }
    var activeBill by remember { mutableStateOf<PendingBillEntity?>(null) }
    var showBillList by remember { mutableStateOf(initialShowBillList) }
    var showConfirm by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var selectedProjectLocalId by remember { mutableStateOf("") }
    var gas by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("") }
    var rewardUsdEdited by remember { mutableStateOf(false) }
    var gasNative by remember { mutableStateOf("") }
    var gasSymbol by remember { mutableStateOf("") }
    var gasDecimals by remember { mutableStateOf("") }
    var gasValuationTimestamp by remember { mutableStateOf<String?>(null) }
    var gasValuationSource by remember { mutableStateOf<String?>(null) }
    var gasL1Native by remember { mutableStateOf("") }
    var gasL2Native by remember { mutableStateOf("") }
    var gasOperatorNative by remember { mutableStateOf("") }
    var gasL1Usd by remember { mutableStateOf<Double?>(null) }
    var gasL2Usd by remember { mutableStateOf<Double?>(null) }
    var gasOperatorUsd by remember { mutableStateOf<Double?>(null) }
    var gasSplitQuoteTimestamp by remember { mutableStateOf<String?>(null) }
    var gasExecutionLabel by remember { mutableStateOf("执行费") }
    var gasSplitSymbol by remember { mutableStateOf("") }
    var rewardNative by remember { mutableStateOf("") }
    var rewardSymbol by remember { mutableStateOf("") }
    var rewardDecimals by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var showToolManager by remember { mutableStateOf(false) }
    var showWeb3HashSync by remember { mutableStateOf(false) }
    var web3HashSyncDraftId by remember { mutableStateOf<String?>(null) }
    var web3HashSyncInput by remember { mutableStateOf("") }
    // The X5 surface is browsing-only: it must not expose wallet-specific actions.
    // Keep separate preferences so its reduced tool set cannot overwrite the legacy
    // wallet browser's dock configuration.
    val toolOptions = listOfNotNull(
        if (showWalletTools) "copy_address" to "复制地址" else null,
        "pending_bills" to stringResource(R.string.acct_ui_97),
        "refresh_page" to "刷新网页",
        "copy_url" to "复制网址",
        "open_external" to "外部打开"
    )
    val preferences = remember(context, showWalletTools) {
        context.getSharedPreferences(
            if (showWalletTools) "browser_dock_preferences" else "x5_browser_dock_preferences",
            android.content.Context.MODE_PRIVATE
        )
    }
    var selectedToolIds by remember {
        mutableStateOf(preferences.getString("selected_tools", null)?.split(",")
            ?.map { if (it == "mock_bill") "new_accounting" else it }
            ?.filter { id -> toolOptions.any { it.first == id } }
            ?.distinct()?.takeIf { it.isNotEmpty() } ?: toolOptions.map { it.first })
    }
    LaunchedEffect(selectedToolIds) {
        preferences.edit().putString("selected_tools", selectedToolIds.joinToString(",")).apply()
    }

    LaunchedEffect(accountingRepository) {
        projects = accountingRepository.loadProjects()
        if (selectedProjectLocalId.isBlank()) selectedProjectLocalId = projects.firstOrNull()?.id.orEmpty()
    }

    fun saveProjectLocally(project: ProjectDto, onSaved: () -> Unit = {}) {
        scope.launch {
            runCatching { accountingRepository.saveLocalProject(project) }
                .onSuccess { updated ->
                    projects = updated
                    selectedProjectLocalId = project.id
                    onSaved()
                }
                .onFailure { Toast.makeText(context, "项目保存失败，请重试", Toast.LENGTH_SHORT).show() }
        }
    }

    fun createAndSelectProject() {
        val name = newProjectName.trim()
        if (name.isBlank()) return
        val project = ProjectDto(
            id = UUID.randomUUID().toString(),
            name = name,
            description = "本地自定义项目",
            status = "tracking"
        )
        saveProjectLocally(project) {
            newProjectName = ""
            showProjectPicker = false
        }
    }

    fun openBill(bill: PendingBillEntity) {
        val saved = AccountingFormDraftCodec.decode(bill.draftFormJson)
        val existingAssets = LedgerAssetBreakdownCodec.decode(bill.assetBreakdownJson)
        val gasAsset = existingAssets.firstOrNull { it.kind == "gas" }
        val rewardAsset = existingAssets.firstOrNull { it.kind == "reward" }
        val l1Asset = existingAssets.firstOrNull { it.kind == "l1_fee" }
        val l2Asset = existingAssets.firstOrNull { it.kind == "l2_fee" || it.kind == "execution_fee" }
        val operatorAsset = existingAssets.firstOrNull { it.kind == "operator_fee" }
        activeBill = bill
        gas = saved?.gas ?: bill.gasCostUsd.takeIf { it.isFinite() && it > 0.0 }
            ?.let { String.format(Locale.US, "%.2f", it) }
            ?: gasAsset?.usd_valuation?.takeIf { it.isFinite() && it >= 0.0 }
                ?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()
        reward = saved?.reward.orEmpty()
        rewardUsdEdited = saved?.rewardUsdEdited ?: false
        gasNative = saved?.gasNative ?: gasAsset?.amount_native.orEmpty()
        gasSymbol = saved?.gasSymbol ?: gasAsset?.symbol.orEmpty()
        gasDecimals = saved?.gasDecimals ?: gasAsset?.decimals?.toString().orEmpty()
        gasValuationTimestamp = saved?.gasValuationTimestamp ?: gasAsset?.valuation_timestamp
        gasValuationSource = saved?.gasValuationSource ?: gasAsset?.valuation_source
        gasExecutionLabel = saved?.gasExecutionLabel ?: if (l2Asset?.kind == "l2_fee") "L2 执行费" else "执行费"
        gasL1Native = saved?.gasL1Native ?: l1Asset?.amount_native.orEmpty()
        gasL2Native = saved?.gasL2Native ?: l2Asset?.amount_native.orEmpty()
        gasOperatorNative = saved?.gasOperatorNative ?: operatorAsset?.amount_native.orEmpty()
        gasL1Usd = saved?.gasL1Usd ?: l1Asset?.usd_valuation
        gasL2Usd = saved?.gasL2Usd ?: l2Asset?.usd_valuation
        gasOperatorUsd = saved?.gasOperatorUsd ?: operatorAsset?.usd_valuation
        gasSplitQuoteTimestamp = saved?.gasSplitQuoteTimestamp ?: l1Asset?.valuation_timestamp ?: l2Asset?.valuation_timestamp
        gasSplitSymbol = saved?.gasSplitSymbol ?: l1Asset?.symbol ?: l2Asset?.symbol ?: gasAsset?.symbol.orEmpty()
        rewardNative = saved?.rewardNative ?: rewardAsset?.amount_native.orEmpty()
        rewardSymbol = saved?.rewardSymbol ?: rewardAsset?.symbol.orEmpty()
        rewardDecimals = saved?.rewardDecimals ?: rewardAsset?.decimals?.toString().orEmpty()
        selectedProjectLocalId = saved?.selectedProjectLocalId
            ?: selectedProjectLocalId.takeIf { it.isNotBlank() }
            ?: projects.firstOrNull()?.id.orEmpty()
        showBillList = false
        showConfirm = true
    }

    LaunchedEffect(initialShowBillList, realPending) {
        if (initialShowBillList && realPending.isNotEmpty() && activeBill == null && !showConfirm) {
            // Home's accounting shortcut opens the latest real pending record directly.
            openBill(realPending.first())
        }
    }

    fun createManualAccountingEntry() {
        if (walletAddress.isBlank()) {
            Toast.makeText(context, "请先在记账中心选择一个钱包，再新增手动账单", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            try {
                val bill = PendingBillEntity(
                    id = UUID.randomUUID().toString(),
                    txHash = "MANUAL-" + UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US),
                    walletAddress = walletAddress,
                    gasCostUsd = 0.0,
                    interactionMethod = "手动记账（非链上交易）",
                    timestamp = System.currentTimeMillis(),
                    chain = chainName.ifBlank { "unknown" }
                )
                accountingRepository.addPendingBill(bill)
                openBill(bill)
            } catch (_: Exception) {
                Toast.makeText(context, "创建记账记录失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    
    fun discardManualDraft(bill: PendingBillEntity) {
        if (!bill.txHash.startsWith("MANUAL-")) return
        scope.launch { runCatching { accountingRepository.deletePendingBill(bill.id) } }
    }

    fun deleteDraft(bill: PendingBillEntity) {
        if (!bill.txHash.startsWith("MANUAL-")) return
        scope.launch {
            val ok = runCatching { accountingRepository.deletePendingBill(bill.id) }.isSuccess
            if (!ok) Toast.makeText(context, "删除失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    val gasValue = parseConfirmedUsdAmount(gas)
    val rewardValue = parseConfirmedUsdAmount(reward)
    val nativeAssetsValid =
        isValidNativeAssetInput(gasNative, gasSymbol, gasDecimals) &&
            isValidNativeAssetInput(rewardNative, rewardSymbol, rewardDecimals)

    LaunchedEffect(
        activeBill?.id, selectedProjectLocalId, gas, reward, rewardUsdEdited,
        gasNative, gasSymbol, gasDecimals, gasValuationTimestamp, gasValuationSource,
        gasL1Native, gasL2Native, gasOperatorNative, gasL1Usd, gasL2Usd, gasOperatorUsd,
        gasSplitQuoteTimestamp, gasExecutionLabel, gasSplitSymbol,
        rewardNative, rewardSymbol, rewardDecimals
    ) {
        val bill = activeBill ?: return@LaunchedEffect
        val draftJson = AccountingFormDraftCodec.encode(
            AccountingFormDraft(
                selectedProjectLocalId = selectedProjectLocalId.take(128),
                gas = gas.take(128), reward = reward.take(128), rewardUsdEdited = rewardUsdEdited,
                gasNative = gasNative.take(128), gasSymbol = gasSymbol.take(32), gasDecimals = gasDecimals.take(2),
                gasValuationTimestamp = gasValuationTimestamp?.take(80), gasValuationSource = gasValuationSource?.take(40),
                gasL1Native = gasL1Native.take(128), gasL2Native = gasL2Native.take(128),
                gasOperatorNative = gasOperatorNative.take(128),
                gasL1Usd = gasL1Usd, gasL2Usd = gasL2Usd, gasOperatorUsd = gasOperatorUsd,
                gasSplitQuoteTimestamp = gasSplitQuoteTimestamp?.take(80),
                gasExecutionLabel = gasExecutionLabel.take(32), gasSplitSymbol = gasSplitSymbol.take(32),
                rewardNative = rewardNative.take(128), rewardSymbol = rewardSymbol.take(32), rewardDecimals = rewardDecimals.take(2)
            )
        )
        delay(400)
        runCatching { accountingRepository.savePendingBillDraft(bill.id, draftJson) }
    }

    if (showDock) Box(modifier = modifier.fillMaxSize()) {
        val dockShape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp)

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .fillMaxWidth(0.94f)
                .widthIn(max = 420.dp)
                .height(64.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = dockShape,
                    ambientColor = Color.Black.copy(alpha = 0.06f),
                    spotColor = Color.Black.copy(alpha = 0.06f)
                )

        ) {
            // Clip the capsule background and LazyRow together to the pill boundary.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(dockShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) 0.68f else 0.72f))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), dockShape)
            ) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedToolIds, key = { it }) { id ->
                        val option = toolOptions.firstOrNull { it.first == id } ?: return@items
                        val icon = when (id) {
                            "new_accounting" -> Icons.Outlined.ReceiptLong
                            "copy_address" -> Icons.Outlined.ContentCopy
                            "pending_bills" -> Icons.Outlined.ReceiptLong
                            "refresh_page" -> Icons.Outlined.Refresh
                            "copy_url" -> Icons.Outlined.Link
                            else -> Icons.Outlined.OpenInNew
                        }
                        val iconBackground = MaterialTheme.colorScheme.secondaryContainer
                        val action: () -> Unit = when (id) {
                            "new_accounting" -> { { createManualAccountingEntry() } }
                            "copy_address" -> { { onCopyAddress() } }
                            "pending_bills" -> { { showBillList = true } }
                            "refresh_page" -> { { onRefreshPage() } }
                            "copy_url" -> { { onCopyPageUrl() } }
                            else -> { { onOpenPageExternally() } }
                        }
                        Box(
                            modifier = Modifier.size(width = 60.dp, height = 56.dp)
                                .appPressFeedback(onClick = action, scale = 0.94f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(AppDesignTokens.Radius.Md.dp))
                                        .background(iconBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    option.second,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    item(key = "add_tool") {
                        Column(
                            modifier = Modifier.size(width = 60.dp, height = 56.dp)
                                .appPressFeedback(onClick = { showToolManager = true }, scale = 0.94f)
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier.size(32.dp)
                                    .clip(RoundedCornerShape(AppDesignTokens.Radius.Md.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = "添加工具",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "添加",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWeb3HashSync) {
        Dialog(
            onDismissRequest = { showWeb3HashSync = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Xl.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 8.dp
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("同步链上交易", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "粘贴钱包中已提交的 TxHash。App 会通过该草稿对应的链 RPC 核对发送地址、目标地址、金额和调用数据；核对不一致不会绑定。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    AppTextField(
                        value = web3HashSyncInput,
                        onValueChange = { web3HashSyncInput = it.trim() },
                        placeholder = "TxHash"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextButton(
                            onClick = { showWeb3HashSync = false },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.acct_ui_53)) }
                        AppButton(
                            text = "核对并同步",
                            enabled = web3HashSyncDraftId != null && web3HashSyncInput.isNotBlank(),
                            onClick = {
                                val id = web3HashSyncDraftId ?: return@AppButton
                                scope.launch {
                                    val accepted = runCatching {
                                        web3Repository.attachManuallyVerifiedHash(id, web3HashSyncInput)
                                    }.getOrDefault(false)
                                    if (accepted) {
                                        com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(context, id)
                                        showWeb3HashSync = false
                                        Toast.makeText(context, "TxHash 已核对，开始同步链上回执", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "TxHash 不匹配或链上尚未找到该交易", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showToolManager) {
        AlertDialog(
            onDismissRequest = { showToolManager = false },
            title = { Text("管理工具") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("勾选显示的工具；使用上下箭头调整顺序。", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                    toolOptions.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = option.first in selectedToolIds,
                                onCheckedChange = { checked ->
                                    selectedToolIds = if (checked) selectedToolIds + option.first else selectedToolIds - option.first
                                }
                            )
                            Text(option.second, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            AppTextButton(
                                enabled = option.first in selectedToolIds && selectedToolIds.indexOf(option.first) > 0,
                                onClick = {
                                    val index = selectedToolIds.indexOf(option.first)
                                    if (index > 0) selectedToolIds = selectedToolIds.toMutableList().also {
                                        val item = it.removeAt(index); it.add(index - 1, item)
                                    }
                                }
                            ) { Text("↑") }
                            AppTextButton(
                                enabled = option.first in selectedToolIds && selectedToolIds.indexOf(option.first) < selectedToolIds.lastIndex,
                                onClick = {
                                    val index = selectedToolIds.indexOf(option.first)
                                    if (index >= 0 && index < selectedToolIds.lastIndex) selectedToolIds = selectedToolIds.toMutableList().also {
                                        val item = it.removeAt(index); it.add(index + 1, item)
                                    }
                                }
                            ) { Text("↓") }
                        }
                    }
                    if (selectedToolIds.isEmpty()) Text("至少选择一个工具，或稍后通过默认配置恢复。", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { AppTextButton(onClick = { showToolManager = false }) { Text("完成") } },
            dismissButton = {
                AppTextButton(onClick = { selectedToolIds = toolOptions.map { it.first } }) { Text("恢复默认") }
            }
        )
    }

    if (showBillList) {
        Dialog(
            onDismissRequest = { if (showDock) showBillList = false else onCloseAccounting() },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp).heightIn(max = 520.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Xl.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 8.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.acct_ui_1), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.acct_ui_2), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                        }
                        AppIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = stringResource(R.string.acct_ui_3),
                            onClick = { if (showDock) showBillList = false else onCloseAccounting() }
                        )
                    }
                    if (visibleWeb3Drafts.isNotEmpty()) {
                        Text(stringResource(R.string.acct_ui_4), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visibleWeb3Drafts, key = { "web3-${it.id}" }) { draft ->
                                val statusLabel = when (draft.status) {
                                    Web3DraftStatus.CONFIRMED -> stringResource(R.string.acct_ui_5)
                                    Web3DraftStatus.CONFIRMED_FAILED -> stringResource(R.string.acct_ui_6)
                                    Web3DraftStatus.WAITING_L1_FEE -> stringResource(R.string.acct_ui_7)
                                    Web3DraftStatus.WAITING_OPERATOR_FEE -> stringResource(R.string.acct_ui_8)
                                    Web3DraftStatus.RECEIPT_NEEDS_SYNC -> stringResource(R.string.acct_ui_9)
                                    Web3DraftStatus.TIMED_OUT_NEEDS_SYNC -> stringResource(R.string.acct_ui_10)
                                    Web3DraftStatus.WAITING_RECEIPT -> stringResource(R.string.acct_ui_11)
                                    Web3DraftStatus.PENDING_WALLET, Web3DraftStatus.WAITING_HASH -> stringResource(R.string.acct_ui_12)
                                    Web3DraftStatus.WALLET_UNAVAILABLE -> stringResource(R.string.acct_ui_13)
                                    else -> stringResource(R.string.acct_ui_14)
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(statusLabel, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                draft.txHash?.let { it.take(8) + "..." + it.takeLast(6) }
                                                    ?: "Chain ID ${draft.chainId}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            draft.lastError?.let {
                                                Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                            }
                                        }
                                        when (draft.status) {
                                            Web3DraftStatus.CONFIRMED, Web3DraftStatus.CONFIRMED_FAILED -> {
                                                AppTextButton(onClick = {
                                                    scope.launch {
                                                        val bill = runCatching {
                                                            web3Repository.createPendingAccountingBill(draft.id)
                                                        }.getOrNull()
                                                        if (bill != null) openBill(bill)
                                                        else Toast.makeText(context, stringResource(R.string.acct_ui_15), Toast.LENGTH_SHORT).show()
                                                    }
                                                }) { Text(stringResource(R.string.acct_ui_16)) }
                                            }
                                            Web3DraftStatus.WAITING_L1_FEE -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    AppTextButton(onClick = {
                                                        com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(context, draft.id)
                                                    }) { Text(stringResource(R.string.acct_ui_17)) }
                                                    AppTextButton(onClick = {
                                                        scope.launch {
                                                            val bill = runCatching {
                                                                web3Repository.createManualReviewBillFromDraft(draft.id)
                                                            }.getOrNull()
                                                            if (bill != null) openBill(bill)
                                                            else Toast.makeText(context, stringResource(R.string.acct_ui_18), Toast.LENGTH_SHORT).show()
                                                        }
                                                    }) { Text(stringResource(R.string.acct_ui_19)) }
                                                }
                                            }
                                            Web3DraftStatus.WAITING_OPERATOR_FEE -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    AppTextButton(onClick = {
                                                        com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(context, draft.id)
                                                    }) { Text(stringResource(R.string.acct_ui_17)) }
                                                    AppTextButton(onClick = {
                                                        scope.launch {
                                                            val bill = runCatching {
                                                                web3Repository.createManualReviewBillFromDraft(draft.id)
                                                            }.getOrNull()
                                                            if (bill != null) openBill(bill)
                                                            else Toast.makeText(context, stringResource(R.string.acct_ui_18), Toast.LENGTH_SHORT).show()
                                                        }
                                                    }) { Text(stringResource(R.string.acct_ui_19)) }
                                                }
                                            }
                                            Web3DraftStatus.RECEIPT_NEEDS_SYNC -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    if (com.airdrophunter.app.data.Web3RpcRegistry.endpoint(draft.chainId) != null) {
                                                        AppTextButton(onClick = {
                                                            com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(context, draft.id)
                                                        }) { Text(stringResource(R.string.acct_ui_17)) }
                                                    }
                                                    AppTextButton(onClick = {
                                                        scope.launch {
                                                            val bill = runCatching {
                                                                web3Repository.createManualReviewBillFromDraft(draft.id)
                                                            }.getOrNull()
                                                            if (bill != null) openBill(bill)
                                                            else Toast.makeText(context, stringResource(R.string.acct_ui_18), Toast.LENGTH_SHORT).show()
                                                        }
                                                    }) { Text(stringResource(R.string.acct_ui_19)) }
                                                }
                                            }
                                            Web3DraftStatus.TIMED_OUT_NEEDS_SYNC -> {
                                                if (com.airdrophunter.app.data.Web3RpcRegistry.endpoint(draft.chainId) != null) {
                                                    AppTextButton(onClick = {
                                                        web3HashSyncDraftId = draft.id
                                                        web3HashSyncInput = ""
                                                        showWeb3HashSync = true
                                                    }) { Text(stringResource(R.string.acct_ui_20)) }
                                                } else {
                                                    Text(stringResource(R.string.acct_ui_21), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (realPending.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.acct_ui_22), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(realPending, key = { it.id }) { bill ->
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            Modifier.weight(1f).appPressFeedback(onClick = { openBill(bill) }, scale = 1f).padding(vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                if (bill.id.startsWith("WEB3-") && bill.gasCostUsd == 0.0) {
                                                    val quote = LedgerAssetBreakdownCodec.decode(bill.assetBreakdownJson)
                                                        .firstOrNull { it.kind == "gas" }?.usd_valuation
                                                    quote?.let { String.format(Locale.US, "真实 Gas $%.2f", it) }
                                                        ?: "真实 Gas 已获取 · USD 待填写"
                                                } else String.format(Locale.US, "Gas $%.2f", bill.gasCostUsd),
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text((if (bill.txHash.startsWith("MANUAL-")) stringResource(R.string.acct_ui_manual_entry_prefix) else stringResource(R.string.acct_ui_pending_tx_prefix)) + bill.interactionMethod, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                            Text(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(bill.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (bill.txHash.startsWith("MANUAL-")) {
                                            AppIconButton(
                                                icon = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.acct_ui_23),
                                                onClick = { deleteDraft(bill) },
                                                haptic = AppHaptic.WARNING
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            text = stringResource(R.string.acct_ui_24),
                            onClick = { createManualAccountingEntry() },
                            modifier = Modifier.weight(1f),
                            style = AppButtonStyle.SECONDARY
                        )
                        AppButton(
                            text = "完成",
                            onClick = { if (showDock) showBillList = false else onCloseAccounting() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showConfirm && activeBill != null) {
        val bill = activeBill!!
        val selectedProject = projects.firstOrNull { it.id == selectedProjectLocalId }
        val contributionNetwork = bill.network.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
            ?: bill.chain.trim().lowercase(Locale.US)
        val contributionHashValid = ManualProjectAccountingPolicy.isTransactionHashFormatValid(contributionNetwork, bill.txHash)
        Dialog(
            onDismissRequest = {
                if (!saving) {
                    activeBill?.let(::discardManualDraft)
                    showConfirm = false
                    activeBill = null
                    if (!showDock) onCloseAccounting()
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp).heightIn(max = 620.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Xl.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.acct_ui_25), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        AppIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = stringResource(R.string.acct_ui_26),
                            enabled = !saving,
                            onClick = { activeBill?.let(::discardManualDraft); showConfirm = false; activeBill = null; if (!showDock) onCloseAccounting() }
                        )
                    }
                    Text((if (bill.txHash.startsWith("MANUAL-")) "手动记账（非链上交易）" else stringResource(R.string.acct_ui_27)) + if (bill.txHash.startsWith("MANUAL-")) "" else bill.txHash.take(8) + "..." + bill.txHash.takeLast(6), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                    if (bill.id.startsWith("WEB3-") && bill.interactionMethod.contains("手动核验")) {
                        Text(
                            stringResource(R.string.acct_ui_28),
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (gasL1Native.isNotBlank() || gasL2Native.isNotBlank() || gasOperatorNative.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.acct_ui_29), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                if (gasL2Native.isNotBlank()) Text(
                                    "$gasExecutionLabel：$gasL2Native $gasSplitSymbol" + (gasL2Usd?.let { "（$%.4f）".format(Locale.US, it) } ?: ""),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                                )
                                if (gasL1Native.isNotBlank()) Text(
                                    stringResource(R.string.acct_ui_gas_native_fee, gasL1Native, gasSplitSymbol) + (gasL1Usd?.let { "（$%.4f）".format(Locale.US, it) } ?: ""),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                                )
                                if (gasOperatorNative.isNotBlank()) Text(
                                    stringResource(R.string.acct_ui_operator_fee, gasOperatorNative, gasSplitSymbol) + (gasOperatorUsd?.let { "（$%.4f）".format(Locale.US, it) } ?: ""),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    AppTextField(
                        value = gas,
                        onValueChange = { gas = it; gasValuationTimestamp = Instant.now().toString(); gasValuationSource = "user_input" },
                        placeholder = stringResource(R.string.acct_ui_30),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.acct_ui_31), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        val selectedProject = projects.firstOrNull { it.id == selectedProjectLocalId }
                        AppButton(
                            text = selectedProject?.name ?: stringResource(R.string.acct_ui_32),
                            onClick = { showProjectPicker = true },
                            style = AppButtonStyle.SECONDARY
                        )
                        if (selectedProject == null) {
                            Text(stringResource(R.string.acct_ui_33), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    AppTextField(
                        value = reward,
                        onValueChange = { reward = it; rewardUsdEdited = true },
                        placeholder = stringResource(R.string.acct_ui_34),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Text(stringResource(R.string.acct_ui_35), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                    if (ManualProjectAccountingPolicy.isEligibleForDefaultCommunityContribution(
                            selectedProject?.communityOfficialId, contributionNetwork, bill.txHash
                        )) {
                        Text(stringResource(R.string.acct_ui_36),
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    } else if (selectedProject?.communityOfficialId == null) {
                        Text(stringResource(R.string.acct_ui_37),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text(stringResource(R.string.acct_ui_38),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(stringResource(R.string.acct_ui_39), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = gasNative, onValueChange = { gasNative = it; gasValuationTimestamp = null; gasValuationSource = null },
                            placeholder = stringResource(R.string.acct_ui_40),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.2f)
                        )
                        val gasSymbolChoices = remember(chainName) {
                            val chain = chainName.lowercase(Locale.US)
                            when {
                                "tron" in chain -> listOf("TRX")
                                "sol" in chain -> listOf("SOL")
                                "bsc" in chain || "bnb" in chain || "binance" in chain -> listOf("BNB")
                                "polygon" in chain -> listOf("POL", "MATIC")
                                "ethereum" in chain || chain == "eth" -> listOf("ETH")
                                else -> listOf("ETH", "BNB", "TRX", "SOL", "POL", "MATIC")
                            }
                        }
                        var gasSymbolMenuExpanded by remember { mutableStateOf(false) }
                        var gasSymbolCustom by remember { mutableStateOf(gasSymbol.isNotBlank() && gasSymbol !in gasSymbolChoices) }
                        Box(Modifier.weight(0.8f)) {
                            if (gasSymbolCustom) {
                                AppTextField(
                                    value = gasSymbol,
                                    onValueChange = { gasSymbol = it.uppercase(Locale.US); gasValuationTimestamp = null; gasValuationSource = null },
                                    placeholder = stringResource(R.string.acct_ui_41),
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingContent = {
                                        AppButton(stringResource(R.string.acct_ui_42), onClick = { gasSymbolCustom = false; gasSymbol = gasSymbolChoices.firstOrNull().orEmpty() },
                                            style = AppButtonStyle.TEXT, fullWidth = false, heightDp = 44, fontSizeSp = 14)
                                    }
                                )
                            } else {
                                AppButton(
                                    text = gasSymbol.ifBlank { stringResource(R.string.acct_ui_41) },
                                    onClick = { gasSymbolMenuExpanded = true },
                                    style = AppButtonStyle.SECONDARY
                                )
                                DropdownMenu(expanded = gasSymbolMenuExpanded, onDismissRequest = { gasSymbolMenuExpanded = false }) {
                                    gasSymbolChoices.forEach { symbol ->
                                        DropdownMenuItem(text = { Text(symbol) }, onClick = {
                                            gasSymbol = symbol
                                            gasSymbolCustom = false
                                            gasSymbolMenuExpanded = false
                                            gasValuationTimestamp = null
                                            gasValuationSource = null
                                        })
                                    }
                                    DropdownMenuItem(text = { Text(stringResource(R.string.acct_ui_43)) }, onClick = {
                                        gasSymbol = ""
                                        gasSymbolCustom = true
                                        gasSymbolMenuExpanded = false
                                    })
                                }
                            }
                        }
                    }
                    AppTextField(
                        value = gasDecimals, onValueChange = { gasDecimals = it.filter(Char::isDigit).take(2) },
                        placeholder = stringResource(R.string.acct_ui_44),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = rewardNative, onValueChange = { rewardNative = it },
                            placeholder = stringResource(R.string.acct_ui_45),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.2f)
                        )
                        AppTextField(
                            value = rewardSymbol, onValueChange = { rewardSymbol = it.uppercase(Locale.US) },
                            placeholder = stringResource(R.string.acct_ui_46),
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    AppTextField(
                        value = rewardDecimals, onValueChange = { rewardDecimals = it.filter(Char::isDigit).take(2) },
                        placeholder = stringResource(R.string.acct_ui_47),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    AppButton(
                        text = if (saving) stringResource(R.string.acct_ui_48) else stringResource(R.string.acct_ui_49),
                        enabled = !saving && selectedProjectLocalId.isNotBlank() && gasValue != null && rewardValue != null && nativeAssetsValid,
                        loading = saving,
                        onClick = {
                            val project = projects.firstOrNull { it.id == selectedProjectLocalId } ?: return@AppButton
                            val safeGas = gasValue ?: return@AppButton
                            val safeReward = rewardValue ?: return@AppButton
                            if (saving) return@AppButton
                            val now = Instant.now().toString()
                            val assets = buildList {
                                if (gasNative.isNotBlank()) add(
                                    LedgerAssetAmount(
                                        kind = "gas", amount_native = gasNative.trim(),
                                        symbol = gasSymbol.trim().uppercase(Locale.US),
                                        decimals = gasDecimals.toIntOrNull(),
                                        usd_valuation = if (gasValuationSource != null) safeGas else null,
                                        valuation_timestamp = gasValuationTimestamp,
                                        valuation_source = gasValuationSource
                                    )
                                )
                                if (gasL2Native.isNotBlank()) add(
                                    LedgerAssetAmount(
                                        kind = if (gasExecutionLabel == "L2 执行费") "l2_fee" else "execution_fee",
                                        amount_native = gasL2Native,
                                        symbol = gasSplitSymbol.uppercase(Locale.US), decimals = 18,
                                        usd_valuation = gasL2Usd,
                                        valuation_timestamp = gasSplitQuoteTimestamp,
                                        valuation_source = "OKX".takeIf { gasL2Usd != null }
                                    )
                                )
                                if (gasL1Native.isNotBlank()) add(
                                    LedgerAssetAmount(
                                        kind = "l1_fee", amount_native = gasL1Native,
                                        symbol = gasSplitSymbol.uppercase(Locale.US), decimals = 18,
                                        usd_valuation = gasL1Usd,
                                        valuation_timestamp = gasSplitQuoteTimestamp,
                                        valuation_source = "OKX".takeIf { gasL1Usd != null }
                                    )
                                )
                                if (gasOperatorNative.isNotBlank()) add(
                                    LedgerAssetAmount(
                                        kind = "operator_fee", amount_native = gasOperatorNative,
                                        symbol = gasSplitSymbol.uppercase(Locale.US), decimals = 18,
                                        usd_valuation = gasOperatorUsd,
                                        valuation_timestamp = gasSplitQuoteTimestamp,
                                        valuation_source = "OKX".takeIf { gasOperatorUsd != null }
                                    )
                                )
                                if (rewardNative.isNotBlank()) add(
                                    LedgerAssetAmount(
                                        kind = "reward", amount_native = rewardNative.trim(),
                                        symbol = rewardSymbol.trim().uppercase(Locale.US),
                                        decimals = rewardDecimals.toIntOrNull(),
                                        usd_valuation = if (rewardUsdEdited) safeReward else null,
                                        valuation_timestamp = if (rewardUsdEdited) now else null,
                                        valuation_source = if (rewardUsdEdited) "user_input" else null
                                    )
                                )
                            }
                            val assetJson = LedgerAssetBreakdownCodec.encode(assets)
                            saving = true
                            scope.launch {
                                val autoContribute = ManualProjectAccountingPolicy.isEligibleForDefaultCommunityContribution(
                                    project.communityOfficialId, contributionNetwork, bill.txHash
                                )
                                val ok = runCatching {
                                    accountingRepository.confirmPendingBill(
                                        bill.id, project.id, project.communityOfficialId, project.name, safeGas, safeReward, assetJson,
                                        autoContribute
                                    )
                                }.getOrDefault(false)
                                saving = false
                                if (ok) {
                                    showConfirm = false
                                    activeBill = null
                                    Toast.makeText(context,
                                        if (autoContribute) stringResource(R.string.acct_ui_50) else stringResource(R.string.acct_ui_51),
                                        Toast.LENGTH_SHORT).show()
                                    if (!showDock) onCloseAccounting()
                                } else {
                                    Toast.makeText(context, stringResource(R.string.acct_ui_52), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextButton(onClick = { if (!saving) { activeBill?.let(::discardManualDraft); showConfirm = false; activeBill = null; if (!showDock) onCloseAccounting() } }, enabled = !saving,
                        modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.acct_ui_53)) }
                }
            }
        }
    }

    if (showProjectPicker) {
        Dialog(
            onDismissRequest = { showProjectPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp).heightIn(max = 560.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Xl.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 8.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.acct_ui_54), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        AppIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = stringResource(R.string.acct_ui_55),
                            onClick = { showProjectPicker = false }
                        )
                    }
                    Text(stringResource(R.string.acct_ui_56), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    AppTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        placeholder = stringResource(R.string.acct_ui_57)
                    )
                    AppButton(stringResource(R.string.acct_ui_58), onClick = ::createAndSelectProject,
                        enabled = newProjectName.isNotBlank())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.acct_ui_59), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    if (projects.isEmpty()) {
                        Text(stringResource(R.string.acct_ui_60), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(projects, key = { it.id }) { project ->
                                val selected = selectedProjectLocalId == project.id
                                Surface(
                                    modifier = Modifier.fillMaxWidth().appPressFeedback(onClick = {
                                        selectedProjectLocalId = project.id
                                        showProjectPicker = false
                                    }, scale = 1f),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(project.name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                        if (selected) Text(stringResource(R.string.acct_ui_61), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isValidNativeAssetInput(amount: String, symbol: String, decimals: String): Boolean {
    if (amount.isBlank()) return true
    val value = amount.toBigDecimalOrNull() ?: return false
    if (value.signum() < 0 || symbol.isBlank()) return false
    return decimals.isBlank() || decimals.toIntOrNull()?.let { it in 0..36 } == true
}


@Composable
fun AccountingCenterScreen(
    modifier: Modifier = Modifier,
    isGuest: Boolean = true,
    onClose: () -> Unit = {},
    onConnectWallet: () -> Unit = {},
    initialSection: String = "history",
    initialWalletAddress: String? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val scope = rememberCoroutineScope()
    val accountingRepository = remember(application) { application.appContainer.accountingRepository }
    val pending by accountingRepository.observePendingBills().collectAsStateWithLifecycle(initialValue = emptyList())
    var wallets by remember { mutableStateOf<List<WalletDto>>(emptyList()) }
    var selectedWallet by remember { mutableStateOf<WalletDto?>(null) }
    var walletFilterAddress by remember(initialWalletAddress) { mutableStateOf(initialWalletAddress) }
    var isLoadingWallets by remember { mutableStateOf(true) }
    var walletMenuExpanded by remember { mutableStateOf(false) }
    var walletLoadError by remember { mutableStateOf<String?>(null) }
    var ledgerRows by remember { mutableStateOf<List<PersonalLedgerWriteRequest>>(emptyList()) }
    var isLoadingLedger by remember { mutableStateOf(true) }
    var ledgerLoadError by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember(initialSection) { mutableStateOf(initialSection) }
    var selectedPeriod by remember { mutableStateOf("month") }
    var expandedRecordId by remember { mutableStateOf<String?>(null) }
    var showPendingManager by remember { mutableStateOf(false) }

    suspend fun reloadLedger() {
        isLoadingLedger = true
        ledgerLoadError = null
        try {
            ledgerRows = accountingRepository.loadPersonalLedger()
        } catch (_: Exception) {
            ledgerLoadError = "读取本地账本失败，请稍后重试"
        } finally {
            isLoadingLedger = false
        }
    }

    LaunchedEffect(application, isGuest) {
        isLoadingWallets = true
        walletLoadError = null
        try {
            wallets = application.appContainer.walletRepository.load(isGuest)
            selectedWallet = wallets.firstOrNull { !initialWalletAddress.isNullOrBlank() && it.address.equals(initialWalletAddress, ignoreCase = true) }
                ?: wallets.firstOrNull()
        } catch (_: Exception) {
            wallets = emptyList()
            selectedWallet = null
            walletLoadError = "读取钱包失败，请稍后重试"
        } finally {
            isLoadingWallets = false
        }
    }
    LaunchedEffect(accountingRepository) { reloadLedger() }

    val visiblePending = remember(pending, walletFilterAddress) {
        pending.filterNot { it.txHash.startsWith("MOCK-") }
            .filter { walletFilterAddress.isNullOrBlank() || it.walletAddress.equals(walletFilterAddress, ignoreCase = true) }
    }
    val realPendingCount = visiblePending.size
    val periodRows = remember(ledgerRows, selectedPeriod) {
        val now = java.util.Calendar.getInstance()
        val weekStart = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val monthStartYear = now.get(java.util.Calendar.YEAR)
        val monthStart = now.get(java.util.Calendar.MONTH)
        ledgerRows.filter { row ->
            val timestamp = runCatching { Instant.parse(row.occurred_at).toEpochMilli() }.getOrNull()
                ?: return@filter false
            if (timestamp > now.timeInMillis) return@filter false
            val date = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            when (selectedPeriod) {
                "day" -> date.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
                    date.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
                "week" -> timestamp >= weekStart.timeInMillis
                else -> timestamp <= now.timeInMillis && date.get(java.util.Calendar.YEAR) == monthStartYear &&
                    date.get(java.util.Calendar.MONTH) == monthStart
            }
        }
    }
    val periodSummary = remember(periodRows) { summarizePersonalLedgerRows(periodRows) }
    val incompleteRewardRows = periodSummary.incompleteRewardCount
    val actualGasTotal = periodSummary.actualGasUsd
    val actualRewardTotal = periodSummary.valuedActualRewardUsd
    val completeNetTotal = periodSummary.completeNetProfitUsd
    val pendingBills = visiblePending

    Column(
        modifier = modifier.fillMaxWidth().heightIn(max = 760.dp)
            .navigationBarsPadding().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.acct_ui_62), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.acct_ui_63),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            AppIconButton(icon = Icons.Default.Close, contentDescription = stringResource(R.string.acct_ui_64), onClick = onClose)
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.acct_ui_65), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                    Text(
                        selectedWallet?.let { "${it.wallet_name} · ${it.chain} · ${shortAddress(it.address)}" }
                            ?: if (isLoadingWallets) stringResource(R.string.acct_ui_66) else stringResource(R.string.acct_ui_67),
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(onClick = onConnectWallet) { Text(stringResource(R.string.acct_ui_68)) }
                    Box {
                    AppButton(
                        text = stringResource(R.string.acct_ui_69),
                        onClick = { if (!isLoadingWallets && wallets.isNotEmpty()) walletMenuExpanded = true },
                        enabled = !isLoadingWallets && wallets.isNotEmpty(),
                        style = AppButtonStyle.SECONDARY, fullWidth = false, heightDp = 44, fontSizeSp = 14
                    )
                    DropdownMenu(expanded = walletMenuExpanded, onDismissRequest = { walletMenuExpanded = false }) {
                        wallets.forEach { wallet ->
                            DropdownMenuItem(
                                text = { Text("${wallet.wallet_name} · ${wallet.chain} · ${shortAddress(wallet.address)}") },
                                onClick = {
                                    selectedWallet = wallet
                                    if (selectedSection == "pending") walletFilterAddress = wallet.address
                                    walletMenuExpanded = false
                                }
                            )
                        }
                    }
                    }
                }
            }
        }
        walletLoadError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (!isLoadingWallets && wallets.isEmpty()) {
            Text(stringResource(R.string.acct_ui_70),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("history" to stringResource(R.string.acct_ui_71), "stats" to stringResource(R.string.acct_ui_72), "pending" to stringResource(R.string.acct_ui_73)).forEach { (id, label) ->
                val selected = selectedSection == id
                Surface(
                    modifier = Modifier.weight(1f).height(42.dp)
                        .clip(RoundedCornerShape(AppDesignTokens.Radius.Full.dp))
                        .clickable { selectedSection = id },
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp),
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        when (selectedSection) {
            "history" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.acct_ui_74), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.acct_ui_entry_count, ledgerRows.size), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                    AppTextButton(onClick = { scope.launch { reloadLedger() } }, enabled = !isLoadingLedger) {
                        Text(if (isLoadingLedger) stringResource(R.string.acct_ui_75) else stringResource(R.string.acct_ui_76))
                    }
                }
                if (isLoadingLedger && ledgerRows.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (ledgerLoadError != null && ledgerRows.isEmpty()) {
                    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ledgerLoadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        AppButton(stringResource(R.string.acct_ui_77), onClick = { scope.launch { reloadLedger() } },
                            fullWidth = false, heightDp = 40)
                    }
                } else if (ledgerRows.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                            Text(stringResource(R.string.acct_ui_78), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.acct_ui_79),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            AppButton(stringResource(R.string.acct_ui_80), onClick = { selectedSection = "pending" },
                                style = AppButtonStyle.SECONDARY, fullWidth = false, heightDp = 40)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(ledgerRows, key = { it.client_record_id }) { row ->
                            val expanded = expandedRecordId == row.client_record_id
                            val incomplete = row.extra["usd_profit_complete"] == "false"
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().appPressFeedback(
                                    onClick = { expandedRecordId = if (expanded) null else row.client_record_id },
                                    scale = 0.98f
                                )
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(row.project_name.ifBlank { stringResource(R.string.acct_ui_81) }, color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold)
                                            Text(
                                                runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date.from(Instant.parse(row.occurred_at))) }
                                                    .getOrDefault(row.occurred_at),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                                            Text(stringResource(R.string.acct_ui_51), modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Text(
                                        stringResource(R.string.acct_ui_actual_gas_amount, row.actual_gas_cost_usd) +
                                            if (incomplete) "  ·  收益 USD估值未完成"
                                            else stringResource(R.string.acct_ui_actual_reward_amount, row.actual_reward_usd),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                                    )
                                    if (!incomplete) {
                                        Text(stringResource(R.string.acct_ui_net_profit_amount, row.actual_reward_usd - row.actual_gas_cost_usd),
                                            color = if (row.actual_reward_usd - row.actual_gas_cost_usd >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (expanded) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Text(stringResource(R.string.acct_ui_chain_wallet, row.chain, shortAddress(row.wallet_address)),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                        Text(stringResource(R.string.acct_ui_transaction_hash, row.tx_hash.take(10), row.tx_hash.takeLast(6)),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            "理论 Gas：${row.expected_gas_cost_usd?.let { "$%.2f".format(Locale.US, it) } ?: stringResource(R.string.acct_ui_85)}  ·  理论收益：${row.expected_reward_usd?.let { "$%.2f".format(Locale.US, it) } ?: stringResource(R.string.acct_ui_85)}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall
                                        )
                                        val assets = formatLedgerAssetBreakdown(row.extra["asset_breakdown"])
                                        if (assets.isNotBlank()) Text(assets, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall)
                                        Text("记账方式：${row.interaction_method}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "stats" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("day" to stringResource(R.string.acct_ui_87), "week" to stringResource(R.string.acct_ui_88), "month" to stringResource(R.string.acct_ui_89)).forEach { (id, label) ->
                        AppChip(text = label, selected = selectedPeriod == id, onClick = { selectedPeriod = id })
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(when (selectedPeriod) { "day" -> stringResource(R.string.acct_ui_90); "week" -> stringResource(R.string.acct_ui_91); else -> stringResource(R.string.acct_ui_92) },
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.acct_ui_confirmed_count, periodRows.size), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.acct_ui_93), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                Text("$%.2f".format(Locale.US, actualGasTotal), color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(if (incompleteRewardRows > 0) stringResource(R.string.acct_ui_82) else stringResource(R.string.acct_ui_83),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                Text("$%.2f".format(Locale.US, actualRewardTotal), color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.acct_ui_84), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            Text(completeNetTotal?.let { "$%.2f".format(Locale.US, it) } ?: stringResource(R.string.acct_ui_94),
                                color = completeNetTotal?.let { if (it >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error }
                                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            if (incompleteRewardRows > 0) {
                                Text("${incompleteRewardRows} 笔记录的收益 USD 估值不完整，净收益暂不汇总，避免把未估值金额当作 0。",
                                    color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (periodRows.isEmpty()) {
                    Text(stringResource(R.string.acct_ui_95), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.acct_ui_96),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.acct_ui_97), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.acct_ui_pending_count, realPendingCount) + stringResource(R.string.acct_ui_pending_explanation),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        AppButton(stringResource(R.string.acct_ui_98), onClick = { showPendingManager = true })
                    }
                }
                if (pendingBills.isNotEmpty()) {
                    Text(stringResource(R.string.acct_ui_99), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pendingBills.take(5), key = { it.id }) { bill ->
                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(if (bill.txHash.startsWith("MANUAL-")) stringResource(R.string.acct_ui_19) else stringResource(R.string.acct_ui_100),
                                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text(bill.interactionMethod, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall)
                                    Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(bill.timestamp)),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.acct_ui_101), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showPendingManager) {
        BrowserAccountingToolbar(
            walletAddress = selectedWallet?.address.orEmpty(),
            chainName = selectedWallet?.chain ?: "unknown",
            onCopyAddress = {}, onRefreshPage = {}, onCopyPageUrl = {}, onOpenPageExternally = {},
            modifier = Modifier.fillMaxWidth(),
            initialShowBillList = true,
            showDock = false,
            walletAddressFilter = walletFilterAddress,
            onCloseAccounting = {
                showPendingManager = false
                scope.launch { reloadLedger() }
            }
        )
    }
}
