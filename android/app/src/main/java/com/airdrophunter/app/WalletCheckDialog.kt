package com.airdrophunter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.WalletCheckResponse
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun WalletCheckDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<WalletCheckResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val canSubmit = address.trim().length > 20 && !loading

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text("钱包网络验真", color = AppColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        result = null
                        error = null
                    },
                    label = { Text("输入公开钱包地址") },
                    singleLine = true
                )
                Text("仅在后端进行公链网络验证。请勿输入私钥或助记词。",
                    color = AppColors.TextSecondary)
                error?.let { Text(it, color = AppColors.Danger) }
                result?.let { checked ->
                    Text(
                        checked.message,
                        color = if (checked.valid) AppColors.Success else AppColors.Danger,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (checked.valid) {
                        checked.chain?.let { Text("网络：$it", color = AppColors.TextSecondary) }
                        if (checked.eligibility_available && checked.eligible != null) {
                            Text(
                                if (checked.eligible) "已获取到项目资格信息" else "当前未满足项目资格",
                                color = AppColors.TextPrimary
                            )
                            checked.estimated_reward_usd?.let {
                                Text("预估奖励：$%.2f".format(it), color = AppColors.TextPrimary)
                            }
                        } else {
                            Text("地址已完成网络验证；尚未接入具体项目的资格规则，暂不显示奖励金额。",
                                color = AppColors.TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        result = null
                        try {
                            result = ApiClient.service.checkWallet(address.trim())
                            if (result?.valid == false) error = result?.message
                        } catch (_: Exception) {
                            error = "服务器繁忙，请稍后重试"
                        } finally {
                            loading = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    disabledContainerColor = AppColors.Border
                )
            ) { Text(if (loading) "正在查询…" else "立即查询") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
