package com.airdrophunter.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun BrowserAccountingToolbar(walletAddress: String, onCopyAddress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending by CacheStore.observePendingBills().collectAsStateWithLifecycle(initialValue = emptyList())
    var projects by remember { mutableStateOf<List<ProjectDto>>(emptyList()) }
    var tools by remember { mutableStateOf(listOf("刷新网页", "查看当前地址")) }
    var activeBill by remember { mutableStateOf<PendingBillEntity?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var showCreateProject by remember { mutableStateOf(false) }
    var showAddTool by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var newToolName by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf("") }
    var gas by remember { mutableStateOf("2.50") }
    var reward by remember { mutableStateOf("0") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val type = object : TypeToken<List<ProjectDto>>() {}.type
        val all = listOf("projects", "pnl_projects").flatMap { key ->
            val raw = CacheStore.readLocal(key) ?: CacheStore.read(key)
            raw?.let { runCatching { Gson().fromJson<List<ProjectDto>>(it, type) }.getOrNull() }.orEmpty()
        }
        projects = all.distinctBy { it.id }
        selectedProjectId = projects.firstOrNull()?.officialIdentifier().orEmpty()
        val savedTools = CacheStore.readLocal("browser_accounting_tools")
        savedTools?.let { runCatching { Gson().fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }.getOrNull() }?.let { tools = listOf("刷新网页", "查看当前地址") + it }
    }

    fun openBill(bill: PendingBillEntity) {
        activeBill = bill
        gas = String.format(java.util.Locale.US, "%.2f", bill.gasCostUsd)
        reward = "0"
        if (selectedProjectId.isBlank()) selectedProjectId = projects.firstOrNull()?.officialIdentifier().orEmpty()
        showConfirm = true
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF7F9FC),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("链上记账助手", Modifier.weight(1f), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge)
                Text("本地 Mock · 不读取链上", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilledTonalButton(onClick = {
                        val bill = PendingBillEntity(UUID.randomUUID().toString(), "mock-" + UUID.randomUUID().toString(),
                            walletAddress, 2.50, "Swap", System.currentTimeMillis())
                        scope.launch { CacheStore.addPendingBill(bill); openBill(bill) }
                    }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                        Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("一键抓取")
                    }
                }
                item {
                    OutlinedButton(onClick = onCopyAddress, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("复制地址")
                    }
                }
                items(tools.drop(2)) { tool ->
                    AssistChip(onClick = { Toast.makeText(context, "工具：$tool（本地演示）", Toast.LENGTH_SHORT).show() }, label = { Text(tool) })
                }
                item {
                    IconButton(onClick = { showAddTool = true }) {
                        Icon(Icons.Default.Add, "添加自定义工具", tint = AppColors.Primary)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("待确认", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(8.dp))
                Text("${pending.size}", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            if (pending.isEmpty()) {
                Text("暂无待确认账单 · 点击「一键抓取」创建 Mock 记录",
                    Modifier.padding(horizontal = 13.dp, vertical = 3.dp), color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pending, key = { it.id }) { bill ->
                        Surface(shape = RoundedCornerShape(12.dp), color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)) {
                            Row(Modifier.widthIn(min = 160.dp, max = 230.dp).padding(start = 10.dp, end = 3.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { openBill(bill) }.padding(vertical = 3.dp)) {
                                    Text(bill.interactionMethod, color = AppColors.TextPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Text(String.format(java.util.Locale.US, "Gas $%.2f", bill.gasCostUsd), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { scope.launch { CacheStore.deletePendingBill(bill.id) } }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.Delete, "删除待确认账单", tint = Color(0xFFD83A45), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirm && activeBill != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false; activeBill = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            containerColor = Color.White,
            shape = RoundedCornerShape(22.dp),
            title = { Text("确认本次记账", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${activeBill!!.interactionMethod} · Mock 账单", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(gas, { gas = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Gas 费 (USD)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedButton(onClick = { showProjectPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(projects.firstOrNull { it.officialIdentifier() == selectedProjectId }?.name ?: "选择记账项目")
                    }
                    OutlinedTextField(reward, { reward = it }, modifier = Modifier.fillMaxWidth(), label = { Text("收益 (USD)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                Button(
                    enabled = !saving && selectedProjectId.isNotBlank() && gas.toDoubleOrNull()?.let { it >= 0 } == true && reward.toDoubleOrNull()?.let { it >= 0 } == true,
                    onClick = {
                        val bill = activeBill ?: return@Button
                        val g = gas.toDoubleOrNull() ?: return@Button
                        val r = reward.toDoubleOrNull() ?: return@Button
                        saving = true
                        scope.launch {
                            val projectName = projects.firstOrNull { it.officialIdentifier() == selectedProjectId }?.name ?: "空投项目"
                            val done = CacheStore.confirmPendingBill(bill.id, selectedProjectId, projectName, g, r)
                            saving = false
                            if (done) { showConfirm = false; activeBill = null }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) { Text(if (saving) "正在入账…" else "确认入账") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false; activeBill = null }) { Text("取消") } }
        )
    }

    if (showProjectPicker) {
        AlertDialog(
            onDismissRequest = { showProjectPicker = false },
            title = { Text("选择社区项目") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    projects.forEach { project ->
                        TextButton(onClick = { selectedProjectId = project.officialIdentifier(); showProjectPicker = false },
                            modifier = Modifier.fillMaxWidth()) { Text(project.name) }
                    }
                    TextButton(onClick = { showProjectPicker = false; showCreateProject = true }) { Text("+ 新建项目", color = AppColors.Primary) }
                }
            },
            confirmButton = { TextButton(onClick = { showProjectPicker = false }) { Text("关闭") } }
        )
    }

    if (showCreateProject) {
        AlertDialog(
            onDismissRequest = { showCreateProject = false },
            title = { Text("新建本地记账项目") },
            text = { OutlinedTextField(newProjectName, { newProjectName = it }, label = { Text("项目备注名称") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = newProjectName.isNotBlank(), onClick = {
                    val created = ProjectDto(UUID.randomUUID().toString(), newProjectName.trim(), "本地记账项目", "tracking")
                    projects = projects + created
                    selectedProjectId = created.officialIdentifier()
                    newProjectName = ""
                    showCreateProject = false
                    scope.launch {
                        val existing = CacheStore.readLocal("pnl_projects")?.let {
                            runCatching { Gson().fromJson<List<ProjectDto>>(it, object : TypeToken<List<ProjectDto>>() {}.type) }.getOrNull()
                        }.orEmpty()
                        CacheStore.writeLocal("pnl_projects", Gson().toJson(existing + created))
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreateProject = false }) { Text("取消") } }
        )
    }

    if (showAddTool) {
        AlertDialog(
            onDismissRequest = { showAddTool = false },
            title = { Text("添加快捷工具") },
            text = { OutlinedTextField(newToolName, { newToolName = it }, label = { Text("工具名称") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = newToolName.isNotBlank(), onClick = {
                    val next = tools + newToolName.trim()
                    tools = next
                    newToolName = ""
                    showAddTool = false
                    scope.launch { CacheStore.writeLocal("browser_accounting_tools", Gson().toJson(next.drop(2))) }
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddTool = false }) { Text("取消") } }
        )
    }
}
