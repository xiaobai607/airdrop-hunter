package com.airdrophunter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.WalletCheckResponse
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WalletCheckDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val networkError = stringResource(R.string.network_error)
    var address by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<WalletCheckResponse?>(null) }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text(stringResource(R.string.wallet_title), color = AppColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = address, onValueChange = { address = it },
                    label = { Text(stringResource(R.string.wallet_address_hint)) }, singleLine = true)
                Text(stringResource(R.string.wallet_disclaimer), color = AppColors.TextSecondary)
                result?.let {
                    Text(if (it.eligible) stringResource(R.string.wallet_eligible) else stringResource(R.string.wallet_not_eligible),
                        color = AppColors.Primary, fontWeight = FontWeight.Bold)
                    Text(String.format(Locale.US, "≈ $%.2f", it.estimated_reward_usd), color = AppColors.TextPrimary)
                }
                if (error) Text(networkError, color = AppColors.Danger)
            }
        },
        confirmButton = {
            TextButton(enabled = address.length >= 20 && !loading, onClick = {
                scope.launch {
                    loading = true
                    error = false
                    try { result = ApiClient.service.checkWallet(address.trim()) }
                    catch (_: Exception) { error = true }
                    loading = false
                }
            }) { Text(if (loading) stringResource(R.string.loading) else stringResource(R.string.check_now)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}