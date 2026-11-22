package com.airdrophunter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppTextButton
import kotlinx.coroutines.launch

@Composable
fun WalletCheckDialog(
    onDismiss: () -> Unit,
    onOpenBrowser: (url: String, selectedWallet: WalletDto, wallets: List<WalletDto>, title: String) -> Unit,
    isGuest: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<ProjectDto>>(emptyList()) }
    var wallets by remember { mutableStateOf<List<WalletDto>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<ProjectDto?>(null) }
    var selectedWallet by remember { mutableStateOf<WalletDto?>(null) }
    var projectExpanded by remember { mutableStateOf(false) }
    var walletExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isGuest) {
        try {
            if (isGuest) {
                val gson = com.google.gson.Gson()
                val projectType = object : com.google.gson.reflect.TypeToken<List<ProjectDto>>() {}.type
                val walletType = object : com.google.gson.reflect.TypeToken<List<WalletDto>>() {}.type
                projects = com.airdrophunter.app.data.CacheStore.readLocal("projects")?.let {
                    runCatching { gson.fromJson<List<ProjectDto>>(it, projectType) }.getOrNull()
                }.orEmpty()
                wallets = com.airdrophunter.app.data.CacheStore.readLocal("wallets")?.let {
                    runCatching { gson.fromJson<List<WalletDto>>(it, walletType) }.getOrNull()
                }.orEmpty()
            } else {
                projects = ApiClient.service.getProjects()
                wallets = ApiClient.service.getWallets()
            }
        } catch (_: Exception) { error = context.getString(R.string.loc_wallet_check_01) }
        finally { loading = false }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.loc_wallet_check_02), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loading) Text(stringResource(R.string.loc_wallet_check_03), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.loc_wallet_check_04), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Column {
                    AppButton(
                        text = selectedProject?.name ?: stringResource(R.string.loc_wallet_check_05),
                        onClick = { projectExpanded = true },
                        style = AppButtonStyle.SECONDARY
                    )
                    DropdownMenu(expanded = projectExpanded, onDismissRequest = { projectExpanded = false }) {
                        projects.forEach { project ->
                            DropdownMenuItem(text = { Text(project.name) }, onClick = {
                                selectedProject = project; projectExpanded = false; error = null
                            })
                        }
                    }
                }
                Text(stringResource(R.string.loc_wallet_check_06), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Column {
                    AppButton(
                        text = selectedWallet?.let { "${it.chain} · ${it.wallet_name} (${shortAddress(it.address)})" } ?: stringResource(R.string.loc_wallet_check_07),
                        onClick = { walletExpanded = true },
                        style = AppButtonStyle.SECONDARY,
                        fontSizeSp = 14
                    )
                    DropdownMenu(expanded = walletExpanded, onDismissRequest = { walletExpanded = false }) {
                        wallets.forEach { wallet ->
                            DropdownMenuItem(
                                text = { Text("${wallet.chain} · ${wallet.wallet_name} (${shortAddress(wallet.address)})") },
                                onClick = { selectedWallet = wallet; walletExpanded = false; error = null }
                            )
                        }
                    }
                }
                Text(stringResource(R.string.loc_wallet_check_08),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                if (projects.isEmpty() && !loading) Text(stringResource(R.string.loc_wallet_check_09), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (wallets.isEmpty() && !loading) Text(stringResource(R.string.loc_wallet_check_10), color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.loc_wallet_check_11),
                enabled = selectedProject != null && selectedWallet != null && !submitting && !loading,
                loading = submitting,
                onClick = {
                    val project = selectedProject ?: return@AppButton
                    val wallet = selectedWallet ?: return@AppButton
                    scope.launch {
                        submitting = true; error = null
                        try {
                            val verified = ApiClient.service.checkWallet(wallet.address)
                            if (!verified.valid) {
                                error = verified.message
                            } else {
                                val claimUrl = if (isGuest) project.claim_url else ApiClient.service.getClaimUrl(project.id, wallet.id).url
                                if (claimUrl.startsWith("https://")) {
                                    onOpenBrowser(claimUrl, wallet, wallets, project.name)
                                } else {
                                    error = context.getString(R.string.loc_wallet_check_12)
                                }
                            }
                        } catch (e: retrofit2.HttpException) {
                            error = if (e.code() == 409) "该项目尚未设置官方资格查询入口" else "服务器繁忙，请稍后重试"
                        } catch (_: Exception) {
                            error = context.getString(R.string.loc_wallet_check_13)
                        } finally { submitting = false }
                    }
                }
            )
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text(stringResource(R.string.loc_wallet_check_14)) } }
    )
}
