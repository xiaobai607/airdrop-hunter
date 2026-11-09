package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.AccountingRepository
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.CacheStoreAccountingLocalDataSource
import com.airdrophunter.app.data.CommunityProjectRatingRequest
import com.airdrophunter.app.data.CommunityProjectStatsDto
import com.airdrophunter.app.data.CommunityTokenAggregateDto
import com.airdrophunter.app.data.formatCommunityTokenBreakdown
import com.airdrophunter.app.data.WalletDto
import com.airdrophunter.app.ui.theme.AppColors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

private data class CommunityDemoStats(
    val gas: Double,
    val reward: Double,
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
    val uriHandler = LocalUriHandler.current
    val ratings by CacheStore.observeProjectRatings(projectOfficialId).collectAsStateWithLifecycle(initialValue = emptyList())
    val joinedProjects by CacheStore.observeUserProjects().collectAsStateWithLifecycle(initialValue = emptyList())
    val isJoined = joinedProjects.any { it.projectOfficialId == projectOfficialId }
    val average by CacheStore.observeProjectRatingAverage(projectOfficialId).collectAsStateWithLifecycle(initialValue = null)
    var communityStats by remember(projectOfficialId) { mutableStateOf<CommunityProjectStatsDto?>(null) }
    var periodSnapshots by remember(projectOfficialId) { mutableStateOf<Map<String, com.airdrophunter.app.data.CommunitySnapshotDto>>(emptyMap()) }
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
        "linea" -> CommunityDemoStats(42.80, 184.20, 4.9, 97)
        "scroll" -> CommunityDemoStats(38.60, 128.60, 4.8, 95)
        "zksync" -> CommunityDemoStats(62.00, 156.00, 4.7, 93)
        "starknet" -> CommunityDemoStats(51.00, 98.00, 4.4, 88)
        "base" -> CommunityDemoStats(73.00, 226.00, 4.9, 96)
        "arbitrum" -> CommunityDemoStats(84.00, 142.00, 4.2, 82)
        "optimism" -> CommunityDemoStats(49.00, 117.00, 4.6, 91)
        else -> CommunityDemoStats(18.50, 64.00, 4.0, 1)
    }
    val snapshotProjects = remember(periodSnapshots, projectOfficialId) {
        periodSnapshots.mapValues { (_, snapshot) ->
            snapshot.projects.firstOrNull { it.project_official_id == projectOfficialId }
        }
    }
    val overallProject = snapshotProjects["overall"]
    val displayAverageRating = if (useDemoStats) demoStats.score else communityStats?.average_rating ?: overallProject?.average_rating
    val displayRatingCount = if (useDemoStats) demoStats.ratingCount else communityStats?.rating_count ?: overallProject?.rating_count ?: 0L
    LaunchedEffect(projectOfficialId, sessionScopeKey) {
        wallet = ""
        canRate = false
        participationChecked = false
        selectedScore = 0
        saved = false
        saving = false
        statsLoading = true
        statsFailed = false
        ApiClient.initialize(context)

        // Show every available Room snapshot immediately, then silently revalidate the CDN.
        val cachedSnapshots = listOf("overall", "7d", "3d", "1h").mapNotNull { window ->
            com.airdrophunter.app.data.CommunitySnapshotRepository.readSnapshot(window)?.let { window to it }
        }.toMap()
        periodSnapshots = cachedSnapshots
        val refreshedSnapshots = com.airdrophunter.app.data.CommunitySnapshotRepository.refreshSnapshots()
        periodSnapshots = refreshedSnapshots.ifEmpty { cachedSnapshots }
        statsFailed = periodSnapshots.isEmpty()
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
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(projectName, color = AppColors.TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Steam 式真实参与者评价 · 仅星级评分", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val added = runCatching {
                                CacheStore.joinCommunityProject(projectOfficialId, projectName)
                            }.getOrDefault(false)
                            Toast.makeText(
                                context,
                                if (added) "${projectName} 已加入库管理" else "添加失败，请稍后重试",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = !isJoined,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (isJoined) Icons.Default.Check else Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isJoined) "已添加到库管理" else "添加到库管理")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(projectName, color = AppColors.TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(overallProject?.chain ?: "全网公开统计", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    overallProject?.logo?.takeIf { it.isNotBlank() }?.let { logo ->
                        Surface(shape = RoundedCornerShape(12.dp), color = AppColors.Surface) {
                            Text(logo, Modifier.padding(10.dp), color = AppColors.Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                overallProject?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                overallProject?.website_url?.takeIf { it.startsWith("https://", true) }?.let { url ->
                    TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                        Text("官方网站：$url", maxLines = 1)
                    }
                }

                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF17251F))) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text(
                            if (useDemoStats) "社区大盘（测试演示数据）" else "社区大盘（静态快照）",
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

                        val periodDefinitions = listOf(
                            Triple("overall", "总体", 1.0),
                            Triple("7d", "近7天", 0.42),
                            Triple("3d", "近3天", 0.18),
                            Triple("1h", "近1小时", 0.008)
                        )
                        periodDefinitions.forEach { (window, title, demoScale) ->
                            val projectSnapshot = snapshotProjects[window]
                            val gas = if (useDemoStats) demoStats.gas * demoScale else projectSnapshot?.gas_cost_usd_valuation ?: 0.0
                            val reward = if (useDemoStats) demoStats.reward * demoScale else projectSnapshot?.actual_reward_usd_valuation ?: 0.0
                            val updateTime = periodSnapshots[window]?.update_time
                            val verificationTiers = if (useDemoStats) emptyMap() else
                                projectSnapshot?.gas_by_verification_tier?.takeIf { it.isNotEmpty() }
                                    ?: if (window == "overall") communityStats?.gas_by_verification_tier.orEmpty() else emptyMap()
                            CommunitySnapshotPeriodMetric(
                                title = title,
                                gas = gas,
                                reward = reward,
                                updateTime = updateTime,
                                showUpdateTime = window != "overall",
                                gasTokens = if (useDemoStats) emptyList() else projectSnapshot?.gas_by_token.orEmpty(),
                                rewardTokens = if (useDemoStats) emptyList() else projectSnapshot?.reward_by_token.orEmpty(),
                                gasByVerificationTier = verificationTiers,
                                hasData = useDemoStats || gas > 0.0 || reward > 0.0 ||
                                    !projectSnapshot?.gas_by_token.isNullOrEmpty() || !projectSnapshot?.reward_by_token.isNullOrEmpty()
                            )
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
                                statsLoading -> "正在读取本地快照…"
                                statsFailed -> "暂无快照缓存；联网且 CDN 配置完成后会自动更新"
                                useDemoStats -> "测试演示样本仅用于界面验证，不会上传"
                                else -> "Gas 按用户自报、链上已确认、链上精确核验分级展示；实际收益由用户确认"
                            },
                            color = Color(0xFFB9C8C0), style = MaterialTheme.typography.labelSmall
                        )
                    }
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

