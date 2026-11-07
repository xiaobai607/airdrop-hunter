package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
    val scope = rememberCoroutineScope()
    val pending by CacheStore.observePendingBills().collectAsStateWithLifecycle(initialValue = emptyList())
    var projects by remember { mutableStateOf<List<ProjectDto>>(emptyList()) }
    var activeBill by remember { mutableStateOf<PendingBillEntity?>(null) }
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
        val all = listOf("projects", "pnl_projects").flatMap { key ->
            val raw = CacheStore.readLocal(key) ?: CacheStore.read(key)
            raw?.let { runCatching { Gson().fromJson<List<ProjectDto>>(it, type) }.getOrNull() }.orEmpty()
        }
        projects = all.distinctBy { it.id }
        selectedProjectId = projects.firstOrNull()?.officialIdentifier().orEmpty()
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
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilledTonalButton(onClick = {
                        val bill = PendingBillEntity(UUID.randomUUID().toString(),
                            "mock-" + UUID.randomUUID().toString(), walletAddress, 2.50, "Swap", System.currentTimeMillis())
                        scope.launch { CacheStore.addPendingBill(bill); openBill(bill) }
                    }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                        Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp)); Text("一键抓取")
                    }
                }
                item {
                    OutlinedButton(onClick = onCopyAddress,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp)); Text("复制地址")
                    }
                }
                item {
                    OutlinedButton(onClick = { newProjectName = ""; showCreateProject = true },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp)); Text("添加项目")
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pending.isEmpty()) {
                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color.White,
                            border = BorderStroke(1.dp, AppColors.Border)) {
                            Text("暂无待确认账单", Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    items(pending, key = { it.id }) { bill ->
                        Surface(shape = RoundedCornerShape(13.dp), color = Color.White,
                            border = BorderStroke(1.dp, AppColors.Border)) {
                            Column(Modifier.widthIn(min = 148.dp, max = 205.dp).padding(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(bill.interactionMethod, Modifier.weight(1f).clickable { openBill(bill) },
                                        color = AppColors.TextPrimary, style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    IconButton(onClick = { scope.launch { CacheStore.deletePendingBill(bill.id) } },
                                        modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Close, "删除待确认账单", tint = Color(0xFFD83242),
                                            modifier = Modifier.size(17.dp))
                                    }
                                }
                                Text(String.format(java.util.Locale.US, "Gas $%.2f · 点击确认", bill.gasCostUsd),
                                    Modifier.clickable { openBill(bill) }, color = AppColors.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
            containerColor = Color.White,
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("确认本次记账", Modifier.weight(1f), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showConfirm = false; activeBill = null }) {
                        Icon(Icons.Default.Close, "关闭", tint = AppColors.TextSecondary)
                    }
                }
            },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${activeBill!!.interactionMethod} · Mock 账单",
                        color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(gas, { gas = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gas 费 (USD)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showProjectPicker = true }, modifier = Modifier.weight(1f)) {
                            Text(projects.firstOrNull { it.officialIdentifier() == selectedProjectId }?.name ?: "选择项目",
                                maxLines = 1)
                        }
                        TextButton(onClick = { newProjectName = ""; showCreateProject = true },
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("+ 新建项目", color = AppColors.Primary, maxLines = 1)
                        }
                    }
                    OutlinedTextField(reward, { reward = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("实际收益 (USD)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                Button(
                    enabled = !saving && selectedProjectId.isNotBlank() &&
                        gas.toDoubleOrNull()?.let { it >= 0 } == true &&
                        reward.toDoubleOrNull()?.let { it >= 0 } == true,
                    onClick = {
                        val bill = activeBill ?: return@Button
                        val g = gas.toDoubleOrNull() ?: return@Button
                        val r = reward.toDoubleOrNull() ?: return@Button
                        saving = true
                        scope.launch {
                            val name = projects.firstOrNull { it.officialIdentifier() == selectedProjectId }?.name ?: "空投项目"
                            val done = CacheStore.confirmPendingBill(bill.id, selectedProjectId, name, g, r)
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
            containerColor = Color.White,
            title = { Text("选择记账项目", color = AppColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (projects.isEmpty()) Text("暂无项目，请点击新建项目。", color = AppColors.TextSecondary)
                    projects.forEach { project ->
                        TextButton(onClick = {
                            selectedProjectId = project.officialIdentifier()
                            showProjectPicker = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(project.name, color = AppColors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProjectPicker = false }) { Text("关闭") } }
        )
    }

    if (showCreateProject) {
        AlertDialog(
            onDismissRequest = { showCreateProject = false },
            containerColor = Color.White,
            title = { Text("新建本地记账项目", color = AppColors.TextPrimary) },
            text = {
                OutlinedTextField(newProjectName, { newProjectName = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("项目名称") }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = newProjectName.isNotBlank(), onClick = {
                    val created = ProjectDto(UUID.randomUUID().toString(), newProjectName.trim(), "本地记账项目", "tracking")
                    projects = projects + created
                    selectedProjectId = created.officialIdentifier()
                    newProjectName = ""
                    showCreateProject = false
                    scope.launch {
                        val existing = CacheStore.readLocal("pnl_projects")?.let {
                            runCatching { Gson().fromJson<List<ProjectDto>>(it,
                                object : TypeToken<List<ProjectDto>>() {}.type) }.getOrNull()
                        }.orEmpty()
                        CacheStore.writeLocal("pnl_projects", Gson().toJson(existing + created))
                    }
                }) { Text("创建", color = AppColors.Primary) }
            },
            dismissButton = { TextButton(onClick = { showCreateProject = false }) { Text("取消") } }
        )
    }
}
