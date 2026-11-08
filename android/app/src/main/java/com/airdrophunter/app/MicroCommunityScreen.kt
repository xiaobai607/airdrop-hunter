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
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.CommunityProjectStatsDto
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.ui.theme.AppColors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

private data class CommunityDemoStats(
    val gas: Double,
    val reward: Double,
    val contributions: Long,
    val interactions: Long
)

@Composable
fun MicroCommunityScreen(
    projectOfficialId: String,
    projectName: String,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ratings by CacheStore.observeProjectRatings(projectOfficialId).collectAsStateWithLifecycle(initialValue = emptyList())
    val average by CacheStore.observeProjectRatingAverage(projectOfficialId).collectAsStateWithLifecycle(initialValue = null)
    var communityStats by remember(projectOfficialId) { mutableStateOf<CommunityProjectStatsDto?>(null) }
    var statsLoading by remember(projectOfficialId) { mutableStateOf(true) }
    var statsFailed by remember(projectOfficialId) { mutableStateOf(false) }
    var useDemoStats by remember(projectOfficialId) { mutableStateOf(BuildConfig.DEBUG) }
    var wallet by remember { mutableStateOf("") }
    var canRate by remember { mutableStateOf(false) }
    var participationChecked by remember { mutableStateOf(false) }
    var selectedScore by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val myRating = ratings.firstOrNull { it.walletAddress == wallet }
    val demoStats = when (projectOfficialId.lowercase()) {
        "linea" -> CommunityDemoStats(42.80, 184.20, 1280, 4320)
        "scroll" -> CommunityDemoStats(38.60, 128.60, 964, 3210)
        "zksync" -> CommunityDemoStats(62.00, 156.00, 1820, 5860)
        "starknet" -> CommunityDemoStats(51.00, 98.00, 1450, 4100)
        "base" -> CommunityDemoStats(73.00, 226.00, 2200, 7240)
        "arbitrum" -> CommunityDemoStats(84.00, 142.00, 1730, 5320)
        "optimism" -> CommunityDemoStats(49.00, 117.00, 1390, 4680)
        else -> CommunityDemoStats(18.50, 64.00, 320, 980)
    }
    val displayGasTotal = if (useDemoStats) demoStats.gas else communityStats?.total_gas_cost_usd ?: 0.0
    val displayRewardTotal = if (useDemoStats) demoStats.reward else communityStats?.total_actual_reward_usd ?: 0.0
    val displayContributionCount = if (useDemoStats) demoStats.contributions else communityStats?.contribution_count ?: 0L
    val displayInteractionCount = if (useDemoStats) demoStats.interactions else communityStats?.total_interactions ?: 0L
    val combinedTotal = (displayGasTotal + displayRewardTotal).coerceAtLeast(1.0)
    val rewardWeight = if (displayGasTotal + displayRewardTotal <= 0.0) 0.5f
        else (displayRewardTotal / combinedTotal).toFloat().coerceIn(0.01f, 0.99f)
    val gasWeight = 1f - rewardWeight
    LaunchedEffect(projectOfficialId) {
        statsLoading = true
        statsFailed = false
        runCatching {
            ApiClient.initialize(context)
            ApiClient.service.getCommunityProjectStats(projectOfficialId)
        }.onSuccess { communityStats = it }
            .onFailure { statsFailed = true }
        statsLoading = false

        val type = object : TypeToken<List<WalletDto>>() {}.type
        val wallets = CacheStore.readLocal("wallets")?.let {
            runCatching { Gson().fromJson<List<WalletDto>>(it, type) }.getOrNull()
        }.orEmpty()
        wallet = wallets.firstOrNull()?.address.orEmpty()
        canRate = wallet.isNotBlank() && CacheStore.hasPnlParticipation(projectOfficialId)
        participationChecked = true
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
                    Text(
                        if (useDemoStats) "社区大盘（测试演示数据）" else "社区大盘（全网用户确认贡献）",
                        color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                    )
                    if (BuildConfig.DEBUG) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { useDemoStats = true }, contentPadding = PaddingValues(0.dp)) {
                                Text(if (useDemoStats) "✓ 测试演示" else "测试演示", color = Color(0xFFFFD08A))
                            }
                            TextButton(onClick = { useDemoStats = false }, contentPadding = PaddingValues(0.dp)) {
                                Text(if (!useDemoStats) "✓ 全网真实" else "全网真实", color = Color.White)
                            }
                        }
                    }
                    if (useDemoStats) {
                        Text("仅用于界面和流程测试，不会上传到公共统计", color = Color(0xFFFFD08A), style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.weight(rewardWeight).fillMaxHeight().background(Color(0xFF31C48D), RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)))
                        Box(Modifier.weight(gasWeight).fillMaxHeight().background(Color(0xFFEF6262), RoundedCornerShape(topEnd = 9.dp, bottomEnd = 9.dp)))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("总收益", color = Color(0xFF9FE7C9), style = MaterialTheme.typography.labelSmall)
                            Text(String.format(java.util.Locale.US, "$%,.2f", displayRewardTotal), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("总花费", color = Color(0xFFFFB0B0), style = MaterialTheme.typography.labelSmall)
                            Text(String.format(java.util.Locale.US, "$%,.2f", displayGasTotal), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        when {
                            useDemoStats -> "测试样本：$displayContributionCount 笔演示贡献 · 不进入全网统计"
                            statsLoading -> "正在加载全网统计…"
                            statsFailed -> "全网统计暂时不可用，请稍后重试"
                            displayContributionCount == 0L -> "暂无用户确认贡献，统计从真实入账开始累计"
                            else -> "已确认贡献 $displayContributionCount 笔 · 交互 $displayInteractionCount 次"
                        },
                        color = Color(0xFFB9C8C0), style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("本机已保存的评分", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (ratings.isEmpty()) "暂无评分记录。全网评分功能尚未接入，暂不展示模拟数据。"
                        else "本机评分记录：${ratings.size} 条 · 平均 ${String.format(java.util.Locale.US, "%.1f", average ?: 0.0)} 星",
                        color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall
                    )
                    ratings.take(3).forEach { rating ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("本机记录 #${rating.walletAddress.takeLast(4).uppercase()}",
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
                                modifier = Modifier.size(34.dp).clickable(enabled = !saved) {
                                    if (!participationChecked || !canRate) {
                                        Toast.makeText(context, "仅限真实参与该项目的用户打分", Toast.LENGTH_SHORT).show()
                                    } else selectedScore = score
                                }
                            )
                        }
                    }
                    if (!canRate) {
                        Text("仅限真实参与该项目的用户打分", color = AppColors.TextHelper, style = MaterialTheme.typography.labelMedium)
                    } else if (saved) {
                        Text("已提交：匿名猎人 #${wallet.takeLast(4).uppercase()}", color = AppColors.Success, style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text("评分将以“匿名猎人 #XXXX”显示，不支持文字评论", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = {
                            if (!participationChecked || !canRate) {
                                Toast.makeText(context, "仅限真实参与该项目的用户打分", Toast.LENGTH_SHORT).show()
                            } else if (selectedScore in 1..5 && !saved && !saving) {
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
