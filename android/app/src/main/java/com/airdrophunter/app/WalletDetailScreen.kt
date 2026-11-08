package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.UnifiedToken
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.ui.theme.AppColors
import java.text.DecimalFormat

@Composable
fun WalletDetailScreen(
    wallet: WalletDto,
    onBack: () -> Unit,
    onOpenBrowser: (String, WalletDto, List<WalletDto>, String) -> Unit,
    wallets: List<WalletDto>,
    vm: WalletDetailViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val walletDetailViewModel: WalletDetailViewModel = vm ?: viewModel(factory = WalletDetailViewModelFactory(application.appContainer.assetRepository))
    val state by walletDetailViewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var selectedToken by remember { mutableStateOf<UnifiedToken?>(null) }
    LaunchedEffect(wallet.address, wallet.chain) { walletDetailViewModel.loadWallet(wallet.address, wallet.chain) }
    DisposableEffect(walletDetailViewModel) { onDispose { walletDetailViewModel.clear() } }

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selectedToken != null) selectedToken = null else onBack() }) {
                Icon(Icons.Default.ArrowBack, "返回", tint = AppColors.TextPrimary)
            }
            if (selectedToken == null) {
                DynamicChainIcon(wallet.ecosystem, Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(wallet.wallet_name.ifBlank { "钱包资产" }, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(shortAddress(wallet.address), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFE8F3F1)), contentAlignment = Alignment.Center) {
                    Text(selectedToken!!.symbol.take(1), color = AppColors.Primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(selectedToken!!.name + " (" + selectedToken!!.symbol + ")", Modifier.weight(1f), color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(wallet.address)) }) {
                Icon(Icons.Default.ContentCopy, "复制地址", tint = AppColors.Primary)
            }
        }
        if (selectedToken == null) {
            UnifiedAssetContent(
                assets = state.assets,
                isLoading = state.isLoading,
                errorMessage = state.errorMessage,
                onRefresh = walletDetailViewModel::refresh,
                onTokenClick = { selectedToken = it },
                modifier = Modifier.weight(1f)
            )
        } else {
            TokenReadOnlyDetail(
                token = selectedToken!!,
                wallet = wallet,
                onCopy = { clipboard.setText(AnnotatedString(wallet.address)) },
                onExplorer = {
                    onOpenBrowser(explorerAddressUrl(wallet), wallet, wallets, "区块链浏览器")
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TokenReadOnlyDetail(
    token: UnifiedToken,
    wallet: WalletDto,
    onCopy: () -> Unit,
    onExplorer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 28.dp)) {
            Text("代币余额", color = AppColors.TextSecondary)
            Text(DecimalFormat("#,##0.########").format(token.quantity) + " " + token.symbol,
                color = AppColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("折合 USD", color = AppColors.TextSecondary)
            Text(token.usdValue?.let { "$ " + DecimalFormat("#,##0.00").format(it) } ?: "暂无可靠估值",
                color = AppColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(token.priceUsd?.let { "单价 $ " + DecimalFormat("#,##0.########").format(it) } ?: "单价暂无",
                color = AppColors.TextSecondary)
            token.change24hPct?.let { change ->
                Text("24 小时 " + (if (change.signum() >= 0) "+" else "") + DecimalFormat("0.00").format(change) + "%",
                    color = if (change.signum() >= 0) Color(0xFF16834A) else Color(0xFFC34455))
            }
            Spacer(Modifier.height(18.dp))
            Text("只读资产信息 · 不支持链上交易", color = AppColors.TextHelper, style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider(color = AppColors.Border)
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Text("当前钱包地址", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(10.dp))
            Text(wallet.address, color = AppColors.TextSecondary)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("一键复制完整地址")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onExplorer, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                Text("前往区块链浏览器查看交易历史")
            }
        }
    }
}

private fun explorerAddressUrl(wallet: WalletDto): String {
    val address = wallet.address
    return when (wallet.chain.lowercase()) {
        "bnb chain" -> "https://bscscan.com/address/" + address
        "polygon" -> "https://polygonscan.com/address/" + address
        "arbitrum" -> "https://arbiscan.io/address/" + address
        "optimism" -> "https://optimistic.etherscan.io/address/" + address
        "base" -> "https://basescan.org/address/" + address
        "avalanche" -> "https://snowtrace.io/address/" + address
        "tron", "trc20" -> "https://tronscan.org/#/address/" + address
        "sol", "solana" -> "https://solscan.io/account/" + address
        "bitcoin" -> "https://mempool.space/address/" + address
        else -> "https://etherscan.io/address/" + address
    }
}
