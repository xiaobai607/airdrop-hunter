package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppTextField
import com.airdrophunter.app.ui.components.BaseCard
import com.airdrophunter.app.ui.theme.AppDesignTokens

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
    val warning = if (isSystemInDarkTheme()) AppDesignTokens.Dark.Warning else AppDesignTokens.Light.Warning
    val warningSoft = if (isSystemInDarkTheme()) AppDesignTokens.Dark.WarningSoft else AppDesignTokens.Light.WarningSoft

    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BaseCard {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Text(
                    "请输入你自己的 TRON 转出地址。打开租赁平台后，可通过底部工具栏一键复制该地址。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
                AppTextField(
                    value = address,
                    onValueChange = { address = it; attempted = false },
                    placeholder = "请输入 TRON 转出地址（T 开头）",
                    isError = attempted && !valid
                )
                if (attempted && !valid) {
                    Text(
                        "地址格式不正确，请输入有效的 TRON Base58 地址。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(
                    color = warningSoft,
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = warning, modifier = Modifier.size(18.dp))
                        Text(
                            "安全提示：仅用于租赁能量，切勿输入对方收款地址。",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                AppButton(
                    text = "去租赁能量",
                    onClick = {
                        attempted = true
                        if (valid) onOpenBrowser(normalized)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
