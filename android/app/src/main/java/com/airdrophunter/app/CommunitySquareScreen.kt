package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.CommunityProjectStatsDto
import com.airdrophunter.app.data.CommunityProjectSubmissionRequest
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.appPressFeedback
import com.airdrophunter.app.ui.components.AppChip
import com.airdrophunter.app.ui.components.AppTextField
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.StaggeredListItem
import com.airdrophunter.app.ui.components.AppIconButton
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.components.AppButtonStyle
import java.time.Instant
import java.util.Locale

private data class CommunityProject(
    val id: String, val name: String, val chain: String, val logo: String,
    val reward: Double, val gas: Double, val createdAt: String,
    val tags: List<String>,
    val averageRating: Double? = null, val ratingCount: Long = 0,
    val gasByVerificationTier: Map<String, Double> = emptyMap(),
    val isActive: Boolean = true,
    val supportedChains: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val participationRequirements: List<String> = emptyList(),
    val fundingRequirement: String = "unknown",
    val campaignStage: String = "unknown",
    val rewardTypes: List<String> = emptyList(),
    val riskFlags: List<String> = emptyList(),
    val effortLevel: String = "unknown"
) {
    val hasContributionData: Boolean get() = gas > 0.0 || reward > 0.0
}

private enum class CommunityRanking(val titleRes: Int) {
    REWARD(R.string.comm_rank_reward), GAS(R.string.comm_rank_gas), NEW(R.string.comm_rank_new)
}

@Composable
private fun communityProjectLabel(project: CommunityProject): String = when {
    !project.isActive -> stringResource(R.string.comm_inactive)
    project.hasContributionData -> stringResource(R.string.comm_network_data)
    else -> stringResource(R.string.comm_no_contribution)
}

@Composable
private fun communityReputationLabel(project: CommunityProject): String = when {
    project.ratingCount < 5L || project.averageRating == null -> stringResource(R.string.comm_rating_few)
    project.averageRating >= 4.5 -> stringResource(R.string.comm_rating_excellent)
    project.averageRating >= 4.0 -> stringResource(R.string.comm_rating_good)
    project.averageRating >= 3.0 -> stringResource(R.string.comm_rating_mixed)
    else -> stringResource(R.string.comm_rating_low)
}

