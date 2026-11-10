package com.airdrophunter.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import kotlin.math.roundToInt
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheStore
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
    val isDemoProject = projectOfficialId.startsWith("demo:")
    val ratings by CacheStore.observeProjectRatings(projectOfficialId).collectAsStateWithLifecycle(initialValue = emptyList())
    val joinedProjects by CacheStore.observeUserProjects().collectAsStateWithLifecycle(initialValue = emptyList())
    val isJoined = joinedProjects.any { it.projectOfficialId == projectOfficialId }
    val average by CacheStore.observeProjectRatingAverage(projectOfficialId).collectAsStateWithLifecycle(initialValue = null)
    var communityStats by remember(projectOfficialId) { mutableStateOf<CommunityProjectStatsDto?>(null) }
    var periodSnapshots by remember(projectOfficialId) { mutableStateOf<Map<String, com.airdrophunter.app.data.CommunitySnapshotDto>>(emptyMap()) }
    var statsLoading by remember(projectOfficialId) { mutableStateOf(true) }
    var statsFailed by remember(projectOfficialId) { mutableStateOf(false) }
    var selectedPeriod by remember(projectOfficialId) { mutableStateOf("overall") }
    var useDemoStats by remember(projectOfficialId) { mutableStateOf(isDemoProject) }
    var wallet by remember(projectOfficialId, sessionScopeKey) { mutableStateOf("") }
    var canRate by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    var participationChecked by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    var selectedScore by remember(projectOfficialId, sessionScopeKey) { mutableIntStateOf(0) }
    var saved by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    var saving by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    val myRating = ratings.firstOrNull { it.walletAddress == wallet }
    val demoStats = when (projectOfficialId.lowercase()) {
        "demo:novalayer" -> CommunityDemoStats(42.80, 184.20, 4.8, 28)
        "demo:orbitfi" -> CommunityDemoStats(38.60, 128.60, 4.6, 21)
        "demo:zetabridge" -> CommunityDemoStats(62.00, 156.00, 4.2, 17)
        "demo:pixelquest" -> CommunityDemoStats(51.00, 98.00, 3.8, 13)
        "demo:aetherrollup" -> CommunityDemoStats(73.00, 226.00, 4.9, 32)
        "demo:lumenprotocol" -> CommunityDemoStats(18.50, 64.00, 4.0, 9)
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
        if (isDemoProject) {
            // Synthetic project detail is local-only; it never touches API or personal data.
            periodSnapshots = emptyMap()
            statsLoading = false
            participationChecked = true
            canRate = false
            return@LaunchedEffect
        }
        ApiClient.initialize(context)

        // Show every available Room snapshot immediately, then silently revalidate the CDN.
        val cachedSnapshots = listOf("overall", "3d", "1h").mapNotNull { window ->
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

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(projectName, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Steam 式真实参与者评价 · 仅星级评分", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = {
                        if (isDemoProject) {
                            Toast.makeText(context, "测试项目不会写入库管理", Toast.LENGTH_SHORT).show()
                        } else scope.launch {
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
                    enabled = !isJoined && !isDemoProject,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (isJoined) Icons.Default.Check else Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(when {
                        isDemoProject -> "测试项目 · 不会加入库管理"
                        isJoined -> "已添加到库管理"
                        else -> "添加到库管理"
                    })
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(projectName, color = AppColors.TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(overallProject?.chain ?: if (isDemoProject) "测试演示项目" else "全网公开统计", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    overallProject?.logo?.takeIf { it.isNotBlank() }?.let { logo ->
                        Surface(shape = RoundedCornerShape(12.dp), color = AppColors.Surface) {
                            Text(logo, Modifier.padding(10.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val detailDescription = overallProject?.description?.takeIf { it.isNotBlank() }
                    ?: if (isDemoProject) "这是用于界面与流程测试的虚构空投项目，不代表真实项目、真实奖励或真实参与数据。" else null
                detailDescription?.let {
                    Text(it, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                overallProject?.website_url?.takeIf { it.startsWith("https://", true) }?.let { url ->
                    TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                        Text("官方网站：$url", maxLines = 1)
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            listOf("overall" to "总览", "3d" to "近3天", "1h" to "近1小时").forEach { (window, label) ->
                                val selected = selectedPeriod == window
                                Surface(
                                    modifier = Modifier.weight(1f).clickable { selectedPeriod = window },
                                    color = if (selected) Color(0xFF7A58FF) else Color.Transparent,
                                    shape = CircleShape
                                ) {
                                    Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                        Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        if (BuildConfig.DEBUG) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { useDemoStats = true }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) {
                                        Text(if (useDemoStats) "✓ 演示" else "演示", color = MaterialTheme.colorScheme.tertiary,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(onClick = { useDemoStats = false }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) {
                                        Text(if (!useDemoStats) "✓ 全网" else "全网", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        if (useDemoStats) {
                            Text("测试演示数据 · 不计入全网统计", color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        AnimatedContent(
                            targetState = selectedPeriod,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "community-period-switch"
                        ) { window ->
                            val demoScale = when (window) { "overall" -> 1.0; "3d" -> 0.18; else -> 0.008 }
                            val projectSnapshot = snapshotProjects[window]
                            val gas = if (useDemoStats) demoStats.gas * demoScale else projectSnapshot?.gas_cost_usd_valuation ?: 0.0
                            val reward = if (useDemoStats) demoStats.reward * demoScale else projectSnapshot?.reward_usd_valuation?.takeIf { it > 0.0 } ?: projectSnapshot?.actual_reward_usd_valuation ?: 0.0
                            val gasTokens = if (useDemoStats) emptyList() else projectSnapshot?.gas_by_token.orEmpty()
                            val rewardTokens = if (useDemoStats) emptyList() else projectSnapshot?.reward_by_token.orEmpty()
                            CommunitySnapshotPeriodMetric(
                                gas = gas,
                                reward = reward,
                                gasTokens = gasTokens,
                                rewardTokens = rewardTokens,
                                hasData = useDemoStats || gas > 0.0 || reward > 0.0 ||
                                    gasTokens.isNotEmpty() || rewardTokens.isNotEmpty()
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(
                                when {
                                    statsLoading -> "正在读取快照…"
                                    statsFailed -> "暂无快照缓存"
                                    else -> "数据更新：" + (if (useDemoStats) "演示样本" else periodSnapshots[selectedPeriod]?.update_time?.replace("T", " ") ?: "暂无")
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
                        Text("仅限真实参与该项目的用户打分", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    } else if (saved) {
                        Text("已保存在本机", color = AppColors.Success, style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text("评分仅保存在本机；当前版本不会上传个人账本或提交全网评分", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = {
                            if (!participationChecked || !canRate) {
                                Toast.makeText(context, "仅限本机有真实参与记账记录的用户打分", Toast.LENGTH_SHORT).show()
                            } else if (selectedScore in 1..5 && !saved && !saving) {
                                saving = true
                                scope.launch {
                                    val ok = CacheStore.saveProjectRating(projectOfficialId, wallet, selectedScore)
                                    saving = false
                                    saved = ok
                                    if (!ok) Toast.makeText(context, "本机评分保存失败", Toast.LENGTH_SHORT).show()
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
                    ) { Text(if (saving) "提交中…" else if (saved) "已评价" else "保存星级评分", fontWeight = FontWeight.Bold) }
                }
            }
        }
        }
    }
}

@Composable
private fun CommunitySnapshotPeriodMetric(
    gas: Double,
    reward: Double,
    gasTokens: List<CommunityTokenAggregateDto>,
    rewardTokens: List<CommunityTokenAggregateDto>,
    hasData: Boolean
) {
    val total = (gas + reward).coerceAtLeast(0.0)
    val hasComparableAmounts = hasData && total > 0.0
    val gasWeight = if (hasComparableAmounts) (gas.coerceAtLeast(0.0) / total).toFloat() else 0f
    val rewardWeight = if (hasComparableAmounts) (reward.coerceAtLeast(0.0) / total).toFloat() else 0f
    val gasColor = MaterialTheme.colorScheme.error
    val rewardColor = MaterialTheme.colorScheme.secondary
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("🔥 Gas 支出", color = gasColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(formatCompactUsd(gas), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(tokenHint(gasTokens), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("🎁 收益", color = rewardColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(formatCompactUsd(reward), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(tokenHint(rewardTokens), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        if (hasComparableAmounts) {
            Row(
                Modifier.fillMaxWidth().height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (gasWeight > 0f) {
                    Box(
                        Modifier.weight(gasWeight).fillMaxHeight().background(
                            gasColor,
                            if (rewardWeight == 0f) CircleShape else RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (gasWeight >= 0.15f) {
                            Text("${(gasWeight * 100).roundToInt()}%", color = Color.White,
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (rewardWeight > 0f) {
                    Box(
                        Modifier.weight(rewardWeight).fillMaxHeight().background(
                            rewardColor,
                            if (gasWeight == 0f) CircleShape else RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 50.dp, bottomEnd = 50.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (rewardWeight >= 0.15f) {
                            Text("${(rewardWeight * 100).roundToInt()}%", color = Color.White,
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Text("暂无可比较的 Gas / 收益数据", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatCompactUsd(value: Double): String = when {
    value >= 1_000_000 -> String.format(java.util.Locale.US, "$%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(java.util.Locale.US, "$%.1fK", value / 1_000.0)
    else -> String.format(java.util.Locale.US, "$%.0f", value.coerceAtLeast(0.0))
}

private fun tokenHint(tokens: List<CommunityTokenAggregateDto>): String =
    tokens.takeIf { it.isNotEmpty() }?.let { "≈ " + formatCommunityTokenBreakdown(it) } ?: "≈ 暂无代币明细"

