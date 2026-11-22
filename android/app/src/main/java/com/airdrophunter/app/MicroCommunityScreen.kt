package com.airdrophunter.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppButtonStyle
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.appPressFeedback
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.CommunityTokenAggregateDto
import com.airdrophunter.app.data.formatCommunityTokenBreakdown
import com.airdrophunter.app.data.WalletDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

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
    var periodSnapshots by remember(projectOfficialId) { mutableStateOf<Map<String, com.airdrophunter.app.data.CommunitySnapshotDto>>(emptyMap()) }
    var statsLoading by remember(projectOfficialId) { mutableStateOf(true) }
    var statsFailed by remember(projectOfficialId) { mutableStateOf(false) }
    var selectedPeriod by remember(projectOfficialId) { mutableStateOf("overall") }
    var wallet by remember(projectOfficialId, sessionScopeKey) { mutableStateOf("") }
    var canRate by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    var participationChecked by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    var selectedScore by remember(projectOfficialId, sessionScopeKey) { mutableIntStateOf(0) }
    var saved by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    var saving by remember(projectOfficialId, sessionScopeKey) { mutableStateOf(false) }
    val myRating = ratings.firstOrNull { it.walletAddress == wallet }
    val snapshotProjects = remember(periodSnapshots, projectOfficialId) {
        periodSnapshots.mapValues { (_, snapshot) ->
            snapshot.projects.firstOrNull { it.project_official_id == projectOfficialId }
        }
    }
    val overallProject = snapshotProjects["overall"]
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
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(projectName, modifier = Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.micro_rating_intro), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                AppButton(
                    text = if (isJoined) stringResource(R.string.micro_added_to_library) else stringResource(R.string.micro_add_to_library),
                    onClick = {
                        scope.launch {
                            val added = runCatching {
                                CacheStore.joinCommunityProject(projectOfficialId, projectName)
                            }.getOrDefault(false)
                            Toast.makeText(
                                context,
                                if (added) context.getString(R.string.micro_added_project, projectName) else context.getString(R.string.micro_add_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = !isJoined,
                    style = AppButtonStyle.SECONDARY,
                    leadingIcon = if (isJoined) Icons.Default.Check else Icons.Default.Add
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.micro_overview), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(overallProject?.chain ?: stringResource(R.string.micro_public_stats), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    overallProject?.logo?.takeIf { it.isNotBlank() }?.let { logo ->
                        Surface(shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp), color = MaterialTheme.colorScheme.surface) {
                            Text(logo, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val detailDescription = overallProject?.description?.takeIf { it.isNotBlank() }
                detailDescription?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                overallProject?.website_url?.takeIf { it.startsWith("https://", true) }?.let { url ->
                    AppTextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                        Text(stringResource(R.string.micro_website, url), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }

                Card(
                    shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("overall" to stringResource(R.string.micro_period_overall), "3d" to stringResource(R.string.micro_period_3d), "1h" to stringResource(R.string.micro_period_1h)).forEach { (window, label) ->
                                val selected = selectedPeriod == window
                                Surface(
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                        .appPressFeedback(
                                            onClick = { selectedPeriod = window },
                                            scale = 1f,
                                            role = androidx.compose.ui.semantics.Role.Tab
                                        )
                                        .semantics { this.selected = selected },
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                ) {
                                    Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        AnimatedContent(
                            targetState = selectedPeriod,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "community-period-switch"
                        ) { window ->
                            val projectSnapshot = snapshotProjects[window]
                            val gas = projectSnapshot?.gas_cost_usd_valuation ?: 0.0
                            val reward = projectSnapshot?.reward_usd_valuation?.takeIf { it > 0.0 }
                                ?: projectSnapshot?.actual_reward_usd_valuation ?: 0.0
                            val gasTokens = projectSnapshot?.gas_by_token.orEmpty()
                            val rewardTokens = projectSnapshot?.reward_by_token.orEmpty()
                            CommunitySnapshotPeriodMetric(
                                gas = gas,
                                reward = reward,
                                gasTokens = gasTokens,
                                rewardTokens = rewardTokens,
                                hasData = gas > 0.0 || reward > 0.0 ||
                                    gasTokens.isNotEmpty() || rewardTokens.isNotEmpty()
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(
                                when {
                                    statsLoading -> context.getString(R.string.micro_loading_snapshot)
                                    statsFailed -> context.getString(R.string.micro_no_snapshot_cache)
                                    else -> context.getString(R.string.micro_updated_at, periodSnapshots[selectedPeriod]?.update_time?.replace("T", " ") ?: context.getString(R.string.micro_not_available))
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            Card(
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.micro_local_ratings), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (ratings.isEmpty()) stringResource(R.string.micro_no_local_ratings)
                        else stringResource(R.string.micro_local_rating_summary, ratings.size, String.format(java.util.Locale.US, "%.1f", average ?: 0.0)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall
                    )
                    ratings.take(3).forEach { rating ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.micro_local_rating_record),
                                Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            Text("★".repeat(rating.score), color = Color(0xFFFFB12B), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Card(
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.micro_rate_project), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { score ->
                            Icon(
                                imageVector = if (score <= selectedScore) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.micro_score_stars, score),
                                tint = if (score <= selectedScore) Color(0xFFFFB12B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp).appPressFeedback(
                                    onClick = {
                                        if (!participationChecked || !canRate) {
                                            Toast.makeText(context, context.getString(R.string.micro_rate_participants_only), Toast.LENGTH_SHORT).show()
                                        } else selectedScore = score
                                    },
                                    enabled = !saved,
                                    scale = 0.94f
                                )
                            )
                        }
                    }
                    if (!canRate) {
                        Text(stringResource(R.string.micro_rate_participants_only), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    } else if (saved) {
                        Text(stringResource(R.string.micro_saved_locally), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text(stringResource(R.string.micro_rating_local_only), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    AppButton(
                        text = if (saved) stringResource(R.string.micro_rated) else stringResource(R.string.micro_save_rating),
                        onClick = {
                            if (!participationChecked || !canRate) {
                                Toast.makeText(context, context.getString(R.string.micro_rate_with_ledger_only), Toast.LENGTH_SHORT).show()
                            } else if (selectedScore in 1..5 && !saved && !saving) {
                                saving = true
                                scope.launch {
                                    val ok = CacheStore.saveProjectRating(projectOfficialId, wallet, selectedScore)
                                    saving = false
                                    saved = ok
                                    if (!ok) Toast.makeText(context, context.getString(R.string.micro_rating_save_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = canRate && selectedScore in 1..5 && !saved && !saving,
                        loading = saving
                    )
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
                Text(stringResource(R.string.micro_gas_spend), color = gasColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(formatCompactUsd(gas), color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(tokenHint(gasTokens), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.micro_reward_income), color = rewardColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
                            Text("${(gasWeight * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onError,
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
                            Text("${(rewardWeight * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSecondary,
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Text(stringResource(R.string.micro_no_comparable_data), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatCompactUsd(value: Double): String = when {
    value >= 1_000_000 -> String.format(java.util.Locale.US, "$%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(java.util.Locale.US, "$%.1fK", value / 1_000.0)
    else -> String.format(java.util.Locale.US, "$%.0f", value.coerceAtLeast(0.0))
}

@Composable
private fun tokenHint(tokens: List<CommunityTokenAggregateDto>): String =
    tokens.takeIf { it.isNotEmpty() }?.let { "≈ " + formatCommunityTokenBreakdown(it) } ?: "≈ " + stringResource(R.string.micro_no_token_details)

