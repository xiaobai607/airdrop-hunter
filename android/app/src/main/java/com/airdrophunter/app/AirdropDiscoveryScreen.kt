package com.airdrophunter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.AirdropWriteRequest

@Composable
fun AirdropDiscoveryScreen(
    modifier: Modifier = Modifier,
    viewModel: AirdropsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadAirdrops() }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("发现真实空投机会", style = MaterialTheme.typography.headlineSmall)
        Text(
            "这里只展示已保存的活动记录。请核对官方链接；未验证记录不会被标记为官方确认。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.loadAirdrops() }, enabled = !state.isLoading) {
                Text("刷新")
            }
            Button(onClick = { showAddDialog = true }, enabled = !state.isSaving) {
                Text("添加空投")
            }
        }
        if (state.message != null) {
            Text(state.message!!, color = MaterialTheme.colorScheme.primary)
        }
        if (state.error != null) {
            Text("读取失败：${state.error}", color = MaterialTheme.colorScheme.error)
        }
        if (state.isLoading && state.items.isEmpty()) {
            CircularProgressIndicator()
        } else if (state.items.isEmpty()) {
            Text("目前还没有空投记录。可以先添加你找到的项目和官方来源链接。")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            if (item.description.isNotBlank()) Text(item.description)
                            Text("状态：${airdropStatusLabel(item.status)} · 链：${item.chain.ifBlank { "未填写" }}")
                            if (item.reward_details.isNotBlank()) {
                                Text("奖励：${item.reward_details}")
                            }
                            if (item.eligibility.isNotBlank()) {
                                Text("参与条件：${item.eligibility}")
                            }
                            Text(
                                "数据来源：${item.source_name} · ${verificationLabel(item.verification_status)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (item.official_url.isNotBlank()) {
                                Text("官方链接：${item.official_url}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (item.source_url.isNotBlank()) {
                                Text("来源链接：${item.source_url}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAirdropDialog(
            saving = state.isSaving,
            onDismiss = { showAddDialog = false },
            onSave = { request ->
                viewModel.createAirdrop(request)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddAirdropDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (AirdropWriteRequest) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var chain by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("") }
    var eligibility by remember { mutableStateOf("") }
    var officialUrl by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加空投记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("项目名称 *") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("项目简介") })
                OutlinedTextField(chain, { chain = it }, label = { Text("所属链，例如 Base") }, singleLine = true)
                OutlinedTextField(reward, { reward = it }, label = { Text("奖励说明") })
                OutlinedTextField(eligibility, { eligibility = it }, label = { Text("参与条件") })
                OutlinedTextField(officialUrl, { officialUrl = it }, label = { Text("官方活动链接") })
                OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text("信息来源链接") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = {
                    onSave(
                        AirdropWriteRequest(
                            name = name.trim(),
                            description = description.trim(),
                            category = "other",
                            chain = chain.trim(),
                            status = "unknown",
                            reward_details = reward.trim(),
                            eligibility = eligibility.trim(),
                            official_url = officialUrl.trim(),
                            source_name = "手动录入",
                            source_url = sourceUrl.trim(),
                            source_type = "manual",
                            verification_status = if (sourceUrl.isBlank()) "unverified" else "source_linked"
                        )
                    )
                }
            ) { Text(if (saving) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}

private fun airdropStatusLabel(status: String): String = when (status) {
    "upcoming" -> "即将开始"
    "ongoing" -> "进行中"
    "ended" -> "已结束"
    else -> "待确认"
}

private fun verificationLabel(status: String): String = when (status) {
    "verified" -> "已核实"
    "source_linked" -> "已附来源，待核实"
    else -> "未核实"
}
