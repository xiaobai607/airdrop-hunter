package com.airdrophunter.app

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.AirdropCampaignDto
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.appPressFeedback
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.StaggeredListItem
import com.airdrophunter.app.ui.components.AppChip
import com.airdrophunter.app.ui.components.AirdropSourceHealthPanel

@Composable
fun AirdropCampaignsScreen(
    modifier: Modifier = Modifier,
    onOpenBinanceOfficial: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as AirdropHunterApplication
    val vm: AirdropCampaignsViewModel = viewModel(
        factory = AirdropCampaignsViewModelFactory(app.appContainer.airdropCampaignRepository)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showSourceDetails by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("查看已收录的公开活动与参与条件", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
                AppTextButton(onClick = vm::refresh, enabled = !state.isRefreshing) {
                    Text(if (state.isRefreshing) "刷新中…" else "刷新", color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("全部" to null, "精选" to "featured", "普通" to "ordinary").forEach { (label, tier) ->
                    AppChip(text = label, selected = state.selectedTier == tier, onClick = { vm.selectTier(tier) })
                }
            }
        }

        Column(Modifier.weight(1f)) {
        if (state.sourceStatuses.isNotEmpty() || state.sourceStatusError != null) {
            AirdropSourceHealthPanel(
                statuses = state.sourceStatuses,
                error = if (state.items.isNotEmpty()) state.error ?: state.sourceStatusError else state.sourceStatusError,
                expanded = showSourceDetails,
                onToggle = { showSourceDetails = !showSourceDetails },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (state.error != null && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("活动目录暂时不可用", color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("请检查网络后重试。\n${state.error.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        AppButton("重新加载", onClick = vm::load, enabled = !state.isLoading, fullWidth = false)
                    }
                }
            }
        } else if (state.isLoading && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂时没有公开活动", color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("当前筛选条件下没有可展示的记录。稍后可以刷新再查看。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        AppButton("刷新活动", onClick = vm::refresh, enabled = !state.isRefreshing, fullWidth = false)
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.error != null && state.sourceStatuses.isEmpty()) {
                    item {
                        Text(
                            state.error.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                item {
                    Text(
                        "${state.items.size} 个公开活动",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                // The Binance entry occupies the first campaign-card slot; no public campaign is removed.
                item(key = "binance_official_entry") {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .appPressFeedback(onClick = onOpenBinanceOfficial, scale = 0.96f),
                        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp)) {
                                Text("币安", Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("币安官方活动", color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("查看 Binance 官方公告与活动", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, campaign ->
                    StaggeredListItem(campaign.id, index) {
                        CampaignCard(campaign) {
                        val url = campaign.official_url
                        if (url.isNullOrBlank()) {
                            Toast.makeText(context, "该活动暂未提供已核验的官方链接", Toast.LENGTH_SHORT).show()
                        } else {
                            runCatching {
                                context.startActivity(
                                    Intent(context, BrowserActivity::class.java)
                                        .putExtra(BrowserActivity.EXTRA_URL, url)
                                        .putExtra(BrowserActivity.EXTRA_TITLE, campaign.name)
                                )
                            }.onFailure {
                                Toast.makeText(context, "无法打开活动链接", Toast.LENGTH_SHORT).show()
                            }
                        }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CampaignCard(campaign: AirdropCampaignDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().appPressFeedback(onClick = onClick, scale = 0.96f),
        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    campaign.name,
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    color = if (campaign.tier == "featured") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)
                ) {
                    Text(
                        if (campaign.tier == "featured") "精选" else "普通",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (campaign.description.isNotBlank()) {
                Text(
                    campaign.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CampaignTag("Binance 官方")
                CampaignTag(campaign.chain ?: "链待确认")
                CampaignTag(campaignStatusText(campaign.campaign_status))
                CampaignTag(if (campaign.requires_kyc == true) "需要 KYC" else if (campaign.requires_kyc == false || campaign.is_kyc_free == true) "免 KYC" else "KYC 未知")
            }
            Text(
                if (campaign.official_url.isNullOrBlank()) "官方链接待核验" else "打开官方活动 ↗",
                color = if (campaign.official_url.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f) else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CampaignTag(value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
        Text(value, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

private fun campaignStatusText(status: String): String = when (status) {
    "upcoming" -> "即将开始"
    "ongoing" -> "进行中"
    "snapshot" -> "快照相关"
    "claim_open" -> "开放领取"
    "ended" -> "已结束"
    else -> "状态待确认"
}
