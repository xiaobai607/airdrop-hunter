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
import com.airdrophunter.app.data.AccountingRepository
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.CacheStoreAccountingLocalDataSource
import com.airdrophunter.app.data.CommunityProjectRatingRequest
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
    val interactions: Long,
    val recentInteractions: Long,
    val score: Double,
    val ratingCount: Long
)

@Composable
fun MicroCommunityScreen(
    projectOfficialId: String,
    projectName: String,
    modifier: Modifier = Modifier,
    isGuest: Boolean = true,
    sessionScopeKey: String = "guest"
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val ratings by CacheStore.observeProjectRatings(projectOfficialId).collectAsStateWithLifecycle(initialValue = emptyList())
    val average by CacheStore.observeProjectRatingAverage(projectOfficialId).collectAsStateWithLifecycle(initialValue = null)
    var communityStats by remember(projectOfficialId) { mutableStateOf<CommunityProjectStatsDto?>(null) }
    var statsLoading by remember(projectOfficialId) { mutableStateOf(true) }
    var statsFailed by remember(projectOfficialId) { mutableStateOf(false) }
    var useDemoStats by remember(projectOfficialId) { mutableStateOf(BuildConfig.DEBUG) }
    var wallet by remember(projectOfficialId, sessionScopeKey) { mutableStateOf("") }
    var canRate by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    var participationChecked by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    var selectedScore by remember(projectOfficialId, sessionScopeKey) { mutableIntStateOf(0) }
    var saved by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    var saving by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    val myRating = ratings.firstOrNull { it.walletAddress == wallet }
    val demoStats = when (projectOfficialId.lowercase()) {
        "linea" -> CommunityDemoStats(42.80, 184.20, 1280, 4320, 310, 4.9, 97)
        "scroll" -> CommunityDemoStats(38.60, 128.60, 964, 3210, 205, 4.8, 95)
        "zksync" -> CommunityDemoStats(62.00, 156.00, 1820, 5860, 480, 4.7, 93)
        "starknet" -> CommunityDemoStats(51.00, 98.00, 1450, 4100, 365, 4.4, 88)
        "base" -> CommunityDemoStats(73.00, 226.00, 2200, 7240, 520, 4.9, 96)
        "arbitrum" -> CommunityDemoStats(84.00, 142.00, 1730, 5320, 190, 4.2, 82)
        "optimism" -> CommunityDemoStats(49.00, 117.00, 1390, 4680, 270, 4.6, 91)
        else -> CommunityDemoStats(18.50, 64.00, 320, 980, 20, 4.0, 1)
    }
    val displayGasTotal = if (useDemoStats) demoStats.gas else communityStats?.total_gas_cost_usd ?: 0.0
    val displayRewardTotal = if (useDemoStats) demoStats.reward else communityStats?.total_actual_reward_usd ?: 0.0
    val displayContributionCount = if (useDemoStats) demoStats.contributions else communityStats?.contribution_count ?: 0L
    val displayInteractionCount = if (useDemoStats) demoStats.interactions else communityStats?.total_interactions ?: 0L
    val displayRecentInteractions = if (useDemoStats) demoStats.recentInteractions else communityStats?.interactions_24h ?: 0L
    val displayAverageRating = if (useDemoStats) demoStats.score else communityStats?.average_rating
    val displayRatingCount = if (useDemoStats) demoStats.ratingCount else communityStats?.rating_count ?: 0L
    val combinedTotal = (displayGasTotal + displayRewardTotal).coerceAtLeast(1.0)
    val rewardWeight = if (displayGasTotal + displayRewardTotal <= 0.0) 0.5f
        else (displayRewardTotal / combinedTotal).toFloat().coerceIn(0.01f, 0.99f)
    val gasWeight = 1f - rewardWeight
    LaunchedEffect(projectOfficialId, sessionScopeKey) {
        wallet = ""
        canRate = false
        participationChecked = false
        selectedScore = 0
        saved = false
        saving = false
        statsLoading = true
        statsFailed = false
        runCatching {
            ApiClient.initialize(context)
            ApiClient.service.getCommunityProjectStats(projectOfficialId)
        }.onSuccess { communityStats = it }
            .onFailure { statsFailed = true }
        statsLoading = false

        val type = object : TypeToken<List<WalletDto>>() {}.type
        val walletCache = if (isGuest) CacheStore.readLocal("wallets") else CacheStore.read("wallets")
        val wallets = walletCache?.let {
            runCatching { Gson().fromJson<List<WalletDto>>(it, type) }.getOrNull()
        }.orEmpty()
        wallet = wallets.firstOrNull()?.address.orEmpty()
        canRate = CacheStore.hasPnlParticipation(projectOfficialId) && (!isGuest || wallet.isNotBlank())
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
                        if (displayRatingCount > 0 && displayAverageRating != null)
                            "全网评分 ★ ${String.format(java.util.Locale.US, "%.1f", displayAverageRating)} · ${displayRatingCount} 个账号评分"
                        else "暂无全网评分",
                        color = if (displayRatingCount > 0) Color(0xFFFFD08A) else Color(0xFFB9C8C0),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        when {
                            useDemoStats -> "测试样本：$displayContributionCount 笔演示贡献 · 不进入全网统计"
                            statsLoading -> "正在加载全网统计…"
                            statsFailed -> "全网统计暂时不可用，请稍后重试"
                            displayContributionCount == 0L -> "暂无用户确认贡献，统计从真实入账开始累计"
                            else -> "已确认贡献 $displayContributionCount 笔 · 总交互 $displayInteractionCount 次 · 近24h交互 $displayRecentInteractions 次"
                        },
                        color = Color(0xFFB9C8C0), style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("本机已保存的评分", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (ratings.isEmpty()) "暂无本机评分记录。全网评分只统计通过真实参与验证的账号。"
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
                        Text(if (isGuest) "已保存在本机" else "已提交全网星级评分", color = AppColors.Success, style = MaterialTheme.typography.labelMedium)
                    } else if (isGuest) {
                        Text("游客评分只保存在本机，不计入全网评分", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text("账号私有账本将用于验证真实参与，公开只展示星级汇总", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = {
                            if (!participationChecked || !canRate) {
                                Toast.makeText(context, "仅限真实参与该项目的用户打分", Toast.LENGTH_SHORT).show()
                            } else if (selectedScore in 1..5 && !saved && !saving) {
                                saving = true
                                scope.launch {
                                    val ok = if (isGuest) {
                                        CacheStore.saveProjectRating(projectOfficialId, wallet, selectedScore)
                                    } else {
                                        runCatching {
                                            val repository = AccountingRepository(CacheStoreAccountingLocalDataSource(), ApiClient.service)
                                            check(repository.syncPersonalLedger()) { "私人账本同步失败" }
                                            communityStats = ApiClient.service.submitCommunityProjectRating(
                                                projectOfficialId, CommunityProjectRatingRequest(selectedScore)
                                            )
                                            if (wallet.isBlank()) true else CacheStore.saveProjectRating(projectOfficialId, wallet, selectedScore)
                                        }.getOrDefault(false)
                                    }
                                    saving = false
                                    saved = ok
                                    if (!ok) Toast.makeText(context, if (isGuest) "本机评分保存失败" else "云端验证失败，请确认账本已同步", Toast.LENGTH_SHORT).show()
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
