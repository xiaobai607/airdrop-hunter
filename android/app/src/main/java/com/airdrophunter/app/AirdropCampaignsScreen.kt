package com.airdrophunter.app

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.AirdropCampaignDto
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun AirdropCampaignsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as AirdropHunterApplication
    val vm: AirdropCampaignsViewModel = viewModel(
        factory = AirdropCampaignsViewModelFactory(app.appContainer.airdropCampaignRepository)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("全部" to null, "精选" to "featured", "普通" to "ordinary").forEach { (label, tier) ->
                val selected = state.selectedTier == tier
                Surface(
                    onClick = { vm.selectTier(tier) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) AppColors.Primary else AppColors.Surface,
                    border = if (selected) null else BorderStroke(1.dp, AppColors.Border)
                ) {
                    Text(
                        label,
                        Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        color = if (selected) AppColors.Surface else AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::refresh, enabled = !state.isRefreshing) {
                Text(if (state.isRefreshing) "刷新中…" else "刷新", color = AppColors.Primary)
            }
        }

        if (state.error != null && state.items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("暂时无法加载空投活动", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(state.error.orEmpty(), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Button(onClick = vm::load, enabled = !state.isLoading) { Text("重试") }
            }
        } else if (state.isLoading && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        } else if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无已核验并公开的空投活动", color = AppColors.TextHelper)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "\${state.items.size} 个公开活动",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                items(state.items, key = { it.id }) { campaign ->
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

@Composable
private fun CampaignCard(campaign: AirdropCampaignDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.Border),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    campaign.name,
                    Modifier.weight(1f),
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = if (campaign.tier == "featured") AppColors.TealTint else AppColors.MutedBackground,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        if (campaign.tier == "featured") "精选" else "普通",
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = AppColors.Primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (campaign.description.isNotBlank()) {
                Text(campaign.description, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                CampaignTag(campaign.chain ?: "链待确认")
                CampaignTag(campaignStatusText(campaign.campaign_status))
                CampaignTag(if (campaign.requires_kyc == true) "需要 KYC" else if (campaign.requires_kyc == false || campaign.is_kyc_free == true) "免 KYC" else "KYC 未知")
            }
            Text(
                if (campaign.official_url.isNullOrBlank()) "官方链接待核验" else "打开官方活动 ↗",
                color = if (campaign.official_url.isNullOrBlank()) AppColors.TextHelper else AppColors.Primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CampaignTag(value: String) {
    Surface(color = AppColors.MutedBackground, shape = RoundedCornerShape(999.dp)) {
        Text(value, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
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
