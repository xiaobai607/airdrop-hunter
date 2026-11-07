package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.ui.theme.AppColors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

private data class RatingPeriod(val title: String, val score: Double, val people: Int, val positivePercent: Int)

private fun steamLabel(positivePercent: Int): String = when {
    positivePercent >= 95 -> "好评如潮"
    positivePercent >= 85 -> "特别好评"
    positivePercent >= 75 -> "多半好评"
    else -> "褒贬不一"
}

@Composable
fun MicroCommunityScreen(
    projectOfficialId: String,
    projectName: String,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val ratings by CacheStore.observeProjectRatings(projectOfficialId).collectAsStateWithLifecycle(initialValue = emptyList())
    val average by CacheStore.observeProjectRatingAverage(projectOfficialId).collectAsStateWithLifecycle(initialValue = null)
    val pnl by CacheStore.observePnlRecords().collectAsStateWithLifecycle(initialValue = emptyList())
    var wallet by remember { mutableStateOf("MOCK-WALLET-LOCAL") }
    var canRate by remember { mutableStateOf(false) }
    var selectedScore by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val myRating = ratings.firstOrNull { it.walletAddress == wallet }
    val projectRecords = pnl.filter { it.projectOfficialId == projectOfficialId }
    val displayGasTotal = 1000.0 + projectRecords.sumOf { it.totalGasCostUsd }
    val displayRewardTotal = 2000.0 + projectRecords.sumOf { it.totalRewardUsd }
    val combinedTotal = (displayGasTotal + displayRewardTotal).coerceAtLeast(1.0)
    val rewardWeight = (displayRewardTotal / combinedTotal).toFloat().coerceIn(0.01f, 0.99f)
    val gasWeight = 1f - rewardWeight
    val periods = listOf(
        RatingPeriod("近 1 天", 4.9, 12, 96),
        RatingPeriod("近 3 天", 4.5, 45, 91),
        RatingPeriod("近 1 周", 3.2, 120, 68),
        RatingPeriod("总体评价", 3.8, 300, 79)
    )

    LaunchedEffect(projectOfficialId) {
        val type = object : TypeToken<List<WalletDto>>() {}.type
        val wallets = CacheStore.readLocal("wallets")?.let {
            runCatching { Gson().fromJson<List<WalletDto>>(it, type) }.getOrNull()
        }.orEmpty()
        wallet = wallets.firstOrNull()?.address ?: "MOCK-WALLET-LOCAL"
        canRate = CacheStore.hasPnlParticipation(projectOfficialId)
    }
    LaunchedEffect(myRating) {
        if (myRating != null) { selectedScore = myRating.score; saved = true }
    }

    Column(modifier.fillMaxSize().background(AppColors.Background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(projectName, color = AppColors.TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Steam 式真实参与者评价 · 仅星级评分", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF17251F))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("全网盈亏大盘", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.weight(rewardWeight).fillMaxHeight().background(Color(0xFF31C48D), RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)))
                        Box(Modifier.weight(gasWeight).fillMaxHeight().background(Color(0xFFEF6262), RoundedCornerShape(topEnd = 9.dp, bottomEnd = 9.dp)))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("全网总收益", color = Color(0xFF9FE7C9), style = MaterialTheme.typography.labelSmall)
                            Text(String.format(java.util.Locale.US, "$%,.2f", displayRewardTotal), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("全网总花费", color = Color(0xFFFFB0B0), style = MaterialTheme.typography.labelSmall)
                            Text(String.format(java.util.Locale.US, "$%,.2f", displayGasTotal), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Mock 全网样本 + 本地已确认账单 · 收益 ${(rewardWeight * 100).toInt()}% / Gas ${(gasWeight * 100).toInt()}%",
                        color = Color(0xFFB9C8C0), style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("社区评价总结", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    periods.forEachIndexed { index, period ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(period.title, Modifier.weight(1f), color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Surface(color = when (index) {
                                0 -> Color(0xFFE8F7EF); 1 -> Color(0xFFEAF3FF); 2 -> Color(0xFFFFF2E4); else -> Color(0xFFF0EDFF)
                            }, shape = RoundedCornerShape(999.dp)) {
                                Text(steamLabel(period.positivePercent), Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    color = AppColors.TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(9.dp))
                            Text("★ %.1f".format(java.util.Locale.US, period.score), color = Color(0xFFE5A126),
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("  ${period.people}人", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        }
                        if (index != periods.lastIndex) HorizontalDivider(color = AppColors.Border)
                    }
                    Text("好评率按 4–5 星统计 · 各周期为 Mock 样本", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                    Text("本地真实评分：${ratings.size} 人 · 平均 ${String.format(java.util.Locale.US, "%.1f", average ?: 0.0)} 星",
                        color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    ratings.take(3).forEach { rating ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("匿名猎人 #${rating.walletAddress.takeLast(4).uppercase()}",
                                Modifier.weight(1f), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            Text("★".repeat(rating.score), color = Color(0xFFFFB12B), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("为这个项目打分", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { score ->
                            Icon(
                                imageVector = if (score <= selectedScore) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "$score 星",
                                tint = if (score <= selectedScore) Color(0xFFFFB12B) else AppColors.TextHelper,
                                modifier = Modifier.size(34.dp).clickable(enabled = canRate && !saved) { selectedScore = score }
                            )
                        }
                    }
                    if (!canRate) {
                        Text("仅限真实参与用户评价", color = AppColors.TextHelper, style = MaterialTheme.typography.labelMedium)
                    } else if (saved) {
                        Text("已提交：匿名猎人 #${wallet.takeLast(4).uppercase()}", color = AppColors.Success, style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text("评分将以“匿名猎人 #XXXX”显示，不支持文字评论", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = {
                            if (canRate && selectedScore in 1..5 && !saved && !saving) {
                                saving = true
                                scope.launch {
                                    val ok = CacheStore.saveProjectRating(projectOfficialId, wallet, selectedScore)
                                    saving = false
                                    saved = ok
                                }
                            }
                        },
                        enabled = canRate && selectedScore in 1..5 && !saved && !saving,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Primary,
                            disabledContainerColor = AppColors.Border
                        ),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text(if (saving) "提交中…" else if (saved) "已评价" else "提交星级评分", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
