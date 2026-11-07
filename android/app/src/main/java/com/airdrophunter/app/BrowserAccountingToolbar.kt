package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun BrowserAccountingToolbar(walletAddress: String, onCopyAddress: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pending by CacheStore.observePendingBills().collectAsStateWithLifecycle(initialValue = emptyList())
    var projects by remember { mutableStateOf<List<ProjectDto>>(emptyList()) }
    var activeBill by remember { mutableStateOf<PendingBillEntity?>(null) }
    var showPanel by remember { mutableStateOf(false) }
    var showCopyCard by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var showCreateProject by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf("") }
    var gas by remember { mutableStateOf("2.50") }
    var reward by remember { mutableStateOf("0") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val type = object : TypeToken<List<ProjectDto>>() {}.type
        projects = listOf("projects", "pnl_projects").flatMap { key ->
            val raw = CacheStore.readLocal(key) ?: CacheStore.read(key)
            raw?.let { runCatching { Gson().fromJson<List<ProjectDto>>(it, type) }.getOrNull() }.orEmpty()
        }.distinctBy { it.id }
        selectedProjectId = projects.firstOrNull()?.officialIdentifier().orEmpty()
    }

    fun createMockBill() {
        val bill = PendingBillEntity(UUID.randomUUID().toString(), "mock-" + UUID.randomUUID(),
            walletAddress, 2.50, "Swap", System.currentTimeMillis())
        scope.launch {
            CacheStore.addPendingBill(bill)
            activeBill = bill
            gas = "2.50"
            reward = "0"
            showConfirm = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xDDF7F9FC),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        border = BorderStroke(1.dp, AppColors.Border),
        shadowElevation = 5.dp
    ) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarCircleIcon(Icons.Default.FlashOn, "记账抓取", Color(0xFFEAF7F1), Color(0xFF16845B), ::createMockBill)
            Spacer(Modifier.width(22.dp))
            ToolbarCircleIcon(Icons.Default.ContentCopy, "复制地址", Color(0xFFEDF3FF), Color(0xFF356BC5), { showCopyCard = true })
            Spacer(Modifier.width(22.dp))
            ToolbarCircleIcon(Icons.Default.ReceiptLong, "待确认账单", Color(0xFFFFF3E7), Color(0xFFB86A18), { showPanel = true })
            Spacer(Modifier.width(22.dp))
            ToolbarCircleIcon(Icons.Default.Add, "新建项目", Color(0xFFF0EDFF), Color(0xFF7055B8), {
                newProjectName = ""
                showCreateProject = true
            })
        }
    }

    if (showCopyCard) {
        Dialog(onDismissRequest = { showCopyCard = false }) {
            Surface(Modifier.fillMaxWidth(), color = Color.White,
                shape = RoundedCornerShape(22.dp), shadowElevation = 14.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("复制钱包地址", color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(walletAddress.ifBlank { "当前没有可用的钱包地址" },
                        color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { onCopyAddress(); showCopyCard = false },
                        enabled = walletAddress.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) { Text("复制到剪贴板") }
                }
            }
        }
    }

    if (showPanel) {
        Dialog(onDismissRequest = { showPanel = false }) {
            Surface(Modifier.fillMaxWidth(), color = Color.White,
                shape = RoundedCornerShape(24.dp), shadowElevation = 14.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("待确认账单", color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${pending.size} 笔等待确认", color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { showPanel = false }) { Text("关闭") }
                    }
                    if (pending.isEmpty()) {
                        Text("暂无待确认账单。点击底部闪电图标可抓取一笔 Mock 账单。",
                            color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pending, key = { it.id }) { bill ->
                                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF7F9FC),
                                    border = BorderStroke(1.dp, AppColors.Border)) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).clickable {
                                            activeBill = bill
                                            gas = String.format(java.util.Locale.US, "%.2f", bill.gasCostUsd)
                                            reward = "0"
                                            if (selectedProjectId.isBlank()) selectedProjectId = projects.firstOrNull()?.officialIdentifier().orEmpty()
                                            showPanel = false
                                            showConfirm = true
                                        }) {
                                            Text(bill.interactionMethod, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                            Text(String.format(java.util.Locale.US, "Gas $%.2f · 点击确认", bill.gasCostUsd),
                                                color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(onClick = { scope.launch { CacheStore.deletePendingBill(bill.id) } }) {
                                            Text("删除", color = Color(0xFFD83242))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { showPanel = false; newProjectName = ""; showCreateProject = true },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                        Text("+ 新建本地项目")
                    }
                }
            }
        }
    }

    if (showConfirm && activeBill != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false; activeBill = null },
            containerColor = Color.White, shape = RoundedCornerShape(22.dp),
            title = { Text("确认本次记账", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(gas, { gas = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gas 费 (USD)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showProjectPicker = true }, modifier = Modifier.weight(1f)) {
                            Text(projects.firstOrNull { it.officialIdentifier() == selectedProjectId }?.name ?: "选择项目", maxLines = 1)
                        }
                        TextButton(onClick = { newProjectName = ""; showCreateProject = true }) {
                            Text("+ 新建项目", color = AppColors.Primary, maxLines = 1)
                        }
                    }
                    OutlinedTextField(reward, { reward = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("实际收益 (USD)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                Button(enabled = !saving && selectedProjectId.isNotBlank() &&
                    gas.toDoubleOrNull()?.let { it >= 0 } == true && reward.toDoubleOrNull()?.let { it >= 0 } == true,
                    onClick = {
                        val bill = activeBill ?: return@Button
                        val g = gas.toDoubleOrNull() ?: return@Button
                        val r = reward.toDoubleOrNull() ?: return@Button
                        saving = true
                        scope.launch {
                            val name = projects.firstOrNull { it.officialIdentifier() == selectedProjectId }?.name ?: "空投项目"
                            val ok = CacheStore.confirmPendingBill(bill.id, selectedProjectId, name, g, r)
                            saving = false
                            if (ok) { showConfirm = false; activeBill = null }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                    Text(if (saving) "入账中…" else "确认入账")
                }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false; activeBill = null }) { Text("取消") } }
        )
    }

    if (showProjectPicker) {
        AlertDialog(onDismissRequest = { showProjectPicker = false }, containerColor = Color.White,
            title = { Text("选择项目") },
            text = { Column { projects.forEach { p ->
                TextButton(onClick = { selectedProjectId = p.officialIdentifier(); showProjectPicker = false },
                    modifier = Modifier.fillMaxWidth()) { Text(p.name) }
            } } },
            confirmButton = { TextButton(onClick = { showProjectPicker = false }) { Text("关闭") } })
    }

    if (showCreateProject) {
        AlertDialog(onDismissRequest = { showCreateProject = false }, containerColor = Color.White,
            title = { Text("新建本地项目") },
            text = { OutlinedTextField(newProjectName, { newProjectName = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("项目名称") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = newProjectName.isNotBlank(), onClick = {
                    val created = ProjectDto(UUID.randomUUID().toString(), newProjectName.trim(), "本地记账项目", "tracking")
                    projects = projects + created
                    selectedProjectId = created.officialIdentifier()
                    showCreateProject = false
                    scope.launch {
                        val old = CacheStore.readLocal("pnl_projects")?.let {
                            runCatching { Gson().fromJson<List<ProjectDto>>(it, object : TypeToken<List<ProjectDto>>() {}.type) }.getOrNull()
                        }.orEmpty()
                        CacheStore.writeLocal("pnl_projects", Gson().toJson(old + created))
                    }
                }) { Text("创建", color = AppColors.Primary) }
            },
            dismissButton = { TextButton(onClick = { showCreateProject = false }) { Text("取消") } })
    }
}

@Composable
private fun ToolbarCircleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, shape = CircleShape, color = background, modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
        }
    }
}
