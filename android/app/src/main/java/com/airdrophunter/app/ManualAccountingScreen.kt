package com.airdrophunter.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.data.AccountingRepository
import com.airdrophunter.app.data.LedgerAssetAmount
import com.airdrophunter.app.data.LedgerAssetBreakdownCodec
import com.airdrophunter.app.data.PersonalLedgerWriteRequest
import com.airdrophunter.app.ui.theme.AppColors
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
    var gasUsd by remember { mutableStateOf("") }
    var rewardUsd by remember { mutableStateOf("") }
    var projectName by remember { mutableStateOf("") }
    var gasNative by remember { mutableStateOf("") }
    var gasSymbol by remember { mutableStateOf("") }
    var gasDecimals by remember { mutableStateOf("") }
    var rewardNative by remember { mutableStateOf("") }
    var rewardSymbol by remember { mutableStateOf("") }
    var rewardDecimals by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val gas = gasUsd.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    val reward = rewardUsd.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    fun validAsset(amount: String, symbol: String): Boolean =
        (amount.isBlank() && symbol.isBlank()) ||
            (amount.toBigDecimalOrNull()?.let { it.signum() >= 0 } == true && symbol.isNotBlank())

    val nativeValid = validAsset(gasNative, gasSymbol) && validAsset(rewardNative, rewardSymbol)
    BackHandler { if (!saving) onBack() }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("记一笔", color = AppColors.TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("手动记录实际支出与收益", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            border = BorderStroke(1.dp, AppColors.Border),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = gasUsd, onValueChange = { gasUsd = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Gas 支出（USD）") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = rewardUsd, onValueChange = { rewardUsd = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("实际收益（USD，零收益填 0）") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = projectName, onValueChange = { projectName = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("归属项目（可选）") },
                    singleLine = true, supportingText = { Text("不选择项目也可以直接保存。") }
                )
            }
        }
        Text("原生币种明细（可选，不同币种分别记录）", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            border = BorderStroke(1.dp, AppColors.Border),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gas 原生金额", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(gasNative, { gasNative = it }, Modifier.weight(1.2f), label = { Text("金额") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(gasSymbol, { gasSymbol = it.uppercase(Locale.US) }, Modifier.weight(0.8f), label = { Text("币种") }, singleLine = true)
                }
                OutlinedTextField(gasDecimals, { gasDecimals = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(), label = { Text("精度（可选）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                HorizontalDivider(color = AppColors.Border)
                Text("收益原生金额", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(rewardNative, { rewardNative = it }, Modifier.weight(1.2f), label = { Text("金额") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(rewardSymbol, { rewardSymbol = it.uppercase(Locale.US) }, Modifier.weight(0.8f), label = { Text("币种") }, singleLine = true)
                }
                OutlinedTextField(rewardDecimals, { rewardDecimals = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(), label = { Text("精度（可选）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
        Text("无需连接钱包，也不需要填写交易哈希。记录仅进入你的个人账本，不会自动贡献到社区统计。", color = AppColors.TextHelper, style = MaterialTheme.typography.bodySmall)
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (saving) "保存中…" else "保存记账")
        }
    }
}
