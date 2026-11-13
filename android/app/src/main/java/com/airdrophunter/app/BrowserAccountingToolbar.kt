package com.airdrophunter.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.appPressFeedback
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppIconButton
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AppTextField
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.VectorConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.time.Instant

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
    onCloseAccounting: () -> Unit = {}
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
    val realPending = remember(pending) { pending.filterNot { it.txHash.startsWith("MOCK-") } }

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
    var dockDragPosition by remember { mutableStateOf(Offset.Zero) }
    var dockIsDragging by remember { mutableStateOf(false) }
    var showToolManager by remember { mutableStateOf(false) }
    var showWeb3HashSync by remember { mutableStateOf(false) }
    var web3HashSyncDraftId by remember { mutableStateOf<String?>(null) }
    var web3HashSyncInput by remember { mutableStateOf("") }
    val toolOptions = listOf(
        "new_accounting" to "记账",
        "copy_address" to "复制地址",
        "pending_bills" to "待确认记录",
        "refresh_page" to "刷新网页",
        "copy_url" to "复制网址",
        "open_external" to "外部打开"
    )
    val preferences = remember(context) { context.getSharedPreferences("browser_dock_preferences", android.content.Context.MODE_PRIVATE) }
    var selectedToolIds by remember {
        mutableStateOf(preferences.getString("selected_tools", null)?.split(",")
            ?.map { if (it == "mock_bill") "new_accounting" else it }
            ?.filter { id -> toolOptions.any { it.first == id } }
            ?.distinct()?.takeIf { it.isNotEmpty() } ?: toolOptions.map { it.first })
    }
    // One two-dimensional animation value keeps horizontal and vertical position atomic.
    val dockOffset = remember {
        androidx.compose.animation.core.Animatable(Offset.Zero, Offset.VectorConverter)
    }
    LaunchedEffect(selectedToolIds) {
        preferences.edit().putString("selected_tools", selectedToolIds.joinToString(",")).apply()
    }

    LaunchedEffect(accountingRepository) {
        projects = accountingRepository.loadProjects()
        selectedProjectLocalId = projects.firstOrNull()?.id.orEmpty()
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
        activeBill = bill
        reward = ""
        rewardUsdEdited = false
        val existingAssets = LedgerAssetBreakdownCodec.decode(bill.assetBreakdownJson)
        val gasAsset = existingAssets.firstOrNull { it.kind == "gas" }
        val rewardAsset = existingAssets.firstOrNull { it.kind == "reward" }
        val initialGasUsd = bill.gasCostUsd.takeIf { it.isFinite() && it > 0.0 }
            ?: gasAsset?.usd_valuation?.takeIf { it.isFinite() && it >= 0.0 }
        gas = initialGasUsd?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()
        gasNative = gasAsset?.amount_native.orEmpty()
        gasSymbol = gasAsset?.symbol.orEmpty()
        gasDecimals = gasAsset?.decimals?.toString().orEmpty()
        gasValuationTimestamp = gasAsset?.valuation_timestamp
        gasValuationSource = gasAsset?.valuation_source
        val l1Asset = existingAssets.firstOrNull { it.kind == "l1_fee" }
        val l2Asset = existingAssets.firstOrNull { it.kind == "l2_fee" || it.kind == "execution_fee" }
        val operatorAsset = existingAssets.firstOrNull { it.kind == "operator_fee" }
        gasExecutionLabel = if (l2Asset?.kind == "l2_fee") "L2 执行费" else "执行费"
        gasL1Native = l1Asset?.amount_native.orEmpty()
        gasL2Native = l2Asset?.amount_native.orEmpty()
        gasOperatorNative = operatorAsset?.amount_native.orEmpty()
        gasL1Usd = l1Asset?.usd_valuation
        gasL2Usd = l2Asset?.usd_valuation
        gasOperatorUsd = operatorAsset?.usd_valuation
        gasSplitQuoteTimestamp = l1Asset?.valuation_timestamp ?: l2Asset?.valuation_timestamp
        gasSplitSymbol = l1Asset?.symbol ?: l2Asset?.symbol ?: gasAsset?.symbol.orEmpty()
        rewardNative = rewardAsset?.amount_native.orEmpty()
        rewardSymbol = rewardAsset?.symbol.orEmpty()
        rewardDecimals = rewardAsset?.decimals?.toString().orEmpty()
        if (selectedProjectLocalId.isBlank()) {
            selectedProjectLocalId = projects.firstOrNull()?.id.orEmpty()
        }
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

    if (showDock) BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val dockWidth = minOf(maxWidth * 0.94f, 420.dp)
        val maxHorizontalDrag = with(androidx.compose.ui.platform.LocalDensity.current) {
            ((maxWidth - dockWidth) / 2f).toPx()
        }
        val maxVerticalDrag = with(androidx.compose.ui.platform.LocalDensity.current) {
            (maxHeight - 100.dp).coerceAtLeast(0.dp).toPx()
        }
        val dockShape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp)
        val leftSnap = -maxHorizontalDrag
        val rightSnap = maxHorizontalDrag

        fun settleDockAfterDrag() {
            val current = dockDragPosition
            val targetX = when {
                maxHorizontalDrag <= 0f -> 0f
                current.x < 0f -> leftSnap
                else -> rightSnap
            }
            val target = Offset(targetX, current.y.coerceIn(-maxVerticalDrag, 0f))
            scope.launch {
                dockOffset.snapTo(current)
                dockIsDragging = false
                dockOffset.animateTo(target, androidx.compose.animation.core.tween(260))
            }
        }

        // One outer movable target owns both the translucent capsule and its tools.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .fillMaxWidth(0.94f)
                .widthIn(max = 420.dp)
                .height(64.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = dockShape,
                    ambientColor = Color.Black.copy(alpha = 0.06f),
                    spotColor = Color.Black.copy(alpha = 0.06f)
                )
                .offset {
                    val position = if (dockIsDragging) dockDragPosition else dockOffset.value
                    IntOffset(position.x.roundToInt(), position.y.roundToInt())
                }
                .pointerInput(maxWidth, maxHeight) {
                    awaitEachGesture {
                        // Observe the gesture in the Initial pass so the parent Dock can
                        // claim a long-press drag before the child LazyRow consumes motion.
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        // A timeout means the finger stayed down and stationary for the
                        // full long-press interval. Early release or movement cancels the drag.
                        val longPressReached = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var cancelled = false
                            while (!cancelled) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed ||
                                    kotlin.math.hypot(
                                        change.position.x - down.position.x,
                                        change.position.y - down.position.y
                                    ) > viewConfiguration.touchSlop
                                ) {
                                    cancelled = true
                                }
                            }
                            false
                        } == null

                        if (longPressReached) {
                            // Pointer events update one Offset synchronously; no per-move
                            // coroutine or independently cancellable X/Y update is created.
                            dockDragPosition = dockOffset.value
                            dockIsDragging = true
                            var lastPosition = down.position
                            var dragActive = true
                            while (dragActive) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    dragActive = false
                                } else {
                                    val delta = change.position - lastPosition
                                    if (delta != Offset.Zero) {
                                        dockDragPosition = Offset(
                                            (dockDragPosition.x + delta.x).coerceIn(-maxHorizontalDrag, maxHorizontalDrag),
                                            (dockDragPosition.y + delta.y).coerceIn(-maxVerticalDrag, 0f)
                                        )
                                        change.consume()
                                    }
                                    lastPosition = change.position
                                }
                            }
                            settleDockAfterDrag()
                        }
                    }
                }
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
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
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
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(AppDesignTokens.Radius.Md.dp)),
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
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
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
                        ) { Text("取消") }
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
                            Text("交易记录", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("选择待确认记录继续填写，或新增一笔记账", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                        }
                        AppIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "关闭",
                            onClick = { if (showDock) showBillList = false else onCloseAccounting() }
                        )
                    }
                    if (visibleWeb3Drafts.isNotEmpty()) {
                        Text("链上交易", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visibleWeb3Drafts, key = { "web3-${it.id}" }) { draft ->
                                val statusLabel = when (draft.status) {
                                    Web3DraftStatus.CONFIRMED -> "回执已确认"
                                    Web3DraftStatus.CONFIRMED_FAILED -> "链上执行失败 · Gas 已产生"
                                    Web3DraftStatus.WAITING_L1_FEE -> "L1 费用待核实"
                                    Web3DraftStatus.WAITING_OPERATOR_FEE -> "运营商费用待核实"
                                    Web3DraftStatus.RECEIPT_NEEDS_SYNC -> "链上回执待补核"
                                    Web3DraftStatus.TIMED_OUT_NEEDS_SYNC -> "钱包未回传 · 待同步"
                                    Web3DraftStatus.WAITING_RECEIPT -> "等待链上回执"
                                    Web3DraftStatus.PENDING_WALLET, Web3DraftStatus.WAITING_HASH -> "等待钱包返回"
                                    Web3DraftStatus.WALLET_UNAVAILABLE -> "未找到钱包应用"
                                    else -> "处理中"
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
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
                                                        else Toast.makeText(context, "链上费用尚未完整确认", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) { Text("继续记账") }
                                            }
                                            Web3DraftStatus.WAITING_L1_FEE -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    AppTextButton(onClick = {
                                                        com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(context, draft.id)
                                                    }) { Text("重试核实") }
                                                    AppTextButton(onClick = {
                                                        scope.launch {
                                                            val bill = runCatching {
                                                                web3Repository.createManualReviewBillFromDraft(draft.id)
                                                            }.getOrNull()
                                                            if (bill != null) openBill(bill)
                                                            else Toast.makeText(context, "当前交易暂不能转入手动记账", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }) { Text("手动记账") }
                                                }
                                            }
                                            Web3DraftStatus.WAITING_OPERATOR_FEE -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    AppTextButton(onClick = {
                                                        com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(context, draft.id)
                                                    }) { Text("重试核实") }
                                                    AppTextButton(onClick = {
                                                        scope.launch {
                                                            val bill = runCatching {
                                                                web3Repository.createManualReviewBillFromDraft(draft.id)
                                                            }.getOrNull()
                                                            if (bill != null) openBill(bill)
                                                            else Toast.makeText(context, "当前交易暂不能转入手动记账", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }) { Text("手动记账") }
                                                }
                                            }
                                            Web3DraftStatus.RECEIPT_NEEDS_SYNC -> {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    if (com.airdrophunter.app.data.Web3RpcRegistry.endpoint(draft.chainId) != null) {
                                                        AppTextButton(onClick = {
                                                            com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(context, draft.id)
                                                        }) { Text("重试核实") }
                                                    }
                                                    AppTextButton(onClick = {
                                                        scope.launch {
                                                            val bill = runCatching {
                                                                web3Repository.createManualReviewBillFromDraft(draft.id)
                                                            }.getOrNull()
                                                            if (bill != null) openBill(bill)
                                                            else Toast.makeText(context, "当前交易暂不能转入手动记账", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }) { Text("手动记账") }
                                                }
                                            }
                                            Web3DraftStatus.TIMED_OUT_NEEDS_SYNC -> {
                                                if (com.airdrophunter.app.data.Web3RpcRegistry.endpoint(draft.chainId) != null) {
                                                    AppTextButton(onClick = {
                                                        web3HashSyncDraftId = draft.id
                                                        web3HashSyncInput = ""
                                                        showWeb3HashSync = true
                                                    }) { Text("同步链上记录") }
                                                } else {
                                                    Text("该链未配置 RPC，请使用普通手动记账", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
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
                            Text("暂无其他待确认记录", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(realPending, key = { it.id }) { bill ->
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(14.dp),
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
                                            Text((if (bill.txHash.startsWith("MANUAL-")) "手动记账 · " else "待确认交易 · ") + bill.interactionMethod, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                            Text(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(bill.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (bill.txHash.startsWith("MANUAL-")) {
                                            AppIconButton(
                                                icon = Icons.Default.Close,
                                                contentDescription = "删除手动草稿",
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
                            text = "＋ 新增记账",
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
                        Text("确认本次记账", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        AppIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "关闭确认弹窗",
                            enabled = !saving,
                            onClick = { activeBill?.let(::discardManualDraft); showConfirm = false; activeBill = null; if (!showDock) onCloseAccounting() }
                        )
                    }
                    Text((if (bill.txHash.startsWith("MANUAL-")) "手动记账（非链上交易）" else "待确认交易：") + if (bill.txHash.startsWith("MANUAL-")) "" else bill.txHash.take(8) + "..." + bill.txHash.takeLast(6), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                    if (bill.id.startsWith("WEB3-") && bill.interactionMethod.contains("手动核验")) {
                        Text(
                            "自动回执核验未完成。请先在区块浏览器确认交易状态，再填写真实 Gas 和收益；不要把预估费用当成实际费用。",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (gasL1Native.isNotBlank() || gasL2Native.isNotBlank() || gasOperatorNative.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("链上费用明细（原生币）", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                if (gasL2Native.isNotBlank()) Text(
                                    "$gasExecutionLabel：$gasL2Native $gasSplitSymbol" + (gasL2Usd?.let { "（$%.4f）".format(Locale.US, it) } ?: ""),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                                )
                                if (gasL1Native.isNotBlank()) Text(
                                    "L1 数据费：$gasL1Native $gasSplitSymbol" + (gasL1Usd?.let { "（$%.4f）".format(Locale.US, it) } ?: ""),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                                )
                                if (gasOperatorNative.isNotBlank()) Text(
                                    "运营商费用：$gasOperatorNative $gasSplitSymbol" + (gasOperatorUsd?.let { "（$%.4f）".format(Locale.US, it) } ?: ""),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    AppTextField(
                        value = gas,
                        onValueChange = { gas = it; gasValuationTimestamp = Instant.now().toString(); gasValuationSource = "user_input" },
                        placeholder = "Gas 费（USD估值）",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("归属项目", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        val selectedProject = projects.firstOrNull { it.id == selectedProjectLocalId }
                        AppButton(
                            text = selectedProject?.name ?: "选择项目",
                            onClick = { showProjectPicker = true },
                            style = AppButtonStyle.SECONDARY
                        )
                        if (selectedProject == null) {
                            Text("请先选择项目，或新建一个本地项目。", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    AppTextField(
                        value = reward,
                        onValueChange = { reward = it; rewardUsdEdited = true },
                        placeholder = "实际收益（USD估值）",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Text("请手动填写最终实际收益；如果实际为零，也请明确输入 0。未填写不能确认入账。", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                    if (ManualProjectAccountingPolicy.isEligibleForDefaultCommunityContribution(
                            selectedProject?.communityOfficialId, contributionNetwork, bill.txHash
                        )) {
                        Text("该项目属于社区目录，符合条件的记录会自动参与脱敏统计；未提供 Tx Hash 按用户自报计入，提供后须通过链上核验。个人账单明细不会公开。",
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    } else if (selectedProject?.communityOfficialId == null) {
                        Text("这是私人自建项目，仅保存到个人账本，不参与社区统计。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text("当前交易暂不满足社区核验条件；个人账本仍可正常保存。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                    }
                    Text("原生币种明细（不同币种分别记录，不相加）", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = gasNative, onValueChange = { gasNative = it; gasValuationTimestamp = null; gasValuationSource = null },
                            placeholder = "Gas 原生金额",
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
                                    placeholder = "Gas 币种",
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingContent = {
                                        AppButton("选择", onClick = { gasSymbolCustom = false; gasSymbol = gasSymbolChoices.firstOrNull().orEmpty() },
                                            style = AppButtonStyle.TEXT, fullWidth = false, heightDp = 44, fontSizeSp = 12)
                                    }
                                )
                            } else {
                                AppButton(
                                    text = gasSymbol.ifBlank { "Gas 币种" },
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
                                    DropdownMenuItem(text = { Text("自定义币种") }, onClick = {
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
                        placeholder = "Gas 精度（可选）",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = rewardNative, onValueChange = { rewardNative = it },
                            placeholder = "收益原生金额",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.2f)
                        )
                        AppTextField(
                            value = rewardSymbol, onValueChange = { rewardSymbol = it.uppercase(Locale.US) },
                            placeholder = "收益币种",
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    AppTextField(
                        value = rewardDecimals, onValueChange = { rewardDecimals = it.filter(Char::isDigit).take(2) },
                        placeholder = "收益精度（可选）",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    AppButton(
                        text = if (saving) "入账中…" else "确认入账",
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
                                        if (autoContribute) "已确认入账；社区脱敏统计已进入提交/核验流程" else "已确认入账",
                                        Toast.LENGTH_SHORT).show()
                                    if (!showDock) onCloseAccounting()
                                } else {
                                    Toast.makeText(context, "入账失败，请重试", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextButton(onClick = { if (!saving) { activeBill?.let(::discardManualDraft); showConfirm = false; activeBill = null; if (!showDock) onCloseAccounting() } }, enabled = !saving,
                        modifier = Modifier.align(Alignment.End)) { Text("取消") }
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
                        Text("选择归属项目", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        AppIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "关闭项目选择",
                            onClick = { showProjectPicker = false }
                        )
                    }
                    Text("新建本地项目", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    AppTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        placeholder = "输入项目名称"
                    )
                    AppButton("+ 新建并选中", onClick = ::createAndSelectProject,
                        enabled = newProjectName.isNotBlank())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("已有项目", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    if (projects.isEmpty()) {
                        Text("暂无项目，请先新建。", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
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
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(project.name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                        if (selected) Text("已选择", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
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
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    var wallets by remember { mutableStateOf<List<WalletDto>>(emptyList()) }
    var selectedWallet by remember { mutableStateOf<WalletDto?>(null) }
    var isLoadingWallets by remember { mutableStateOf(true) }
    var walletMenuExpanded by remember { mutableStateOf(false) }
    var walletLoadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(application, isGuest) {
        isLoadingWallets = true
        walletLoadError = null
        try {
            wallets = application.appContainer.walletRepository.load(isGuest)
            selectedWallet = wallets.firstOrNull()
        } catch (_: Exception) {
            wallets = emptyList()
            selectedWallet = null
            walletLoadError = "读取钱包失败，请稍后重试"
        } finally {
            isLoadingWallets = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("记账", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Text("选择一条待确认交易，手动填写最终 Gas 与实际收益。新增手动记录前请先选择归属钱包。",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Box {
            AppButton(
                text = selectedWallet?.let { "${it.wallet_name} · ${it.chain} · ${shortAddress(it.address)}" }
                    ?: if (isLoadingWallets) "正在读取钱包…" else "选择记账钱包",
                onClick = { if (!isLoadingWallets && wallets.isNotEmpty()) walletMenuExpanded = true },
                enabled = !isLoadingWallets && wallets.isNotEmpty(),
                style = AppButtonStyle.SECONDARY
            )
            DropdownMenu(expanded = walletMenuExpanded, onDismissRequest = { walletMenuExpanded = false }) {
                wallets.forEach { wallet ->
                    DropdownMenuItem(
                        text = { Text("${wallet.wallet_name} · ${wallet.chain} · ${shortAddress(wallet.address)}") },
                        onClick = { selectedWallet = wallet; walletMenuExpanded = false }
                    )
                }
            }
        }
        walletLoadError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (!isLoadingWallets && wallets.isEmpty()) {
            Text("暂无可用钱包。请先到钱包管理添加公开地址；已有待确认记录仍可查看。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        BrowserAccountingToolbar(
            walletAddress = selectedWallet?.address.orEmpty(),
            chainName = selectedWallet?.chain ?: "unknown",
            onCopyAddress = {},
            onRefreshPage = {},
            onCopyPageUrl = {},
            onOpenPageExternally = {},
            modifier = Modifier.fillMaxWidth(),
            initialShowBillList = true,
            showDock = false,
            onCloseAccounting = onClose
        )
    }
}
