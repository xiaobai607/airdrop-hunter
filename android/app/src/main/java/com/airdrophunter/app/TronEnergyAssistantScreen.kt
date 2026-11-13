package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BaseCard {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "先确认转出地址，再前往租赁平台",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    "请输入你自己的 TRON 转出地址。打开租赁平台后，可通过底部工具栏一键复制该地址。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "TRON 转出地址",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AppTextField(
                        value = address,
                        onValueChange = { address = it; attempted = false },
                        placeholder = "T 开头的 Base58 地址",
                        isError = attempted && !valid
                    )
                    if (attempted && !valid) {
                        Text(
                            "地址格式不正确，请输入有效的 TRON Base58 地址。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Surface(
                    color = warningSoft,
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = warning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "安全提示：仅填写你自己的转出地址，切勿输入对方收款地址。",
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
