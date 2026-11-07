package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.AddressDetector
import com.airdrophunter.app.data.DetectedNetwork
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun CheckWalletScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    vm: CheckWalletViewModel = viewModel()
) {
    var address by remember { mutableStateOf("") }
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val detected = remember(address) { AddressDetector.detect(address) }

    DisposableEffect(vm) { onDispose { vm.clearResult() } }

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = AppColors.Primary) }
            Text("安全查钱包", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("输入公开钱包地址", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it; vm.clearResult() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("粘贴 0x / T / Solana 地址") },
                        trailingIcon = {
                            IconButton(onClick = {
                                val pasted = clipboard.getText()?.text.orEmpty()
                                if (pasted.isNotBlank()) { address = pasted.trim(); vm.clearResult() }
                            }) { Icon(Icons.Default.ContentPaste, "粘贴地址", tint = AppColors.Primary) }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    val recognized = detected != DetectedNetwork.UNKNOWN
                    Text(
                        if (recognized) "自动识别：${detected.label}" else if (address.isBlank()) "支持 EVM、TRON、Solana 地址" else "地址格式错误，暂无法识别网络",
                        color = if (recognized) AppColors.Success else AppColors.TextHelper,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { vm.query(address) },
                        enabled = recognized && !state.isLoading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, disabledContainerColor = AppColors.Border),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("立即查询")
                    }
                    Text(
                        "仅查询公开链上数据，不连接钱包、不请求签名，也不会保存输入地址。",
                        color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (state.errorMessage != null) {
            Text(state.errorMessage.orEmpty(), Modifier.padding(20.dp), color = AppColors.Danger)
        }
        if (state.assets != null || state.isLoading) {
            UnifiedAssetContent(
                assets = state.assets,
                isLoading = state.isLoading,
                errorMessage = state.errorMessage,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
