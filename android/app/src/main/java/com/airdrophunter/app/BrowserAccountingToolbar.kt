package com.airdrophunter.app

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.launch
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
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val accountingRepository = remember(application) { application.appContainer.accountingRepository }
    val pending by accountingRepository.observePendingBills().collectAsStateWithLifecycle(initialValue = emptyList())

    var projects by remember { mutableStateOf<List<ProjectDto>>(emptyList()) }
    var activeBill by remember { mutableStateOf<PendingBillEntity?>(null) }
    var showBillList by remember { mutableStateOf(false) }
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
    var rewardNative by remember { mutableStateOf("") }
    var rewardSymbol by remember { mutableStateOf("") }
    var rewardDecimals by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var publicContributionOptIn by remember { mutableStateOf(false) }
    var creatingMockBill by remember { mutableStateOf(false) }
    var showToolManager by remember { mutableStateOf(false) }
    val toolOptions = listOf(
        "mock_bill" to "模拟账单",
        "copy_address" to "复制地址",
        "pending_bills" to "待确认账单",
        "refresh_page" to "刷新网页",
        "copy_url" to "复制网址",
        "open_external" to "外部打开"
    )
    val preferences = remember(context) { context.getSharedPreferences("browser_dock_preferences", android.content.Context.MODE_PRIVATE) }
    var selectedToolIds by remember {
        mutableStateOf(preferences.getString("selected_tools", null)?.split(",")?.filter { id -> toolOptions.any { it.first == id } }
            ?.distinct()?.takeIf { it.isNotEmpty() } ?: toolOptions.map { it.first })
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(selectedToolIds) {
        preferences.edit().putString("selected_tools", selectedToolIds.joinToString(",")).apply()
    }

    LaunchedEffect(accountingRepository) {
        projects = accountingRepository.loadProjects()
        selectedProjectLocalId = projects.firstOrNull()?.id.orEmpty()
    }

    fun saveProjectLocally(project: ProjectDto) {
        projects = (projects.filterNot { it.id == project.id } + project)
        selectedProjectLocalId = project.id
        scope.launch { accountingRepository.saveLocalProject(project) }
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
        saveProjectLocally(project)
        newProjectName = ""
        showProjectPicker = false
    }

    fun openBill(bill: PendingBillEntity) {
        activeBill = bill
        gas = bill.gasCostUsd.takeIf { it.isFinite() && it > 0.0 }?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()
        reward = ""
        rewardUsdEdited = false
        publicContributionOptIn = false
        val existingAssets = LedgerAssetBreakdownCodec.decode(bill.assetBreakdownJson)
        val gasAsset = existingAssets.firstOrNull { it.kind == "gas" }
        val rewardAsset = existingAssets.firstOrNull { it.kind == "reward" }
        gasNative = gasAsset?.amount_native.orEmpty()
        gasSymbol = gasAsset?.symbol.orEmpty()
        gasDecimals = gasAsset?.decimals?.toString().orEmpty()
        gasValuationTimestamp = gasAsset?.valuation_timestamp
        gasValuationSource = gasAsset?.valuation_source
        rewardNative = rewardAsset?.amount_native.orEmpty()
        rewardSymbol = rewardAsset?.symbol.orEmpty()
        rewardDecimals = rewardAsset?.decimals?.toString().orEmpty()
        if (selectedProjectLocalId.isBlank()) {
            selectedProjectLocalId = projects.firstOrNull()?.id.orEmpty()
        }
        showBillList = false
        showConfirm = true
    }

    fun createMockBill() {
        if (creatingMockBill) return
        creatingMockBill = true
        scope.launch {
            try {
                val mockGasSamples = listOf(0.12, 0.28, 0.45, 0.63, 0.91)
                val mockHash = "MOCK-" + UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US)
                val bill = PendingBillEntity(
                    id = UUID.randomUUID().toString(),
                    txHash = mockHash,
                    walletAddress = walletAddress.ifBlank { "MOCK-WALLET-LOCAL" },
                    gasCostUsd = mockGasSamples[pending.size % mockGasSamples.size],
                    interactionMethod = "模拟合约交互",
                    timestamp = System.currentTimeMillis(),
                    chain = chainName.ifBlank { "unknown" }
                )
                accountingRepository.addPendingBill(bill)
                openBill(bill)
            } catch (_: Exception) {
                Toast.makeText(context, "模拟账单创建失败，请重试", Toast.LENGTH_SHORT).show()
            } finally {
                creatingMockBill = false
            }
        }
    }

    fun deleteDraft(bill: PendingBillEntity) {
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

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(100.dp)
    ) {
        val maxHorizontalDrag = with(density) { (configuration.screenWidthDp * 0.03f).dp.toPx() }
        val maxVerticalDrag = with(density) { (configuration.screenHeightDp - 180).coerceAtLeast(0).dp.toPx() }
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.94f)
                .widthIn(max = 420.dp)
                .height(100.dp)
                .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
                .pointerInput(configuration.screenWidthDp, configuration.screenHeightDp) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = {
                            if (abs(dragOffsetX) > maxHorizontalDrag * 0.15f) dragOffsetX = if (dragOffsetX < 0f) -maxHorizontalDrag else maxHorizontalDrag
                            dragOffsetY = dragOffsetY.coerceIn(-maxVerticalDrag, 0f)
                        },
                        onDragCancel = {
                            dragOffsetX = if (dragOffsetX < 0f) -maxHorizontalDrag else maxHorizontalDrag
                        }
                    ) { change, amount ->
                        change.consume()
                        dragOffsetX = (dragOffsetX + amount.x).coerceIn(-maxHorizontalDrag, maxHorizontalDrag)
                        dragOffsetY = (dragOffsetY + amount.y).coerceIn(-maxVerticalDrag, 0f)
                    }
                },
            color = Color(0xDDEAF4FF),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color(0xFFB9D3EA).copy(alpha = 0.8f)),
            shadowElevation = 9.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                selectedToolIds.forEach { id ->
                    val option = toolOptions.firstOrNull { it.first == id } ?: return@forEach
                    val glyph = when (id) {
                        "mock_bill" -> "⚡"
                        "copy_address" -> "▣"
                        "pending_bills" -> "▤"
                        "refresh_page" -> "↻"
                        "copy_url" -> "🔗"
                        else -> "↗"
                    }
                    val tint = when (id) {
                        "mock_bill", "open_external" -> Color(0xFF16845B)
                        "pending_bills" -> Color(0xFF9A5A16)
                        else -> Color(0xFF356BC5)
                    }
                    val background = when (id) {
                        "mock_bill", "open_external" -> Color(0xFFE5F5ED)
                        "pending_bills" -> Color(0xFFFFF1DF)
                        else -> Color(0xFFEAF1FF)
                    }
                    val action: () -> Unit = when (id) {
                        "mock_bill" -> ::createMockBill
                        "copy_address" -> onCopyAddress
                        "pending_bills" -> { { showBillList = true } }
                        "refresh_page" -> onRefreshPage
                        "copy_url" -> onCopyPageUrl
                        else -> onOpenPageExternally
                    }
                    Surface(
                        onClick = action,
                        enabled = !(id == "mock_bill" && creatingMockBill),
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(19.dp),
                        color = background,
                        border = BorderStroke(1.dp, tint.copy(alpha = 0.16f))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(glyph, color = tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(option.second, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
                Surface(
                    onClick = { showToolManager = true },
                    modifier = Modifier.size(68.dp),
                    shape = RoundedCornerShape(19.dp),
                    color = Color(0xFFE8EDF4),
                    border = BorderStroke(1.dp, Color(0xFFB7C4D3).copy(alpha = 0.6f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+", color = Color(0xFF486581), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal)
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
                    Text("勾选显示的工具；使用上下箭头调整顺序。", color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
                    toolOptions.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = option.first in selectedToolIds,
                                onCheckedChange = { checked ->
                                    selectedToolIds = if (checked) selectedToolIds + option.first else selectedToolIds - option.first
                                }
                            )
                            Text(option.second, modifier = Modifier.weight(1f), color = AppColors.TextPrimary)
                            TextButton(
                                enabled = option.first in selectedToolIds && selectedToolIds.indexOf(option.first) > 0,
                                onClick = {
                                    val index = selectedToolIds.indexOf(option.first)
                                    if (index > 0) selectedToolIds = selectedToolIds.toMutableList().also {
                                        val item = it.removeAt(index); it.add(index - 1, item)
                                    }
                                }
                            ) { Text("↑") }
                            TextButton(
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
                    if (selectedToolIds.isEmpty()) Text("至少选择一个工具，或稍后通过默认配置恢复。", color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showToolManager = false }) { Text("完成") } },
            dismissButton = {
                TextButton(onClick = { selectedToolIds = toolOptions.map { it.first } }) { Text("恢复默认") }
            }
        )
    }

    if (showBillList) {
        Dialog(
            onDismissRequest = { showBillList = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp).heightIn(max = 520.dp),
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, AppColors.Border),
                shadowElevation = 14.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("待确认账单", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("选择账单继续记账，红色 X 可删除草稿", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { showBillList = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", tint = AppColors.TextSecondary)
                        }
                    }
                    if (pending.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                            Text("暂无待确认账单", color = AppColors.TextHelper)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pending, key = { it.id }) { bill ->
                                Surface(
                                    color = Color(0xFFFFFAF4),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color(0xFFF2D8B9))
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            Modifier.weight(1f).clickable { openBill(bill) }.padding(vertical = 3.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(String.format(Locale.US, "Gas $%.2f", bill.gasCostUsd), color = Color(0xFF9A5A16), fontWeight = FontWeight.Bold)
                                            Text("模拟账单 · " + bill.interactionMethod, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                            Text(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(bill.timestamp)), color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { deleteDraft(bill) }) {
                                            Icon(Icons.Default.Close, contentDescription = "删除草稿", tint = Color(0xFFD92D20))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = { showBillList = false }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                        Text("完成")
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
                    showConfirm = false
                    activeBill = null
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp).heightIn(max = 620.dp),
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, AppColors.Border),
                shadowElevation = 14.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("确认本次记账", modifier = Modifier.weight(1f), color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(
                            enabled = !saving,
                            onClick = { showConfirm = false; activeBill = null }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "关闭确认弹窗", tint = AppColors.TextSecondary)
                        }
                    }
                    Text("模拟账单：\${bill.txHash.take(16)}…", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = gas,
                        onValueChange = { gas = it; gasValuationTimestamp = Instant.now().toString(); gasValuationSource = "user_input" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gas 费（USD估值）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(13.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("归属项目", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        val selectedProject = projects.firstOrNull { it.id == selectedProjectLocalId }
                        OutlinedButton(onClick = { showProjectPicker = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                            Text(selectedProject?.name ?: "选择项目", modifier = Modifier.weight(1f), maxLines = 1)
                            Text("选择", color = AppColors.Primary)
                        }
                        if (selectedProject == null) {
                            Text("请先选择项目，或新建一个本地项目。", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedTextField(
                        value = reward,
                        onValueChange = { reward = it; rewardUsdEdited = true },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("实际收益（USD估值）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(13.dp)
                    )
                    Text("请手动填写最终实际收益；如果实际为零，也请明确输入 0。未填写不能确认入账。", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = publicContributionOptIn,
                            onCheckedChange = { publicContributionOptIn = it },
                            enabled = !saving && selectedProject?.communityOfficialId != null &&
                                ManualProjectAccountingPolicy.isTransactionVerificationNetworkSupported(contributionNetwork) &&
                                contributionHashValid
                        )
                        Text("将本笔数据匿名贡献至全网大盘（需链上核验）", color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (selectedProject?.communityOfficialId == null ||
                        !ManualProjectAccountingPolicy.isTransactionVerificationNetworkSupported(contributionNetwork)) {
                        Text("该项目或网络暂不支持公共链上核验；个人账本仍可正常保存。", color = AppColors.TextHelper,
                            style = MaterialTheme.typography.labelSmall)
                    } else if (!contributionHashValid) {
                        Text("当前 Tx Hash 格式无效；取消授权仍可正常保存个人账本。",
                            color = AppColors.Danger, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("原生币种明细（不同币种分别记录，不相加）", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = gasNative, onValueChange = { gasNative = it; gasValuationTimestamp = null; gasValuationSource = null },
                            label = { Text("Gas 原生金额") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = gasSymbol, onValueChange = { gasSymbol = it.uppercase(Locale.US); gasValuationTimestamp = null; gasValuationSource = null },
                            label = { Text("Gas 币种") }, singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    OutlinedTextField(
                        value = gasDecimals, onValueChange = { gasDecimals = it.filter(Char::isDigit).take(2) },
                        label = { Text("Gas 精度（可选）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rewardNative, onValueChange = { rewardNative = it },
                            label = { Text("收益原生金额") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = rewardSymbol, onValueChange = { rewardSymbol = it.uppercase(Locale.US) },
                            label = { Text("收益币种") }, singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    OutlinedTextField(
                        value = rewardDecimals, onValueChange = { rewardDecimals = it.filter(Char::isDigit).take(2) },
                        label = { Text("收益精度（可选）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = !saving && selectedProjectLocalId.isNotBlank() && gasValue != null && rewardValue != null && nativeAssetsValid && (!publicContributionOptIn || contributionHashValid),
                        onClick = {
                            val project = projects.firstOrNull { it.id == selectedProjectLocalId } ?: return@Button
                            val safeGas = gasValue ?: return@Button
                            val safeReward = rewardValue ?: return@Button
                            if (saving) return@Button
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
                                val ok = runCatching {
                                    accountingRepository.confirmPendingBill(
                                        bill.id, project.id, project.communityOfficialId, project.name, safeGas, safeReward, assetJson,
                                        publicContributionOptIn
                                    )
                                }.getOrDefault(false)
                                saving = false
                                if (ok) {
                                    showConfirm = false
                                    activeBill = null
                                    Toast.makeText(context,
                                        if (publicContributionOptIn) "私账已确认，匿名贡献等待链上核验" else "已确认入账",
                                        Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "入账失败，请重试", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (saving) "入账中…" else "确认入账")
                    }
                    TextButton(onClick = { if (!saving) { showConfirm = false; activeBill = null } }, enabled = !saving,
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
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, AppColors.Border),
                shadowElevation = 14.dp
            ) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("选择归属项目", modifier = Modifier.weight(1f), color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showProjectPicker = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭项目选择", tint = AppColors.TextSecondary)
                        }
                    }
                    Text("新建本地项目", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("输入项目名称") },
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp)
                    )
                    Button(onClick = ::createAndSelectProject, enabled = newProjectName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                        Text("+ 新建并选中")
                    }
                    HorizontalDivider(color = AppColors.Border)
                    Text("已有项目", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    if (projects.isEmpty()) {
                        Text("暂无项目，请先新建。", color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(projects, key = { it.id }) { project ->
                                val selected = selectedProjectLocalId == project.id
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedProjectLocalId = project.id
                                        showProjectPicker = false
                                    },
                                    color = if (selected) Color(0xFFE8F3F1) else Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (selected) AppColors.Primary else AppColors.Border)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(project.name, Modifier.weight(1f), color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                        if (selected) Text("已选择", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun AccountingCapsuleButton(
    description: String,
    background: Color,
    tint: Color,
    loading: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = !loading,
        shape = CircleShape,
        color = background.copy(alpha = 0.82f),
        modifier = Modifier.size(40.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.14f))
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private fun isValidNativeAssetInput(amount: String, symbol: String, decimals: String): Boolean {
    if (amount.isBlank()) return true
    val value = amount.toBigDecimalOrNull() ?: return false
    if (value.signum() < 0 || symbol.isBlank()) return false
    return decimals.isBlank() || decimals.toIntOrNull()?.let { it in 0..36 } == true
}
