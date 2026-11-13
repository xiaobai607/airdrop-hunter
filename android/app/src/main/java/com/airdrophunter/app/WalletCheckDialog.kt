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
        } catch (_: Exception) { error = "读取数据失败，请稍后重试" }
        finally { loading = false }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("空投资格查询", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loading) Text("正在读取项目和钱包…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("选择项目", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Column {
                    OutlinedButton(onClick = { projectExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedProject?.name ?: "请选择已添加的项目", color = AppColors.TextPrimary)
                    }
                    DropdownMenu(expanded = projectExpanded, onDismissRequest = { projectExpanded = false }) {
                        projects.forEach { project ->
                            DropdownMenuItem(text = { Text(project.name) }, onClick = {
                                selectedProject = project; projectExpanded = false; error = null
                            })
                        }
                    }
                }
                Text("选择钱包", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Column {
                    OutlinedButton(onClick = { walletExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedWallet?.let { "${it.chain} · ${it.wallet_name} (${shortAddress(it.address)})" } ?: "请选择已保存的钱包",
                            color = AppColors.TextPrimary)
                    }
                    DropdownMenu(expanded = walletExpanded, onDismissRequest = { walletExpanded = false }) {
                        wallets.forEach { wallet ->
                            DropdownMenuItem(
                                text = { Text("${wallet.chain} · ${wallet.wallet_name} (${shortAddress(wallet.address)})") },
                                onClick = { selectedWallet = wallet; walletExpanded = false; error = null }
                            )
                        }
                    }
                }
                Text("App 会先请求公链验证地址，再打开项目已保存的 HTTPS 资格入口。资格结果由项目方网页判定。",
                    color = AppColors.TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                if (projects.isEmpty() && !loading) Text("暂无项目，请先在项目管理中添加项目并填写资格入口。", color = AppColors.TextSecondary)
                if (wallets.isEmpty() && !loading) Text("暂无钱包，请先添加公开钱包地址。", color = AppColors.TextSecondary)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            AppButton(
                text = "立即查询",
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
                                    error = "该项目尚未填写官方资格查询入口"
                                }
                            }
                        } catch (e: retrofit2.HttpException) {
                            error = if (e.code() == 409) "该项目尚未设置官方资格查询入口" else "服务器繁忙，请稍后重试"
                        } catch (_: Exception) {
                            error = "服务器繁忙，请稍后重试"
                        } finally { submitting = false }
                    }
                }
            )
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text("取消") } }
    )
}
