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
    val labelRes: Int get() = when {
        !isActive -> R.string.community_removed
        gas > 0.0 || reward > 0.0 -> R.string.community_all_data
        else -> R.string.community_no_contributions
    }
    val reputationRes: Int get() = when {
        ratingCount < 5L || averageRating == null -> R.string.community_rating_sample_small
        averageRating >= 4.5 -> R.string.community_positive
        averageRating >= 4.0 -> R.string.community_good
        averageRating >= 3.0 -> R.string.community_mixed
        else -> R.string.community_low
    }
}

private enum class CommunityRanking(val titleRes: Int) {
    REWARD(R.string.community_rank_reward), GAS(R.string.community_rank_gas), NEW(R.string.community_rank_new)
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
                catalogError = context.getString(R.string.community_catalog_error)
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
                        context.getString(R.string.community_square_title),
                        modifier = Modifier.weight(1f).semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                    AppIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = context.getString(R.string.community_submit_project),
                        onClick = { showSubmissionDialog = true }
                    )
                }
                Text(
                    context.getString(R.string.community_intro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(AppDesignTokens.Radius.Full.dp)).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = context.getString(R.string.community_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = context.getString(R.string.community_search_hint),
                        heightDp = 44,
                        trailingClear = true
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppButton(
                        text = if (airdropFilterSelection.selectedCount == 0) context.getString(R.string.community_filter_airdrop) else context.getString(R.string.community_filter_count, airdropFilterSelection.selectedCount),
                        onClick = { showAirdropFilters = true },
                        modifier = Modifier.weight(1f),
                        style = AppButtonStyle.SECONDARY,
                        fontSizeSp = 14
                    )
                    AppChip(context.getString(R.string.community_favorites), favoritesOnly, onClick = { favoritesOnly = !favoritesOnly })
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                    Text(
                        context.getString(R.string.community_public_data),
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(context.getString(R.string.community_rankings), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommunityRanking.values().forEach { option ->
                        AppChip(
                            text = context.getString(option.titleRes),
                            selected = ranking == option,
                            onClick = { ranking = option }
                        )
                    }
                }
                Text(when {
                    statsLoading -> context.getString(R.string.community_loading_stats)
                    ranking == CommunityRanking.REWARD -> context.getString(R.string.community_rank_reward_help)
                    ranking == CommunityRanking.GAS -> context.getString(R.string.community_rank_gas_help)
                    else -> context.getString(R.string.community_rank_new_help)
                }, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            Text(
                when {
                    statsLoading -> context.getString(R.string.community_loading_directory)
                    catalogError != null && catalogEntries.isEmpty() -> catalogError!!
                    else -> context.getString(R.string.community_project_count, displayedProjects.size) + if (statsLoading) context.getString(R.string.community_stats_loading_suffix) else ""
                },
                color = if (catalogError != null && catalogEntries.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (displayedProjects.isEmpty()) {
            item {
                Text(
                    when {
                        airdropFilterSelection.selectedCount > 0 -> context.getString(R.string.community_no_filter_match)
                        favoritesOnly && searchQuery.isNotBlank() -> context.getString(R.string.community_no_favorite_search)
                        favoritesOnly -> context.getString(R.string.community_favorites_empty)
                        searchQuery.isNotBlank() -> context.getString(R.string.community_no_search_match)
                        else -> context.getString(R.string.community_no_projects)
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
                                    context.getString(project.labelRes), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (!project.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        AppIconButton(
                            icon = if (project.id in joinedOfficialIds) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (project.id in joinedOfficialIds) context.getString(R.string.community_added_to_library) else context.getString(R.string.community_add_to_library),
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
                                        if (added) context.getString(R.string.community_joined_project, project.name) else context.getString(R.string.community_add_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                        AppIconButton(
                            icon = if (project.id in favoriteIds) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (project.id in favoriteIds) context.getString(R.string.community_remove_favorite) else context.getString(R.string.community_add_favorite),
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
                            context.getString(R.string.community_rating_summary, String.format(Locale.US, "%.1f", project.averageRating), context.getString(project.reputationRes), project.ratingCount)
                        else context.getString(R.string.community_no_global_rating),
                        color = if (project.ratingCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    val hasContributionData = project.gas > 0.0 || project.reward > 0.0
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(context.getString(R.string.community_reward_overview), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (hasContributionData) String.format(Locale.US, "$%.2f", project.reward.coerceAtLeast(0.0)) else context.getString(R.string.community_no_data),
                                color = if (hasContributionData) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(context.getString(R.string.community_gas_spend), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (hasContributionData) String.format(Locale.US, "$%.2f", project.gas.coerceAtLeast(0.0)) else context.getString(R.string.community_no_data),
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
            title = { Text(context.getString(R.string.community_filter_title)) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(context.getString(R.string.community_filter_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    visibleAirdropFilterGroups.forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(localizedAirdropFilterGroupTitle(context, group), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            group.options.chunked(3).forEach { optionRow ->
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    optionRow.forEach { option ->
                                        val selected = option.value in airdropFilterSelection.values[group.key].orEmpty()
                                        AppChip(
                                            text = localizedAirdropFilterOptionLabel(context, group.key, option),
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
                AppTextButton(onClick = { showAirdropFilters = false }) { Text(context.getString(R.string.community_filter_results, displayedProjects.size)) }
            },
            dismissButton = {
                AppTextButton(onClick = { airdropFilterSelection = AirdropFilterSelection() }) { Text(context.getString(R.string.community_filter_reset)) }
            }
        )
    }

    if (showSubmissionDialog) {
        AlertDialog(
            onDismissRequest = { if (!submittingProject) showSubmissionDialog = false },
            title = { Text(context.getString(R.string.community_submit_project)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.community_submit_help), style = MaterialTheme.typography.bodySmall)
                    AppTextField(
                        value = submissionName,
                        onValueChange = { submissionName = it },
                        placeholder = context.getString(R.string.community_project_name),
                        enabled = !submittingProject
                    )
                    AppTextField(
                        value = submissionUrl,
                        onValueChange = { submissionUrl = it },
                        placeholder = context.getString(R.string.community_project_url),
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
                                Toast.makeText(context, context.getString(R.string.community_submit_failed), Toast.LENGTH_LONG).show()
                            }
                            submittingProject = false
                        }
                    }
                ) {
                    if (submittingProject) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(context.getString(R.string.community_submit))
                }
            },
            dismissButton = {
                AppTextButton(
                    onClick = { showSubmissionDialog = false },
                    enabled = !submittingProject
                ) { Text(context.getString(R.string.cancel)) }
            }
        )
    }
}


private fun localizedAirdropFilterGroupTitle(
    context: android.content.Context,
    group: AirdropFilterGroup
): String {
    val language = context.resources.configuration.locales.get(0).language
    if (language.startsWith("zh")) return group.title
    return when (group.key) {
        "supported_chains" -> "Chains / Networks"
        "categories" -> "Project categories"
        "participation_requirements" -> "Participation / Tasks"
        "funding_requirement" -> "Capital requirements"
        "campaign_stage" -> "Project / campaign stage"
        "reward_types" -> "Reward types"
        "risk_flags" -> "Risks / eligibility restrictions"
        "effort_level" -> "Time commitment"
        else -> group.key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

private fun localizedAirdropFilterOptionLabel(
    context: android.content.Context,
    groupKey: String,
    option: AirdropFilterOption
): String {
    val language = context.resources.configuration.locales.get(0).language
    if (language.startsWith("zh")) return option.label
    if (option.value == "unknown") return when (groupKey) {
        "supported_chains" -> "Network unconfirmed"
        "categories" -> "Category unlisted"
        "participation_requirements" -> "Requirements unlisted"
        "funding_requirement" -> "Not specified by official source"
        "campaign_stage" -> "Stage unconfirmed"
        "reward_types" -> "Reward unconfirmed"
        "risk_flags" -> "Risk information unlisted"
        "effort_level" -> "Unknown"
        else -> "Unknown"
    }
    return when (option.value) {
        "bnb-chain" -> "BNB Chain"
        "btcfi" -> "BTCFi"
        "defi" -> "DeFi"
        "nft" -> "NFT"
        "ai" -> "AI"
        "depin" -> "DePIN"
        "rwa" -> "RWA"
        "socialfi" -> "SocialFi"
        "github_developer" -> "GitHub contribution"
        "onchain_interaction" -> "On-chain interaction"
        "hold_token" -> "Hold tokens"
        "hold_nft" -> "Hold NFTs"
        "points_quest" -> "Points / quests"
        "minimum_tx_count" -> "Minimum transaction count"
        "minimum_volume" -> "Minimum volume"
        "active_days" -> "Activity across multiple days / months"
        "unique_contracts" -> "Unique contracts interacted with"
        "gas_spend" -> "Gas spent"
        "snapshot_activity" -> "Pre-snapshot activity"
        "wallet_age" -> "Wallet age"
        "multi_chain" -> "Multi-chain activity"
        "community_contribution" -> "Community contribution"
        "no_extra_capital" -> "No additional capital"
        "gas_only" -> "Gas only"
        "low_capital" -> "Low capital"
        "capital_required" -> "Capital required"
        "locked_capital" -> "Capital lockup required"
        "points_season" -> "Points season"
        "mainnet_activity" -> "Mainnet activity"
        "snapshot_announced" -> "Snapshot announced"
        "claim_open" -> "Claim open"
        "allowlist" -> "Allowlist / eligibility"
        "impermanent_loss" -> "Impermanent loss risk"
        "contract_approval" -> "Contract approval risk"
        "deposit_required" -> "Deposit required"
        "official_task_verification" -> "Official task verification"
        "quick" -> "Short / one-time"
        "recurring" -> "Recurring"
        "long_term" -> "Long-term"
        else -> option.value.replace('_', ' ').replace('-', ' ')
            .split(' ').filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}

