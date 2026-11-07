package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PnlAssistantOverlay(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val pending by CacheStore.observePendingBills().collectAsStateWithLifecycle(initialValue = emptyList())
    val records by CacheStore.observePnlRecords().collectAsStateWithLifecycle(initialValue = emptyList())
    var projects by remember { mutableStateOf<List<ProjectDto>>(emptyList()) }
    var wallet by remember { mutableStateOf("MOCK-WALLET-LOCAL") }
    var visible by remember { mutableStateOf(true) }
    var armed by remember { mutableStateOf(false) }
    var activeBill by remember { mutableStateOf<PendingBillEntity?>(null) }
    var sheet by remember { mutableStateOf(false) }
    var selectedProject by remember { mutableStateOf("") }
    var gas by remember { mutableStateOf("2.50") }
    var reward by remember { mutableStateOf("0") }
    var menu by remember { mutableStateOf(false) }
    var newProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val gson = Gson()
        val type = object : TypeToken<List<ProjectDto>>() {}.type
        val lists = listOf("projects", "pnl_projects").mapNotNull { key ->
            val raw = CacheStore.readLocal(key) ?: CacheStore.read(key)
            raw?.let { runCatching { gson.fromJson<List<ProjectDto>>(it, type) }.getOrNull() }
        }.flatten()
        projects = lists.distinctBy { it.id }
        selectedProject = projects.firstOrNull()?.officialIdentifier().orEmpty()
        val walletType = object : TypeToken<List<WalletDto>>() {}.type
        val wallets = CacheStore.readLocal("wallets")?.let { runCatching { gson.fromJson<List<WalletDto>>(it, walletType) }.getOrNull() }.orEmpty()
        wallet = wallets.firstOrNull()?.address ?: "MOCK-WALLET-LOCAL"
    }

    fun openBill(bill: PendingBillEntity) {
        activeBill = bill
        gas = String.format(java.util.Locale.US, "%.2f", bill.gasCostUsd)
        reward = "0"
        if (selectedProject.isBlank()) selectedProject = projects.firstOrNull()?.officialIdentifier().orEmpty()
        sheet = true
    }

    Column(modifier.widthIn(max = 360.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (!visible) {
            Surface(onClick = { visible = true }, color = AppColors.Primary, shape = RoundedCornerShape(50.dp), shadowElevation = 8.dp) {
                Text("账", Modifier.padding(horizontal = 15.dp, vertical = 9.dp), color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, AppColors.Border), elevation = CardDefaults.cardElevation(9.dp)) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("链上记账助手", Modifier.weight(1f), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            if (armed) { armed = false; visible = false }
                            else { armed = true; scope.launch { delay(2000); armed = false } }
                        }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, "关闭", tint = if (armed) AppColors.Danger else AppColors.TextHelper, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (armed) Text("再点一次关闭(2s)", color = AppColors.Danger, style = MaterialTheme.typography.labelSmall)
                    Button(onClick = {
                        val bill = PendingBillEntity(UUID.randomUUID().toString(), "mock-" + UUID.randomUUID().toString(),
                            wallet, 2.50, "Swap", System.currentTimeMillis())
                        scope.launch { CacheStore.addPendingBill(bill); openBill(bill) }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        shape = RoundedCornerShape(12.dp)) { Text("一键抓取最新花费", fontWeight = FontWeight.SemiBold) }
                    if (pending.isNotEmpty()) {
                        Text("待确认账单 · ${pending.size}", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                        pending.take(2).forEach { bill ->
                            Row(Modifier.fillMaxWidth().clickable { openBill(bill) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${bill.interactionMethod} · 待确认", Modifier.weight(1f), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                Text("继续", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    HorizontalDivider(color = AppColors.Border)
                    Text("已入账", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    if (records.isEmpty()) Text("确认账单后，这里会显示项目盈亏", color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
                    records.take(3).forEach { PnlRecordRow(it, projects) }
                }
            }
        }
    }

    if (sheet && activeBill != null) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("确认本次链上花费", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${activeBill!!.interactionMethod} · Mock 交易记录", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(gas, { gas = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Gas 费 (USD)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Box {
                    OutlinedTextField(value = projects.firstOrNull { it.officialIdentifier() == selectedProject }?.name ?: "请选择项目",
                        onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(), label = { Text("归属空投项目") },
                        trailingIcon = { TextButton(onClick = { menu = true }) { Text("选择") } })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        projects.forEach { project -> DropdownMenuItem(text = { Text(project.name) }, onClick = { selectedProject = project.officialIdentifier(); menu = false }) }
                        DropdownMenuItem(text = { Text("+ 新建项目", color = AppColors.Primary) }, onClick = { menu = false; newProjectDialog = true })
                    }
                }
                OutlinedTextField(reward, { reward = it }, modifier = Modifier.fillMaxWidth(), label = { Text("实际收益 (USD)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Button(onClick = {
                    val g = gas.toDoubleOrNull()
                    val r = reward.toDoubleOrNull()
                    val bill = activeBill
                    if (g != null && r != null && g >= 0 && r >= 0 && selectedProject.isNotBlank() && bill != null && !saving) {
                        saving = true
                        scope.launch {
                            val done = CacheStore.confirmPendingBill(bill.id, selectedProject, projects.firstOrNull { it.officialIdentifier() == selectedProject }?.name ?: "空投项目", g, r)
                            saving = false
                            if (done) { sheet = false; activeBill = null }
                        }
                    }
                }, enabled = !saving && gas.toDoubleOrNull()?.let { it >= 0 } == true && reward.toDoubleOrNull()?.let { it >= 0 } == true && selectedProject.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    shape = RoundedCornerShape(13.dp)) { Text(if (saving) "正在入账…" else "确认入账", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (newProjectDialog) AlertDialog(onDismissRequest = { newProjectDialog = false }, title = { Text("新建记账项目") },
        text = { OutlinedTextField(newProjectName, { newProjectName = it }, label = { Text("项目名称") }, singleLine = true) },
        confirmButton = { TextButton(enabled = newProjectName.isNotBlank(), onClick = {
            val project = ProjectDto(UUID.randomUUID().toString(), newProjectName.trim(), "本地盈亏记账项目", "tracking")
            projects = projects + project
            selectedProject = project.officialIdentifier()
            newProjectName = ""
            newProjectDialog = false
            scope.launch {
                val existing = CacheStore.readLocal("pnl_projects")?.let {
                    runCatching { Gson().fromJson<List<ProjectDto>>(it, object : TypeToken<List<ProjectDto>>() {}.type) }.getOrNull()
                }.orEmpty()
                CacheStore.writeLocal("pnl_projects", Gson().toJson(existing + project))
            }
        }) { Text("创建") } },
        dismissButton = { TextButton(onClick = { newProjectDialog = false }) { Text("取消") } })
}

@Composable
private fun PnlRecordRow(record: PnlRecordEntity, projects: List<ProjectDto>) {
    val name = record.projectCustomName.ifBlank { projects.firstOrNull { it.officialIdentifier() == record.projectOfficialId }?.name ?: "空投项目" }
    Row(Modifier.fillMaxWidth().background(AppColors.Background, RoundedCornerShape(11.dp)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Text("消耗 -$" + String.format(java.util.Locale.US, "%.2f", record.totalGasCostUsd) + " · 收益 +$" +
                String.format(java.util.Locale.US, "%.2f", record.totalRewardUsd), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Text((if (record.netProfitUsd >= 0) "+" else "-") + "$" + String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(record.netProfitUsd)),
            color = if (record.netProfitUsd >= 0) AppColors.Success else AppColors.Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
