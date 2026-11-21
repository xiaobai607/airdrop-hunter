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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
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
                            context.getString(R.string.tron_energy_heading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    context.getString(R.string.tron_energy_help),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        context.getString(R.string.tron_energy_address),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AppTextField(
                        value = address,
                        onValueChange = { address = it; attempted = false },
                        placeholder = context.getString(R.string.tron_energy_hint),
                        isError = attempted && !valid
                    )
                    if (attempted && !valid) {
                        Text(
                            context.getString(R.string.tron_energy_invalid),
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
                            context.getString(R.string.tron_energy_safety),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                AppButton(
                    text = context.getString(R.string.tron_energy_action),
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
