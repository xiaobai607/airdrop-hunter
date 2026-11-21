package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.AppTextField
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.AirdropWriteRequest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AirdropDiscoveryScreen(
    modifier: Modifier = Modifier,
    vm: AirdropsViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val airdropsViewModel: AirdropsViewModel = vm ?: viewModel(
        factory = AirdropsViewModelFactory(application.appContainer.airdropsRepository)
    )
    val state by airdropsViewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { airdropsViewModel.loadAirdrops() }

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.airdrop_records_title), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(
            stringResource(R.string.airdrop_records_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                text = stringResource(R.string.airdrop_records_refresh), onClick = { airdropsViewModel.loadAirdrops() },
                enabled = !state.isLoading, loading = state.isLoading,
                style = AppButtonStyle.SECONDARY, fullWidth = false, heightDp = 44, fontSizeSp = 14
            )
            AppButton(
                text = stringResource(R.string.airdrop_records_add), onClick = { showAddDialog = true },
                enabled = !state.isSaving, fullWidth = false, heightDp = 44, fontSizeSp = 14
            )
        }
        if (state.message != null) {
            Text(if (state.message == "__AIRDROP_SAVED__") stringResource(R.string.airdrop_records_saved) else state.message!!, color = MaterialTheme.colorScheme.primary)
        }
        if (state.error != null) {
            Text(stringResource(R.string.airdrop_records_read_failed, localizedAirdropMessage(context, state.error!!)), color = MaterialTheme.colorScheme.error)
        }
        if (state.isLoading && state.items.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.items.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (state.error != null) stringResource(R.string.airdrop_records_unavailable) else stringResource(R.string.airdrop_records_empty), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (state.error != null) stringResource(R.string.airdrop_records_error_help) else stringResource(R.string.airdrop_records_empty_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.error != null) AppButton(stringResource(R.string.airdrop_records_reload), onClick = { airdropsViewModel.loadAirdrops() }, fullWidth = false)
                    else AppButton(stringResource(R.string.airdrop_records_add_first), onClick = { showAddDialog = true }, fullWidth = false)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    item.name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Surface(color = if (item.verification_status == "verified") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                                    Text(verificationLabel(item.verification_status), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (item.verification_status == "verified") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(airdropStatusLabel(item.status), item.chain.ifBlank { context.getString(R.string.airdrop_records_chain_missing) }).forEach { tag ->
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                                        Text(tag, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (item.reward_details.isNotBlank()) Text(stringResource(R.string.airdrop_records_reward, item.reward_details), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (item.eligibility.isNotBlank()) Text(stringResource(R.string.airdrop_records_eligibility, item.eligibility), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(stringResource(R.string.airdrop_records_source, if (item.source_name == "手动录入") stringResource(R.string.airdrop_records_manual_source) else item.source_name), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.official_url.isNotBlank()) {
                                Text(stringResource(R.string.airdrop_records_official_link, item.official_url), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            if (item.source_url.isNotBlank()) {
                                Text(stringResource(R.string.airdrop_records_source_link, item.source_url), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAirdropDialog(
            saving = state.isSaving,
            onDismiss = { showAddDialog = false },
            onSave = { request ->
                airdropsViewModel.createAirdrop(request)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddAirdropDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (AirdropWriteRequest) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var chain by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("") }
    var eligibility by remember { mutableStateOf("") }
    var officialUrl by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.airdrop_records_add_title), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTextField(name, { name = it }, placeholder = stringResource(R.string.airdrop_records_project_name_required))
                AppTextField(description, { description = it }, placeholder = stringResource(R.string.airdrop_records_project_description), singleLine = false)
                AppTextField(chain, { chain = it }, placeholder = stringResource(R.string.airdrop_records_chain_hint))
                AppTextField(reward, { reward = it }, placeholder = stringResource(R.string.airdrop_records_reward_hint), singleLine = false)
                AppTextField(eligibility, { eligibility = it }, placeholder = stringResource(R.string.airdrop_records_eligibility_hint), singleLine = false)
                AppTextField(officialUrl, { officialUrl = it }, placeholder = stringResource(R.string.airdrop_records_official_url_hint))
                AppTextField(sourceUrl, { sourceUrl = it }, placeholder = stringResource(R.string.airdrop_records_source_url_hint))
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.common_save),
                enabled = name.isNotBlank() && !saving,
                loading = saving,
                onClick = {
                    onSave(
                        AirdropWriteRequest(
                            name = name.trim(),
                            description = description.trim(),
                            category = "other",
                            chain = chain.trim(),
                            status = "unknown",
                            reward_details = reward.trim(),
                            eligibility = eligibility.trim(),
                            official_url = officialUrl.trim(),
                            source_name = "手动录入",
                            source_url = sourceUrl.trim(),
                            source_type = "manual",
                            verification_status = if (sourceUrl.isBlank()) "unverified" else "source_linked"
                        )
                    )
                }
            )
        },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun airdropStatusLabel(status: String): String = when (status) {
    "upcoming" -> stringResource(R.string.airdrop_status_upcoming)
    "ongoing" -> stringResource(R.string.airdrop_status_ongoing)
    "ended" -> stringResource(R.string.airdrop_status_ended)
    else -> stringResource(R.string.airdrop_status_pending)
}

@Composable
private fun verificationLabel(status: String): String = when (status) {
    "verified" -> stringResource(R.string.airdrop_verification_verified)
    "source_linked" -> stringResource(R.string.airdrop_verification_source_linked)
    else -> stringResource(R.string.airdrop_verification_unverified)
}

private fun localizedAirdropMessage(context: android.content.Context, message: String): String = when (message) {
    "__AIRDROP_LOAD_FAILED__" -> context.getString(R.string.airdrop_records_load_failed)
    "__AIRDROP_SAVE_FAILED__" -> context.getString(R.string.airdrop_records_save_failed)
    else -> message
}
