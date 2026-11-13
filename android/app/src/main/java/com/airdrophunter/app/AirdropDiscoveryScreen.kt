package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.AppChip
import com.airdrophunter.app.ui.components.AppTextField
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.AirdropWriteRequest

@Composable
fun AirdropDiscoveryScreen(
    modifier: Modifier = Modifier,
    vm: AirdropsViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val airdropsViewModel: AirdropsViewModel = vm ?: viewModel(
        factory = AirdropsViewModelFactory(application.appContainer.airdropsRepository)
    )
    val state by airdropsViewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { airdropsViewModel.loadAirdrops() }

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("空投记录", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(
            "这里只展示已保存的活动记录。请核对官方链接；未验证记录不会被标记为官方确认。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                text = "刷新", onClick = { airdropsViewModel.loadAirdrops() },
                enabled = !state.isLoading, loading = state.isLoading,
                style = AppButtonStyle.SECONDARY, fullWidth = false, heightDp = 44, fontSizeSp = 14
            )
            AppButton(
                text = "添加空投", onClick = { showAddDialog = true },
                enabled = !state.isSaving, fullWidth = false, heightDp = 44, fontSizeSp = 14
            )
        }
        if (state.message != null) {
            Text(state.message!!, color = MaterialTheme.colorScheme.primary)
        }
        if (state.error != null) {
            Text("读取失败：${state.error}", color = MaterialTheme.colorScheme.error)
        }
        if (state.isLoading && state.items.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.items.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (state.error != null) "暂时无法读取空投记录" else "还没有空投记录", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (state.error != null) "请检查网络后重试；已保存的记录不会因此被删除。" else "添加你找到的项目和官方来源链接，集中管理参与条件与奖励信息。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.error != null) AppButton("重新加载", onClick = { airdropsViewModel.loadAirdrops() }, fullWidth = false)
                    else AppButton("添加第一条记录", onClick = { showAddDialog = true }, fullWidth = false)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Surface(color = if (item.verification_status == "verified") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(999.dp)) {
                                    Text(verificationLabel(item.verification_status), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (item.verification_status == "verified") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppChip(text = airdropStatusLabel(item.status), selected = false, onClick = {})
                                AppChip(text = item.chain.ifBlank { "链未填写" }, selected = false, onClick = {})
                            }
                            if (item.reward_details.isNotBlank()) Text("奖励 · ${item.reward_details}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            if (item.eligibility.isNotBlank()) Text("参与条件 · ${item.eligibility}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("来源 · ${item.source_name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                airdropsViewModel.createAirdrop(request)
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
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("添加空投记录", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppTextField(name, { name = it }, placeholder = "项目名称 *")
                AppTextField(description, { description = it }, placeholder = "项目简介", singleLine = false)
                AppTextField(chain, { chain = it }, placeholder = "所属链，例如 Base")
                AppTextField(reward, { reward = it }, placeholder = "奖励说明", singleLine = false)
                AppTextField(eligibility, { eligibility = it }, placeholder = "参与条件", singleLine = false)
                AppTextField(officialUrl, { officialUrl = it }, placeholder = "官方活动链接")
                AppTextField(sourceUrl, { sourceUrl = it }, placeholder = "信息来源链接")
            }
        },
        confirmButton = {
            AppButton(
                text = "保存",
                enabled = name.isNotBlank() && !saving,
                loading = saving,
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
            )
        },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
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