@Composable
fun CommunitySquareScreen(
    modifier: Modifier = Modifier,
    onProjectSelected: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val joinedIds by CacheStore.observeUserProjects().collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by CacheStore.observeCommunityFavorites().collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteIds = remember(favorites) { favorites.map { it.projectOfficialId }.toSet() }
    val joinedOfficialIds = remember(joinedIds) { joinedIds.mapNotNull { it.projectOfficialId }.toSet() }
    var catalogEntries by remember { mutableStateOf<List<com.airdrophunter.app.data.CommunityProjectCatalogDto>>(emptyList()) }
    var communityStats by remember { mutableStateOf<Map<String, CommunityProjectStatsDto>>(emptyMap()) }
    var statsLoading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var ranking by remember { mutableStateOf(CommunityRanking.REWARD) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showAirdropFilters by remember { mutableStateOf(false) }
    var airdropFilterSelection by remember { mutableStateOf(AirdropFilterSelection()) }
    val visibleAirdropFilterGroups = remember(catalogEntries) { airdropFilterGroupsFor(catalogEntries) }
    var searchQuery by remember { mutableStateOf("") }
    var showSubmissionDialog by remember { mutableStateOf(false) }
    var submissionName by remember { mutableStateOf("") }
    var submissionUrl by remember { mutableStateOf("") }
    var submittingProject by remember { mutableStateOf(false) }
    var addingProjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        ApiClient.initialize(context)

        fun applyOverallSnapshot(snapshot: com.airdrophunter.app.data.CommunitySnapshotDto?) {
            if (snapshot == null) return
            // A snapshot is a metrics source, not the authoritative directory. It is
            // only a first-install display fallback until the directory endpoint responds.
            if (catalogEntries.isEmpty()) {
                catalogEntries = snapshot.projects.map { project ->
                    com.airdrophunter.app.data.CommunityProjectCatalogDto(
                        project_official_id = project.project_official_id,
                        name = project.name,
                        chain = project.chain,
                        logo = project.logo,
                        description = project.description,
                        tags = project.tags,
                        website_url = project.website_url,
                        created_at = project.created_at,
                        is_active = true
                    )
                }
            }
            communityStats = snapshot.projects.associate { project ->
                project.project_official_id to CommunityProjectStatsDto(
                    project_official_id = project.project_official_id,
                    total_gas_cost_usd = project.gas_cost_usd_valuation,
                    total_actual_reward_usd = project.actual_reward_usd_valuation,
                    total_reward_usd = project.reward_usd_valuation.takeIf { it > 0.0 } ?: project.actual_reward_usd_valuation,
                    total_estimated_reward_usd = project.estimated_reward_usd_valuation,
                    gas_by_verification_tier = project.gas_by_verification_tier,
                    data_quality = snapshot.data_quality,
                    average_rating = project.average_rating,
                    rating_count = project.rating_count
                )
            }
        }

        // The official directory and public metric snapshots are separate datasets.
        // Paint the last successful Room directory immediately; network failure never
        // changes an entry's active state or removes a locally cached official ID.
        val cachedCatalog = com.airdrophunter.app.data.CommunityCatalogRepository.readCatalog()
        if (!cachedCatalog.isNullOrEmpty()) catalogEntries = cachedCatalog
        val cachedOverall = com.airdrophunter.app.data.CommunitySnapshotRepository.readSnapshot("overall")
        applyOverallSnapshot(cachedOverall)
        try {
            val refreshedCatalog = com.airdrophunter.app.data.CommunityCatalogRepository.refreshCatalog()
            catalogEntries = refreshedCatalog
            catalogError = null
        } catch (_: Exception) {
            if (catalogEntries.isEmpty() && cachedOverall == null) {
                catalogError = context.getString(R.string.comm_catalog_error)
            }
        }
        try {
            val refreshed = com.airdrophunter.app.data.CommunitySnapshotRepository.refreshSnapshots()
            applyOverallSnapshot(refreshed["overall"] ?: cachedOverall)
        } catch (_: Exception) {
            // A failed CDN request must never erase the last good Room snapshot.
            applyOverallSnapshot(cachedOverall)
        } finally {
            statsLoading = false
        }
    }

    val realProjects = catalogEntries.map { entry ->
        val stats = communityStats[entry.project_official_id]
        CommunityProject(
            id = entry.project_official_id,
            name = entry.name,
            chain = entry.chain,
            logo = entry.logo.ifBlank { entry.name.take(1).uppercase() },
            reward = stats?.total_reward_usd ?: stats?.total_actual_reward_usd ?: 0.0,
            gas = stats?.total_gas_cost_usd ?: 0.0,
            createdAt = entry.created_at,
            tags = entry.tags,
            averageRating = stats?.average_rating,
            ratingCount = stats?.rating_count ?: 0L,
            gasByVerificationTier = stats?.gas_by_verification_tier.orEmpty(),
            isActive = entry.is_active,
            supportedChains = entry.supported_chains.orEmpty(),
            categories = entry.categories.orEmpty(),
            participationRequirements = entry.participation_requirements.orEmpty(),
            fundingRequirement = entry.funding_requirement.takeUnless { it.isNullOrBlank() } ?: "unknown",
            campaignStage = entry.campaign_stage.takeUnless { it.isNullOrBlank() } ?: "unknown",
            rewardTypes = entry.reward_types.orEmpty(),
            riskFlags = entry.risk_flags.orEmpty(),
            effortLevel = entry.effort_level.takeUnless { it.isNullOrBlank() } ?: "unknown"
        )
    }
    val displayedProjects = realProjects.filter { project ->
        // Inactive entries remain in the cached directory for the personal library.
        project.isActive &&
            (!favoritesOnly || project.id in favoriteIds) &&
            matchesCommunityProjectQuery(project.name, project.chain, project.tags, searchQuery) &&
            matchesAirdropFilters(
                AirdropFilterMetadata(
                    chain = project.chain,
                    supportedChains = project.supportedChains,
                    categories = project.categories,
                    participationRequirements = project.participationRequirements,
                    fundingRequirement = project.fundingRequirement,
                    campaignStage = project.campaignStage,
                    rewardTypes = project.rewardTypes,
                    riskFlags = project.riskFlags,
                    effortLevel = project.effortLevel
                ),
                airdropFilterSelection
            )
    }
        .let { filtered ->
            when (ranking) {
                CommunityRanking.REWARD -> filtered.sortedByDescending { it.reward }
                CommunityRanking.GAS -> filtered.sortedByDescending { it.gas }
                CommunityRanking.NEW -> filtered.sortedByDescending {
                    runCatching { Instant.parse(it.createdAt).toEpochMilli() }.getOrDefault(0L)
                }
            }
        }

    LazyColumn(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.comm_title),
                        modifier = Modifier.weight(1f).semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    AppIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.comm_submit_title),
                        onClick = { showSubmissionDialog = true }
                    )
                }
                Text(
                    stringResource(R.string.comm_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(AppDesignTokens.Radius.Md.dp))
                        .then(Modifier)
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.comm_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = stringResource(R.string.comm_search_hint),
                        heightDp = 44,
                        trailingClear = true
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppButton(
                        text = if (airdropFilterSelection.selectedCount == 0) stringResource(R.string.comm_filter) else stringResource(R.string.comm_filter_count, airdropFilterSelection.selectedCount),
                        onClick = { showAirdropFilters = true },
                        modifier = Modifier.weight(1f),
                        style = AppButtonStyle.SECONDARY,
                        fontSizeSp = 14
                    )
                    AppChip(stringResource(R.string.comm_favorites), favoritesOnly, onClick = { favoritesOnly = !favoritesOnly })
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                    Text(
                        stringResource(R.string.comm_public_data),
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.comm_rankings), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommunityRanking.values().forEach { option ->
                        AppChip(
                            text = stringResource(option.titleRes),
                            selected = ranking == option,
                            onClick = { ranking = option }
                        )
                    }
                }
                Text(when {
                    statsLoading -> stringResource(R.string.comm_loading_stats)
                    ranking == CommunityRanking.REWARD -> stringResource(R.string.comm_rank_reward_hint)
                    ranking == CommunityRanking.GAS -> stringResource(R.string.comm_rank_gas_hint)
                    else -> stringResource(R.string.comm_rank_new_hint)
                }, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            Text(
                when {
                    statsLoading -> stringResource(R.string.comm_loading_catalog)
                    catalogError != null && catalogEntries.isEmpty() -> catalogError!!
                    else -> stringResource(R.string.comm_project_count, displayedProjects.size) + if (statsLoading) stringResource(R.string.comm_stats_loading_suffix) else ""
                },
                color = if (catalogError != null && catalogEntries.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (displayedProjects.isEmpty()) {
            item {
                Text(
                    when {
                        airdropFilterSelection.selectedCount > 0 -> stringResource(R.string.comm_empty_filter)
                        favoritesOnly && searchQuery.isNotBlank() -> stringResource(R.string.comm_empty_favorite_search)
                        favoritesOnly -> stringResource(R.string.comm_empty_favorites)
                        searchQuery.isNotBlank() -> stringResource(R.string.comm_empty_search)
                        else -> stringResource(R.string.comm_empty)
                    },
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        itemsIndexed(displayedProjects, key = { _, project -> project.id }) { index, project ->
            StaggeredListItem(project.id, index) {
            Card(
                modifier = Modifier.fillMaxWidth().appPressFeedback(
                    onClick = { onProjectSelected(project.id, project.name, project.chain) },
                    scale = 0.96f
                ),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(project.logo, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp) }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                project.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                project.chain,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                color = if (!project.isActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)
                            ) {
                                Text(
                                    communityProjectLabel(project), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (!project.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        AppIconButton(
                            icon = if (project.id in joinedOfficialIds) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (project.id in joinedOfficialIds) stringResource(R.string.comm_added_to_library) else stringResource(R.string.comm_add_to_library),
                            enabled = project.id in joinedOfficialIds || project.id !in addingProjectIds,
                            onClick = {
                                if (project.id !in joinedOfficialIds) scope.launch {
                                    addingProjectIds = addingProjectIds + project.id
                                    val added = runCatching {
                                        CacheStore.joinCommunityProject(project.id, project.name)
                                    }.getOrDefault(false)
                                    addingProjectIds = addingProjectIds - project.id
                                    Toast.makeText(
                                        context,
                                        if (added) context.getString(R.string.comm_added_project, project.name) else context.getString(R.string.comm_add_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                        AppIconButton(
                            icon = if (project.id in favoriteIds) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (project.id in favoriteIds) stringResource(R.string.comm_unfavorite) else stringResource(R.string.comm_favorite),
                            onClick = {
                                scope.launch {
                                    // Favorites are personal local data in the current release.
                                    CacheStore.toggleCommunityFavorite(project.id, project.name)
                                }
                            }
                        )
                    }
                    Text(
                        if (project.ratingCount > 0 && project.averageRating != null)
                            stringResource(R.string.comm_rating_line, String.format(Locale.US, "%.1f", project.averageRating), communityReputationLabel(project), project.ratingCount)
                        else stringResource(R.string.comm_no_rating),
                        color = if (project.ratingCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    val hasContributionData = project.gas > 0.0 || project.reward > 0.0
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.comm_reward_summary), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (hasContributionData) String.format(Locale.US, "$%.2f", project.reward.coerceAtLeast(0.0)) else stringResource(R.string.comm_no_data),
                                color = if (hasContributionData) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.comm_gas_spend), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (hasContributionData) String.format(Locale.US, "$%.2f", project.gas.coerceAtLeast(0.0)) else stringResource(R.string.comm_no_data),
                                color = if (hasContributionData) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    val totalGasReward = project.gas.coerceAtLeast(0.0) + project.reward.coerceAtLeast(0.0)
                    if (totalGasReward > 0.0) {
                        val gasShare = (project.gas.coerceAtLeast(0.0) / totalGasReward).toFloat().coerceIn(0f, 1f)
                        val comparisonBrush = when {
                            gasShare <= 0f -> SolidColor(MaterialTheme.colorScheme.secondary)
                            gasShare >= 1f -> SolidColor(MaterialTheme.colorScheme.error)
                            else -> Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to MaterialTheme.colorScheme.error,
                                    gasShare to MaterialTheme.colorScheme.error,
                                    gasShare to MaterialTheme.colorScheme.secondary,
                                    1f to MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                        Box(
                            Modifier.fillMaxWidth().height(6.dp)
                                .background(comparisonBrush, RoundedCornerShape(AppDesignTokens.Radius.Full.dp))
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        project.tags.forEach { tag ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp)) {
                                Text(tag, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }


                }
            }
            }
        }
    }

    if (showAirdropFilters) {
        AlertDialog(
            onDismissRequest = { showAirdropFilters = false },
            title = { Text(stringResource(R.string.comm_filter_title)) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.comm_filter_explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    visibleAirdropFilterGroups.forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(group.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            group.options.chunked(3).forEach { optionRow ->
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    optionRow.forEach { option ->
                                        val selected = option.value in airdropFilterSelection.values[group.key].orEmpty()
                                        AppChip(
                                            text = option.label,
                                            selected = selected,
                                            onClick = { airdropFilterSelection = airdropFilterSelection.toggle(group.key, option.value) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppTextButton(onClick = { showAirdropFilters = false }) { Text(stringResource(R.string.comm_filter_done, displayedProjects.size)) }
            },
            dismissButton = {
                AppTextButton(onClick = { airdropFilterSelection = AirdropFilterSelection() }) { Text(stringResource(R.string.comm_reset)) }
            }
        )
    }

    if (showSubmissionDialog) {
        AlertDialog(
            onDismissRequest = { if (!submittingProject) showSubmissionDialog = false },
            title = { Text(stringResource(R.string.comm_submit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.comm_submit_explanation), style = MaterialTheme.typography.bodySmall)
                    AppTextField(
                        value = submissionName,
                        onValueChange = { submissionName = it },
                        placeholder = stringResource(R.string.comm_project_name),
                        enabled = !submittingProject
                    )
                    AppTextField(
                        value = submissionUrl,
                        onValueChange = { submissionUrl = it },
                        placeholder = stringResource(R.string.comm_project_url),
                        enabled = !submittingProject
                    )
                }
            },
            confirmButton = {
                AppTextButton(
                    enabled = !submittingProject && submissionName.isNotBlank() && submissionUrl.isNotBlank(),
                    onClick = {
                        scope.launch {
                            submittingProject = true
                            runCatching {
                                ApiClient.initialize(context)
                                ApiClient.service.submitCommunityProject(
                                    CommunityProjectSubmissionRequest(
                                        name = submissionName.trim(),
                                        url = submissionUrl.trim()
                                    )
                                )
                            }.onSuccess { result ->
                                Toast.makeText(
                                    context,
                                    result.message + (result.existing_project?.let { "：${it.name}" } ?: ""),
                                    Toast.LENGTH_LONG
                                ).show()
                                if (result.status == "published") {
                                    runCatching {
                                        catalogEntries = com.airdrophunter.app.data.CommunityCatalogRepository.refreshCatalog().filter { it.is_active }
                                    }
                                }
                                submissionName = ""
                                submissionUrl = ""
                                showSubmissionDialog = false
                            }.onFailure {
                                Toast.makeText(context, context.getString(R.string.comm_submit_failed), Toast.LENGTH_LONG).show()
                            }
                            submittingProject = false
                        }
                    }
                ) {
                    if (submittingProject) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.comm_submit))
                }
            },
            dismissButton = {
                AppTextButton(
                    onClick = { showSubmissionDialog = false },
                    enabled = !submittingProject
                ) { Text(stringResource(R.string.comm_cancel)) }
            }
        )
    }
}
