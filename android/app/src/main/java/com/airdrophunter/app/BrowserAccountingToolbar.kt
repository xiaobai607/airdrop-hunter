package com.airdrophunter.app

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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

@Composable
fun BrowserAccountingToolbar(
    walletAddress: String,
    network: String = "unknown",
    onCopyAddress: () -> Unit,
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
    var reward by remember { mutableStateOf("0.00") }
    var saving by remember { mutableStateOf(false) }

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
        gas = String.format(Locale.US, "%.2f", bill.gasCostUsd)
        reward = "0.00"
        if (selectedProjectLocalId.isBlank()) {
            selectedProjectLocalId = projects.firstOrNull()?.id.orEmpty()
        }
        showBillList = false
        showConfirm = true
    }

    fun deleteDraft(bill: PendingBillEntity) {
        scope.launch {
            val ok = runCatching { accountingRepository.deletePendingBill(bill.id) }.isSuccess
            if (!ok) Toast.makeText(context, "删除失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    val gasValue = gas.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    val rewardValue = reward.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

    Surface(
        modifier = modifier,
        color = Color(0xCCFFFFFF),
        shape = CircleShape,
        border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.8f)),
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            AccountingCapsuleButton(
                description = "复制钱包地址",
                background = Color(0xFFEDF3FF),
                tint = Color(0xFF356BC5),
                onClick = onCopyAddress
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制钱包地址", tint = Color(0xFF356BC5), modifier = Modifier.size(18.dp))
            }
            AccountingCapsuleButton(
                description = "待确认账单",
                background = Color(0xFFFFF4E8),
                tint = Color(0xFF9A5A16),
                onClick = { showBillList = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "待确认账单", tint = Color(0xFF9A5A16), modifier = Modifier.size(20.dp))
            }
        }
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
                                            Text("待核实交易 · " + bill.interactionMethod, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
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
                    Text("链上交易：\${bill.txHash.take(16)}…", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = gas,
                        onValueChange = { gas = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gas 费 (USD)") },
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
                        onValueChange = { reward = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("实际收益 (USD)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(13.dp)
                    )
                    Button(
                        enabled = !saving && selectedProjectLocalId.isNotBlank() && gasValue != null && rewardValue != null,
                        onClick = {
                            val project = projects.firstOrNull { it.id == selectedProjectLocalId } ?: return@Button
                            val safeGas = gasValue ?: return@Button
                            val safeReward = rewardValue ?: return@Button
                            if (saving) return@Button
                            saving = true
                            scope.launch {
                                val ok = runCatching {
                                    accountingRepository.confirmPendingBill(
                                        bill.id, project.id, project.communityOfficialId, project.name, safeGas, safeReward
                                    )
                                }.getOrDefault(false)
                                saving = false
                                if (ok) {
                                    CommunityContributionSyncScheduler.enqueue(context)
                                    showConfirm = false
                                    activeBill = null
                                    Toast.makeText(context, "已确认入账", Toast.LENGTH_SHORT).show()
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
