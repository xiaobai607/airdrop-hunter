package com.airdrophunter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun WalletCheckDialog(
    onDismiss: () -> Unit,
    onOpenBrowser: (url: String, address: String, title: String) -> Unit
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

    LaunchedEffect(Unit) {
        try {
            projects = ApiClient.service.getProjects()
            wallets = ApiClient.service.getWallets()
        } catch (_: Exception) { error = "请先登录并确保网络连接正常" }
        finally { loading = false }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text("空投资格查询", color = AppColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loading) Text("正在读取项目和钱包…", color = AppColors.TextSecondary)
                Text("选择项目", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
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
                error?.let { Text(it, color = AppColors.Danger) }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedProject != null && selectedWallet != null && !submitting && !loading,
                onClick = {
                    val project = selectedProject ?: return@Button
                    val wallet = selectedWallet ?: return@Button
                    scope.launch {
                        submitting = true; error = null
                        try {
                            val verified = ApiClient.service.checkWallet(wallet.address)
                            if (!verified.valid) {
                                error = verified.message
                            } else {
                                val claim = ApiClient.service.getClaimUrl(project.id, wallet.id)
                                onOpenBrowser(claim.url, wallet.address, project.name)
                            }
                        } catch (e: retrofit2.HttpException) {
                            error = if (e.code() == 409) "该项目尚未设置官方资格查询入口" else "服务器繁忙，请稍后重试"
                        } catch (_: Exception) {
                            error = "服务器繁忙，请稍后重试"
                        } finally { submitting = false }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, disabledContainerColor = AppColors.Border)
            ) { Text(if (submitting) "正在验证…" else "立即查询") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
