package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun TronEnergyAssistantScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val normalized = address.trim()
    val valid = normalized.matches(Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$"))

    Column(
        modifier.fillMaxSize().background(AppColors.Background).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = AppColors.TextPrimary) }
            Text("TRON 能量租赁助手", color = AppColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.Bolt, null, tint = AppColors.Primary, modifier = Modifier.size(34.dp))
                Text("准备租赁 TRON 能量", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("请输入你自己的 TRON 转出地址。打开租赁平台后，可通过底部工具栏一键复制该地址。",
                    color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; attempted = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("请输入 TRON 转出地址 (T开头)") },
                    placeholder = { Text("T...") },
                    singleLine = true,
                    isError = attempted && !valid
                )
                if (attempted && !valid) {
                    Text("地址格式不正确，请输入有效的 TRON Base58 地址。", color = AppColors.Danger,
                        style = MaterialTheme.typography.bodySmall)
                }
                Surface(
                    color = Color(0xFFFFEEEE),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "⚠️ 安全提示：仅用于租赁能量，切勿输入对方收款地址！",
                        Modifier.padding(14.dp),
                        color = Color(0xFFB42318),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = {
                        attempted = true
                        if (valid) onOpenBrowser(normalized)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("去租赁能量", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
