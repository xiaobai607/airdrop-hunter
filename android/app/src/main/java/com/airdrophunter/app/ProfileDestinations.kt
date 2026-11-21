package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airdrophunter.app.ui.theme.AppDesignTokens

enum class ProfileDestination(val titleRes: Int) {
    SETTINGS(R.string.profile_settings),
    SECURITY(R.string.profile_security),
    NOTIFICATIONS(R.string.profile_notifications)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDestinationDialog(destination: ProfileDestination, onDismiss: () -> Unit, onOpenLanguageSettings: () -> Unit = {}) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(destination.titleRes),
                            modifier = Modifier.semantics { heading() },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.pd_back), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (destination) {
                        ProfileDestination.SETTINGS -> SettingsDestinationContent(onOpenLanguageSettings)
                        ProfileDestination.SECURITY -> SecurityDestinationContent()
                        ProfileDestination.NOTIFICATIONS -> NotificationsDestinationContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDestinationContent(onOpenLanguageSettings: () -> Unit) {
    Text(stringResource(R.string.pd_preferences), modifier = Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    DestinationInfoRow(stringResource(R.string.pd_appearance), stringResource(R.string.pd_appearance_help))
    DestinationActionRow(stringResource(R.string.pd_language), stringResource(R.string.pd_language_help), onOpenLanguageSettings)
    DestinationInfoRow(stringResource(R.string.pd_browser), stringResource(R.string.pd_browser_help))
    Text(stringResource(R.string.pd_data_diagnostics), modifier = Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    DestinationInfoRow(stringResource(R.string.pd_cache), stringResource(R.string.pd_cache_help))
    DestinationInfoRow(stringResource(R.string.pd_diagnostics), stringResource(R.string.pd_diagnostics_help))
}

@Composable
private fun SecurityDestinationContent() {
    val accounts by WalletConnectSessionDelegate.accounts.collectAsState()
    val origins by DappOriginPermissionStore.authorizedOrigins.collectAsState()

    DestinationInfoCard {
        Text(stringResource(R.string.pd_security_boundary), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.pd_security_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }

    Text(stringResource(R.string.pd_external_sessions), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (accounts.isEmpty()) {
        DestinationInfoCard {
            Text(stringResource(R.string.pd_no_sessions), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        accounts.forEach { account ->
            DestinationInfoCard {
                Text(account.walletLabel, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.pd_network, account.chainId), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(account.address, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    DestinationInfoCard {
        Text(
            stringResource(R.string.pd_external_manage),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.pd_site_permissions), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (origins.isNotEmpty()) TextButton(onClick = { DappOriginPermissionStore.revokeAll() }) { Text(stringResource(R.string.pd_revoke_all)) }
    }
    if (origins.isEmpty()) {
        DestinationInfoCard {
            Text(stringResource(R.string.pd_no_sites), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        origins.keys.sorted().forEach { origin ->
            DestinationInfoCard {
                Text(origin, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.pd_permission_lifetime), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { DappOriginPermissionStore.revoke(origin) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.pd_revoke_site), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    Text(
        stringResource(R.string.pd_permission_note),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun NotificationsDestinationContent() {
    DestinationInfoCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.pd_no_notifications), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.pd_notifications_help),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(stringResource(R.string.pd_unread_count, 0), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DestinationInfoRow(title: String, description: String) {
    DestinationInfoCard {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.pd_in_development), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DestinationActionRow(title: String, description: String, onClick: () -> Unit) {
    DestinationInfoCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClick) { Text(stringResource(R.string.pd_language)) }
        }
    }
}

@Composable
private fun DestinationInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            content = content
        )
    }
}
