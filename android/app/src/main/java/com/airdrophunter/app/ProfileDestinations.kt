package com.airdrophunter.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airdrophunter.app.ui.components.BaseCard
import com.airdrophunter.app.ui.theme.AppDesignTokens

enum class ProfileDestination(val titleRes: Int) {
    SETTINGS(R.string.profile_settings),
    SECURITY(R.string.profile_security),
    NOTIFICATIONS(R.string.profile_notifications)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDestinationDialog(destination: ProfileDestination, appVersion: String = "", onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(destination.titleRes),
                            modifier = Modifier.semantics { heading() },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.loc_profile_dest_21), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    when (destination) {
                        ProfileDestination.SETTINGS -> SettingsDestinationContent(appVersion)
                        ProfileDestination.SECURITY -> SecurityDestinationContent()
                        ProfileDestination.NOTIFICATIONS -> NotificationsDestinationContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDestinationContent(appVersion: String) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            context.getString(R.string.loc_profile_dest_01),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        DestinationInfoCard {
            SettingsInfoRow(
                icon = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = context.getString(R.string.loc_profile_dest_02),
                description = context.getString(R.string.loc_profile_dest_03)
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            context.getString(R.string.loc_profile_dest_04),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        DestinationInfoCard {
            SettingsInfoRow(
                icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "Airdrop Hunter",
                description = context.getString(R.string.loc_profile_dest_05),
                trailing = "v$appVersion"
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    trailing: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        if (trailing != null) {
            Text(
                trailing,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SecurityDestinationContent() {
    val context = LocalContext.current
    val accounts by WalletConnectSessionDelegate.accounts.collectAsState()
    val origins by DappOriginPermissionStore.authorizedOrigins.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DestinationSectionTitle(context.getString(R.string.loc_profile_dest_06))
            DestinationInfoCard {
                Text(
                    context.getString(R.string.loc_profile_dest_07),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    context.getString(R.string.loc_profile_dest_08),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DestinationSectionTitle(context.getString(R.string.loc_profile_dest_09))
            if (accounts.isEmpty()) {
                DestinationInfoCard {
                    Text(
                        context.getString(R.string.loc_profile_dest_10),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                accounts.forEach { account ->
                    DestinationInfoCard {
                        Text(
                            account.walletLabel,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            context.getString(R.string.loc_profile_chain, account.chainId),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Text(
                            account.address,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
            DestinationInfoCard {
                Text(
                    context.getString(R.string.loc_profile_dest_11),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DestinationSectionTitle(context.getString(R.string.loc_profile_dest_12), Modifier.weight(1f))
                if (origins.isNotEmpty()) {
                    TextButton(onClick = { DappOriginPermissionStore.revokeAll() }) {
                        Text(context.getString(R.string.loc_profile_dest_13), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (origins.isEmpty()) {
                DestinationInfoCard {
                    Text(
                        context.getString(R.string.loc_profile_dest_14),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                origins.keys.sorted().forEach { origin ->
                    DestinationInfoCard {
                        Text(
                            origin,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            context.getString(R.string.loc_profile_dest_15),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        TextButton(
                            onClick = { DappOriginPermissionStore.revoke(origin) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(context.getString(R.string.loc_profile_dest_16), color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        }
                    }
                }
            }
            Text(
                context.getString(R.string.loc_profile_dest_17),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun DestinationSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun NotificationsDestinationContent() {
    val context = LocalContext.current
    DestinationInfoCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                context.getString(R.string.loc_profile_dest_18),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                context.getString(R.string.loc_profile_dest_19),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Text(
                context.getString(R.string.loc_profile_dest_20),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DestinationInfoCard(content: @Composable ColumnScope.() -> Unit) {
    BaseCard {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}
