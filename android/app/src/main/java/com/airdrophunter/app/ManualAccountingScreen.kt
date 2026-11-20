package com.airdrophunter.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.data.LedgerAssetAmount
import com.airdrophunter.app.data.LedgerAssetBreakdownCodec
import com.airdrophunter.app.data.PersonalLedgerWriteRequest
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.theme.AppDesignTokens
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Composable
fun ManualAccountingScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val repository = remember(application) { application.appContainer.accountingRepository }
    val scope = rememberCoroutineScope()
    var gasUsd by rememberSaveable { mutableStateOf("") }
    var rewardUsd by rememberSaveable { mutableStateOf("") }
    var projectName by rememberSaveable { mutableStateOf("") }
    var gasNative by rememberSaveable { mutableStateOf("") }
    var gasSymbol by rememberSaveable { mutableStateOf("") }
    var gasDecimals by rememberSaveable { mutableStateOf("") }
    var rewardNative by rememberSaveable { mutableStateOf("") }
    var rewardSymbol by rememberSaveable { mutableStateOf("") }
    var rewardDecimals by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var showNativeDetails by rememberSaveable { mutableStateOf(false) }

    val gas = gasUsd.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    val reward = rewardUsd.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    fun validAsset(amount: String, symbol: String): Boolean =
        (amount.isBlank() && symbol.isBlank()) ||
            (amount.toBigDecimalOrNull()?.let { it.signum() >= 0 } == true && symbol.isNotBlank())

    val nativeValid = validAsset(gasNative, gasSymbol) && validAsset(rewardNative, rewardSymbol)
    BackHandler { if (!saving) onBack() }

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(AppDesignTokens.Spacing.Xxl.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "记录一次交互",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "填写实际发生的 Gas 支出和收益，保存后进入个人账本。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AccountingFormSection(title = "基础信息", description = "项目可不关联，先把真实账目记录下来。") {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("归属项目（选填）") },
                    placeholder = { Text("例如：Linea、Scroll") },
                    singleLine = true,
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Sm.dp)
                )
            }

            AccountingFormSection(title = "费用与收益", description = "金额统一按 USD 记录；没有收益请填写 0。") {
                OutlinedTextField(
                    value = gasUsd,
                    onValueChange = { gasUsd = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gas 支出（USD）*") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Sm.dp),
                    supportingText = { if (gasUsd.isNotBlank() && gas == null) Text("请输入有效的非负数字") }
                )
                OutlinedTextField(
                    value = rewardUsd,
                    onValueChange = { rewardUsd = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("实际收益（USD）*") },
                    placeholder = { Text("没有收益请填 0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Sm.dp),
                    supportingText = { if (rewardUsd.isNotBlank() && reward == null) Text("请输入有效的非负数字") }
                )
            }

            AccountingFormSection(
                title = "原生币种明细",
                description = "可选填写具体币种和数量，便于后续核对。"
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "补充链上资产数量",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showNativeDetails = !showNativeDetails }) {
                        Text(if (showNativeDetails) "收起" else "展开")
                        Icon(
                            if (showNativeDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
                if (showNativeDetails) {
                    Text("Gas 原生金额", style = MaterialTheme.typography.labelLarge, color = AppColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            gasNative, { gasNative = it }, Modifier.weight(1.2f),
                            label = { Text("数量") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            gasSymbol, { gasSymbol = it.uppercase(Locale.US) }, Modifier.weight(0.8f),
                            label = { Text("币种") }, singleLine = true
                        )
                    }
                    OutlinedTextField(
                        gasDecimals, { gasDecimals = it.filter(Char::isDigit).take(2) },
                        Modifier.fillMaxWidth(), label = { Text("精度（选填）" ) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("收益原生金额", style = MaterialTheme.typography.labelLarge, color = AppColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            rewardNative, { rewardNative = it }, Modifier.weight(1.2f),
                            label = { Text("数量") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            rewardSymbol, { rewardSymbol = it.uppercase(Locale.US) }, Modifier.weight(0.8f),
                            label = { Text("币种") }, singleLine = true
                        )
                    }
                    OutlinedTextField(
                        rewardDecimals, { rewardDecimals = it.filter(Char::isDigit).take(2) },
                        Modifier.fillMaxWidth(), label = { Text("精度（选填）") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp)
            ) {
                Text(
                    "无需连接钱包或填写交易哈希。本记录保存到你的个人账本，不会自动公开个人明细。",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 4.dp
        ) {
            Button(
                onClick = {
                    val safeGas = gas ?: return@Button
                    val safeReward = reward ?: return@Button
                    if (saving || !nativeValid) return@Button
                    val now = Instant.now().toString()
                    val assets = buildList {
                        if (gasNative.isNotBlank()) add(LedgerAssetAmount("gas", gasNative.trim(), gasSymbol.trim(), gasDecimals.toIntOrNull()))
                        if (rewardNative.isNotBlank()) add(LedgerAssetAmount("reward", rewardNative.trim(), rewardSymbol.trim(), rewardDecimals.toIntOrNull()))
                    }
                    val entry = PersonalLedgerWriteRequest(
                        client_record_id = UUID.randomUUID().toString(),
                        project_id = null,
                        project_name = projectName.trim().ifBlank { "未关联项目" },
                        chain = "manual",
                        wallet_address = "",
                        tx_hash = "",
                        interaction_method = "手动记账",
                        expected_gas_cost_usd = null,
                        expected_reward_usd = null,
                        actual_gas_cost_usd = safeGas,
                        actual_reward_usd = safeReward,
                        occurred_at = now,
                        extra = buildMap {
                            put("record_type", "manual")
                            LedgerAssetBreakdownCodec.encode(assets)?.let { put("asset_breakdown_json", it) }
                            if (projectName.isNotBlank()) put("project_name_manual", projectName.trim())
                        }
                    )
                    saving = true
                    scope.launch {
                        val saved = runCatching { repository.saveManualLedgerEntry(entry) }.getOrDefault(false)
                        saving = false
                        if (saved) {
                            Toast.makeText(context, "已保存到个人账本", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !saving && gas != null && reward != null && nativeValid,
                modifier = Modifier.fillMaxWidth().height(AppDesignTokens.Control.ButtonHeight.dp),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Sm.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) "保存中…" else "保存记账")
            }
        }
    }
}

@Composable
private fun AccountingFormSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                if (!description.isNullOrBlank()) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                content()
            }
        )
    }
}
