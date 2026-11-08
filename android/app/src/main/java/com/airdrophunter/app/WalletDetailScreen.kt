package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun WalletDetailScreen(
    wallet: WalletDto,
    onBack: () -> Unit,
    vm: WalletDetailViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val walletDetailViewModel: WalletDetailViewModel = vm ?: viewModel(factory = WalletDetailViewModelFactory(application.appContainer.assetRepository, application.appContainer.apiService))
    val state by walletDetailViewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(wallet.address, wallet.ecosystem) { walletDetailViewModel.loadWallet(wallet.address, wallet.ecosystem) }
    DisposableEffect(walletDetailViewModel) { onDispose { walletDetailViewModel.clear() } }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = AppColors.TextPrimary) }
            Column(Modifier.weight(1f)) {
                Text(wallet.wallet_name.ifBlank { "钱包资产" }, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(shortAddress(wallet.address), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(wallet.address)) }) {
                Icon(Icons.Default.ContentCopy, "复制地址", tint = AppColors.Primary)
            }
        }
        UnifiedAssetContent(
            assets = state.assets,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onRefresh = walletDetailViewModel::refresh,
            transactions = state.transactions,
            transactionsLoading = state.transactionsLoading,
            transactionsError = state.transactionsError,
            transactionSupportMessage = state.transactionSupportMessage,
            modifier = Modifier.weight(1f)
        )
    }
}
