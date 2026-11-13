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
    val label: String get() = when {
        !isActive -> "社区已下架"
        gas > 0.0 || reward > 0.0 -> "全网数据"
        else -> "暂无贡献"
    }
    val reputationLabel: String get() = when {
        ratingCount < 5L || averageRating == null -> "评分样本较少"
        averageRating >= 4.5 -> "好评如潮"
        averageRating >= 4.0 -> "整体较好"
        averageRating >= 3.0 -> "褒贬不一"
        else -> "口碑偏低"
    }
}

private enum class CommunityRanking(val title: String) {
    REWARD("吸金榜"), GAS("避坑榜"), NEW("新星榜")
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
                catalogError = "社区目录暂不可用，联网后会自动重试"
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
                        "微社区广场",
                        modifier = Modifier.weight(1f).semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                    AppIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = "提交新空投项目",
                        onClick = { showSubmissionDialog = true }
                    )
                }
                Text(
                    "发现值得关注的 Web3 项目，查看社区真实参与数据与口碑",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(AppDesignTokens.Radius.Full.dp)).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = "搜索社区项目",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = "搜索项目名称、链或标签",
                        heightDp = 44,
                        trailingClear = true
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppButton(
                        text = if (airdropFilterSelection.selectedCount == 0) "筛选空投条件" else "筛选空投条件 · ${airdropFilterSelection.selectedCount}",
                        onClick = { showAirdropFilters = true },
                        modifier = Modifier.weight(1f),
                        style = AppButtonStyle.SECONDARY,
                        fontSizeSp = 14
                    )
                    AppChip("我的收藏", favoritesOnly, onClick = { favoritesOnly = !favoritesOnly })
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                    Text(
                        "社区公开数据 · 脱敏汇总",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("社区榜单", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommunityRanking.values().forEach { option ->
                        AppChip(
                            text = option.title,
                            selected = ranking == option,
                            onClick = { ranking = option }
                        )
                    }
                }
                Text(when {
                    statsLoading -> "正在读取社区统计…"
                    ranking == CommunityRanking.REWARD -> "按社区收益概况排列（含预估与实际）"
                    ranking == CommunityRanking.GAS -> "按社区 Gas 支出排列"
                    else -> "按项目收录时间排列"
                }, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            Text(
                when {
                    statsLoading -> "正在加载社区项目目录…"
                    catalogError != null && catalogEntries.isEmpty() -> catalogError!!
                    else -> "发现 ${displayedProjects.size} 个项目" + if (statsLoading) " · 统计加载中" else ""
                },
                color = if (catalogError != null && catalogEntries.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (displayedProjects.isEmpty()) {
            item {
                Text(
                    when {
                        airdropFilterSelection.selectedCount > 0 -> "没有找到符合筛选条件的项目，可以重置条件再试"
                        favoritesOnly && searchQuery.isNotBlank() -> "没有找到符合搜索条件的收藏项目"
                        favoritesOnly -> "还没有收藏项目，点击项目卡片上的爱心即可收藏"
                        searchQuery.isNotBlank() -> "没有找到匹配的社区项目"
                        else -> "暂无符合条件的项目"
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
                                    project.label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (!project.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        AppIconButton(
                            icon = if (project.id in joinedOfficialIds) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (project.id in joinedOfficialIds) "已添加到库管理" else "添加到库管理",
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
                                        if (added) "${project.name} 已加入库管理" else "添加失败，请稍后重试",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                        AppIconButton(
                            icon = if (project.id in favoriteIds) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (project.id in favoriteIds) "取消收藏" else "收藏项目",
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
                            "★ ${String.format(Locale.US, "%.1f", project.averageRating)} · ${project.reputationLabel} · ${project.ratingCount} 个账号评分"
                        else "暂无全网评分",
                        color = if (project.ratingCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    val hasContributionData = project.gas > 0.0 || project.reward > 0.0
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("收益概况 · 含预估", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (hasContributionData) String.format(Locale.US, "$%.2f", project.reward.coerceAtLeast(0.0)) else "暂无数据",
                                color = if (hasContributionData) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Gas 支出", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (hasContributionData) String.format(Locale.US, "$%.2f", project.gas.coerceAtLeast(0.0)) else "暂无数据",
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
            title = { Text("空投条件筛选") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("同一组内多选为“满足任一项”，不同组之间为“同时满足”。只按目录中已核验/已录入的信息筛选，未标注条件不会被猜测。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                AppTextButton(onClick = { showAirdropFilters = false }) { Text("完成 · ${displayedProjects.size} 个结果") }
            },
            dismissButton = {
                AppTextButton(onClick = { airdropFilterSelection = AirdropFilterSelection() }) { Text("重置条件") }
            }
        )
    }

    if (showSubmissionDialog) {
        AlertDialog(
            onDismissRequest = { if (!submittingProject) showSubmissionDialog = false },
            title = { Text("提交新空投项目") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("只需填写项目名称和链接。可以是官网，也可以是第三方空投平台的项目详情页。", style = MaterialTheme.typography.bodySmall)
                    AppTextField(
                        value = submissionName,
                        onValueChange = { submissionName = it },
                        placeholder = "项目名称",
                        enabled = !submittingProject
                    )
                    AppTextField(
                        value = submissionUrl,
                        onValueChange = { submissionUrl = it },
                        placeholder = "项目链接（HTTPS）",
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
                                Toast.makeText(context, "提交失败，请检查名称和 HTTPS 链接后重试", Toast.LENGTH_LONG).show()
                            }
                            submittingProject = false
                        }
                    }
                ) {
                    if (submittingProject) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("提交")
                }
            },
            dismissButton = {
                AppTextButton(
                    onClick = { showSubmissionDialog = false },
                    enabled = !submittingProject
                ) { Text("取消") }
            }
        )
    }
}
