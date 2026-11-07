package com.airdrophunter.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

@Composable
fun BrowserAccountingToolbar(
    walletAddress: String,
    onCopyAddress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pending by CacheStore.observePendingBills().collectAsStateWithLifecycle(initialValue = emptyList())
    var projects by remember { mutableStateOf<List<ProjectDto>>(emptyList()) }
    var activeBill by remember { mutableStateOf<PendingBillEntity?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var selectedProjectLocalId by remember { mutableStateOf("") }
    var gas by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("0.00") }
    var saving by remember { mutableStateOf(false) }
    var fetchingGas by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val type = object : TypeToken<List<ProjectDto>>() {}.type
        val sources = listOfNotNull(
            CacheStore.readLocal("projects"),
            CacheStore.read("projects"),
            CacheStore.readLocal("pnl_projects")
        )
        projects = sources.flatMap { raw ->
            runCatching { Gson().fromJson<List<ProjectDto>>(raw, type) }.getOrNull().orEmpty()
        }.distinctBy { it.id }
        selectedProjectLocalId = projects.firstOrNull()?.id.orEmpty()
    }

    fun saveProjectLocally(project: ProjectDto) {
        projects = (projects.filterNot { it.id == project.id } + project)
        selectedProjectLocalId = project.id
        scope.launch {
            val type = object : TypeToken<List<ProjectDto>>() {}.type
            val existing = CacheStore.readLocal("pnl_projects")?.let { raw ->
                runCatching { Gson().fromJson<List<ProjectDto>>(raw, type) }.getOrNull()
            }.orEmpty()
            CacheStore.writeLocal(
                "pnl_projects",
                Gson().toJson((existing.filterNot { it.id == project.id } + project))
            )
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
        saveProjectLocally(project)
        newProjectName = ""
        showProjectPicker = false
    }

    fun openBill(bill: PendingBillEntity) {
        activeBill = bill
        gas = String.format(Locale.US, "%.2f", bill.gasCostUsd)
        reward = "0.00"
        if (selectedProjectLocalId.isBlank()) {
            selectedProjectLocalId = projects.firstOrNull()?.id.orEmpty()
        }
        showConfirm = true
    }

    fun fetchRealGasBill() {
        if (fetchingGas) return
        if (walletAddress.isBlank() || walletAddress == "MOCK-WALLET-LOCAL") {
            Toast.makeText(context, "请先选择有效的钱包地址", Toast.LENGTH_SHORT).show()
            return
        }
        fetchingGas = true
        scope.launch {
            try {
                val result = RealGasTrackerRepository().fetchLatestGasCost(walletAddress)
                if (result == null || result.txHash.isBlank() ||
                    !result.gasCostUsd.isFinite() || result.gasCostUsd < 0.0
                ) {
                    Toast.makeText(context, "抓取失败或无记录", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bill = PendingBillEntity(
                    id = UUID.randomUUID().toString(),
                    txHash = result.txHash,
                    walletAddress = walletAddress,
                    gasCostUsd = result.gasCostUsd,
                    interactionMethod = result.interactionMethod,
                    timestamp = System.currentTimeMillis()
                )
                CacheStore.addPendingBill(bill)
                activeBill = bill
                gas = String.format(Locale.US, "%.2f", result.gasCostUsd)
                reward = "0.00"
                if (selectedProjectLocalId.isBlank()) {
                    selectedProjectLocalId = projects.firstOrNull()?.id.orEmpty()
                }
                showConfirm = true
            } catch (_: Exception) {
                Toast.makeText(context, "抓取失败或无记录", Toast.LENGTH_SHORT).show()
            } finally {
                fetchingGas = false
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFDFEFF),
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, AppColors.Border),
        shadowElevation = 9.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolbarCircleButton(
                description = "一键抓取",
                background = Color(0xFFEAF7F1),
                tint = Color(0xFF16845B),
                loading = fetchingGas,
                onClick = ::fetchRealGasBill
            ) {
                if (fetchingGas) {
                    CircularProgressIndicator(Modifier.size(21.dp), color = Color(0xFF16845B), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.FlashOn, contentDescription = "一键抓取", tint = Color(0xFF16845B), modifier = Modifier.size(21.dp))
                }
            }
            ToolbarCircleButton(
                description = "复制地址",
                background = Color(0xFFEDF3FF),
                tint = Color(0xFF356BC5),
                onClick = onCopyAddress
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制地址", tint = Color(0xFF356BC5), modifier = Modifier.size(20.dp))
            }
            if (pending.isEmpty()) {
                Text(
                    "暂无待确认账单",
                    modifier = Modifier.weight(1f).padding(start = 3.dp),
                    color = AppColors.TextHelper,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            } else {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 2.dp)
                ) {
                    items(pending, key = { it.id }) { bill ->
                        Surface(
                            onClick = { openBill(bill) },
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFFFF4E8),
                            border = BorderStroke(1.dp, Color(0xFFF2D8B9))
                        ) {
                            Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                                Text(
                                    String.format(Locale.US, "Gas $%.2f", bill.gasCostUsd),
                                    color = Color(0xFF9A5A16),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    bill.interactionMethod,
                                    color = AppColors.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirm && activeBill != null) {
        val gasValue = gas.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        val rewardValue = reward.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
        Dialog(
            onDismissRequest = {
                if (!saving) {
                    showConfirm = false
                    activeBill = null
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 640.dp),
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("确认本次记账", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "交易：${activeBill?.txHash?.take(12)}…",
                        color = AppColors.TextHelper,
                        style = MaterialTheme.typography.labelSmall
                    )

                    // 1. Gas fee input
                    OutlinedTextField(
                        value = gas,
                        onValueChange = { gas = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gas 费 (USD)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(13.dp)
                    )

                    // 2. Project selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("归属项目", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        val selectedProject = projects.firstOrNull { it.id == selectedProjectLocalId }
                        OutlinedButton(
                            onClick = { showProjectPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text(
                                selectedProject?.name ?: "选择项目",
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text("选择", color = AppColors.Primary)
                        }
                        if (selectedProject == null) {
                            Text("请先从项目列表选择，或新建一个本地项目。", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // 3. Actual reward input
                    OutlinedTextField(
                        value = reward,
                        onValueChange = { reward = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("实际收益 (USD)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(13.dp)
                    )

                    // 4. Confirm action is in normal vertical flow, never overlaid on fields.
                    Button(
                        enabled = !saving && selectedProjectLocalId.isNotBlank() && gasValue != null && rewardValue != null,
                        onClick = {
                            val bill = activeBill ?: return@Button
                            val project = projects.firstOrNull { it.id == selectedProjectLocalId } ?: return@Button
                            val safeGas = gasValue ?: return@Button
                            val safeReward = rewardValue ?: return@Button
                            if (saving) return@Button
                            saving = true
                            scope.launch {
                                val ok = runCatching {
                                    CacheStore.confirmPendingBill(
                                        bill.id,
                                        project.id,
                                        project.communityOfficialId,
                                        project.name,
                                        safeGas,
                                        safeReward
                                    )
                                }.getOrDefault(false)
                                saving = false
                                if (ok) {
                                    showConfirm = false
                                    activeBill = null
                                    Toast.makeText(context, "已确认入账", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "入账失败，请重试", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (saving) "入账中…" else "确认入账")
                    }
                    TextButton(
                        enabled = !saving,
                        onClick = { showConfirm = false; activeBill = null },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("取消") }
                }
            }
        }
    }

    if (showProjectPicker) {
        Dialog(
            onDismissRequest = { showProjectPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(max = 620.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    shadowElevation = 18.dp
                ) {
                    Column(
                        Modifier.fillMaxWidth().imePadding().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("选择归属项目", Modifier.weight(1f), color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { showProjectPicker = false }) { Text("关闭") }
                        }
                        Text("新建项目", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = newProjectName,
                            onValueChange = { newProjectName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("输入新项目名称") },
                            singleLine = true,
                            shape = RoundedCornerShape(13.dp)
                        )
                        Button(
                            onClick = ::createAndSelectProject,
                            enabled = newProjectName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) { Text("+ 新建并选中项目") }
                        HorizontalDivider(color = AppColors.Border)
                        Text("本地已有项目", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        if (projects.isEmpty()) {
                            Text("暂无项目，请在上方新建。", color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
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
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
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
}

@Composable
private fun ToolbarCircleButton(
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
        color = background,
        modifier = Modifier.size(44.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.12f))
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