@Composable
private fun CommunitySnapshotPeriodMetric(
    title: String,
    gas: Double,
    reward: Double,
    updateTime: String?,
    showUpdateTime: Boolean,
    gasTokens: List<CommunityTokenAggregateDto>,
    rewardTokens: List<CommunityTokenAggregateDto>,
    gasByVerificationTier: Map<String, Double>,
    hasData: Boolean
) {
    val total = gas + reward
    val hasComparableAmounts = hasData && total > 0.0
    val gasWeight = if (hasComparableAmounts) (gas / total).toFloat() else 0f
    val rewardWeight = if (hasComparableAmounts) (reward / total).toFloat() else 0f
    Surface(color = Color(0xFF20352B), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text("USD 估值", color = Color(0xFFB9C8C0), style = MaterialTheme.typography.labelSmall)
            }
            if (hasComparableAmounts && gas > 0.0 && reward > 0.0) {
                Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.weight(gasWeight).fillMaxHeight().background(Color(0xFFEF6262), RoundedCornerShape(6.dp)))
                    Box(Modifier.weight(rewardWeight).fillMaxHeight().background(Color(0xFF31C48D), RoundedCornerShape(6.dp)))
                }
            } else if (hasComparableAmounts) {
                Box(
                    Modifier.fillMaxWidth().height(18.dp).background(
                        if (gas > 0.0) Color(0xFFEF6262) else Color(0xFF31C48D),
                        RoundedCornerShape(6.dp)
                    )
                )
            } else {
                Text(
                    if (hasData) "暂无可比较的 Gas / 实际收益金额" else "暂无该时间窗口的确认贡献",
                    color = Color(0xFFB9C8C0),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Gas USD估值", color = Color(0xFFFFB0B0), style = MaterialTheme.typography.labelSmall)
                    Text(String.format(java.util.Locale.US, "$%,.2f", gas), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("实际收益 USD估值", color = Color(0xFF9FE7C9), style = MaterialTheme.typography.labelSmall)
                    Text(String.format(java.util.Locale.US, "$%,.2f", reward), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            if (gasTokens.isNotEmpty()) {
                Text("Gas 原生币：${formatCommunityTokenBreakdown(gasTokens)}",
                    color = Color(0xFFFFB0B0), style = MaterialTheme.typography.labelSmall)
            }
            if (rewardTokens.isNotEmpty()) {
                Text("收益代币：${formatCommunityTokenBreakdown(rewardTokens)}",
                    color = Color(0xFF9FE7C9), style = MaterialTheme.typography.labelSmall)
            }
            if (gasByVerificationTier.isNotEmpty()) {
                CommunityVerificationTierBar(values = gasByVerificationTier, compact = false)
            }
            if (showUpdateTime) {
                Text(
                    updateTime?.let { "数据更新时间：${it.replace("T", " ")}" } ?: "数据更新时间：暂无快照",
                    color = Color(0xFFB9C8C0),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
