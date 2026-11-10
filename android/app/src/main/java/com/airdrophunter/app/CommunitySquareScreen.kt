package com.airdrophunter.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.time.Instant
import java.util.Locale

private data class CommunityProject(
    val id: String, val name: String, val chain: String, val logo: String, val websiteUrl: String,
    val reward: Double, val gas: Double, val createdAt: String,
    val tags: List<String>, val isDemo: Boolean = false,
    val demoScore: Double = 0.0,
    val averageRating: Double? = null, val ratingCount: Long = 0,
    val gasByVerificationTier: Map<String, Double> = emptyMap()
) {
    val label: String get() = if (isDemo) "测试演示" else if (gas > 0.0 || reward > 0.0) "全网数据" else "暂无贡献"
    val metricWeights: Pair<Float, Float> get() = communityMetricWeights(gas, reward)
    val gasWeight: Float get() = metricWeights.first
    val rewardWeight: Float get() = metricWeights.second
    val reputationLabel: String get() = when {
        isDemo && demoScore >= 4.5 -> "好评如潮"
        isDemo && demoScore >= 4.0 -> "整体较好"
        isDemo && demoScore >= 3.0 -> "褒贬不一"
        isDemo -> "口碑偏低"
        ratingCount < 5L || averageRating == null -> "评分样本较少"
        averageRating >= 4.5 -> "好评如潮"
        averageRating >= 4.0 -> "整体较好"
        averageRating >= 3.0 -> "褒贬不一"
        else -> "口碑偏低"
    }
}

private enum class CommunityRanking(val title: String) {
    REWARD("🔥 吸金榜"), GAS("💀 避坑榜"), NEW("🚀 新星榜")
}


@Composable
fun CommunitySquareScreen(
    modifier: Modifier = Modifier,
    onProjectSelected: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
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
    var useDemoData by remember { mutableStateOf(BuildConfig.DEBUG) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSubmissionDialog by remember { mutableStateOf(false) }
    var submissionName by remember { mutableStateOf("") }
    var submissionUrl by remember { mutableStateOf("") }
    var submittingProject by remember { mutableStateOf(false) }
    var addingProjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val allTags = remember(catalogEntries, useDemoData) {
        if (BuildConfig.DEBUG && useDemoData) listOf("Layer2", "DeFi", "测试网", "零成本", "生态", "跨链", "GameFi", "空投")
        else catalogEntries.flatMap { it.tags }.distinct().sorted()
    }

    LaunchedEffect(Unit) {
        ApiClient.initialize(context)

        fun applyOverallSnapshot(snapshot: com.airdrophunter.app.data.CommunitySnapshotDto?) {
            if (snapshot == null) return
            catalogEntries = snapshot.projects.map { project ->
                com.airdrophunter.app.data.CommunityProjectCatalogDto(
                    project_official_id = project.project_official_id,
                    name = project.name,
                    chain = project.chain,
                    logo = project.logo,
                    description = project.description,
                    tags = project.tags,
                    website_url = project.website_url,
                    created_at = project.created_at
                )
            }
            communityStats = snapshot.projects.associate { project ->
                project.project_official_id to CommunityProjectStatsDto(
                    project_official_id = project.project_official_id,
                    total_gas_cost_usd = project.gas_cost_usd_valuation,
                    total_actual_reward_usd = project.actual_reward_usd_valuation,
                    gas_by_verification_tier = project.gas_by_verification_tier,
                    data_quality = snapshot.data_quality,
                    average_rating = project.average_rating,
                    rating_count = project.rating_count
                )
            }
        }

        // Room is the first paint. CDN revalidation happens silently afterwards.
        val cachedOverall = com.airdrophunter.app.data.CommunitySnapshotRepository.readSnapshot("overall")
        applyOverallSnapshot(cachedOverall)
        try {
            val refreshed = com.airdrophunter.app.data.CommunitySnapshotRepository.refreshSnapshots()
            applyOverallSnapshot(refreshed["overall"] ?: cachedOverall)
            if (catalogEntries.isEmpty()) catalogError = "静态社区快照暂不可用，联网后会自动重试"
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
            websiteUrl = entry.website_url,
            reward = stats?.total_reward_usd ?: stats?.total_actual_reward_usd ?: 0.0,
            gas = stats?.total_gas_cost_usd ?: 0.0,
            createdAt = entry.created_at,
            tags = entry.tags,
            isDemo = false,
            averageRating = stats?.average_rating,
            ratingCount = stats?.rating_count ?: 0L,
            gasByVerificationTier = stats?.gas_by_verification_tier.orEmpty()
        )
    }
    // Synthetic projects exist only in the DEBUG test mode. They never enter the catalog,
    // API, personal project library, ledger, or contribution outbox.
    val demoProjects = listOf(
        CommunityProject("demo:novalayer", "NovaLayer", "Arbitrum", "N", "", 184.20, 42.80,
            "2026-11-10T08:00:00Z", listOf("Layer2", "DeFi", "测试网"), true, 4.8),
        CommunityProject("demo:orbitfi", "OrbitFi", "Base", "O", "", 128.60, 38.60,
            "2026-11-09T08:00:00Z", listOf("DeFi", "零成本", "生态"), true, 4.6),
        CommunityProject("demo:zetabridge", "ZetaBridge", "Optimism", "Z", "", 156.00, 62.00,
            "2026-11-08T08:00:00Z", listOf("跨链", "Layer2"), true, 4.2),
        CommunityProject("demo:pixelquest", "PixelQuest", "BNB Chain", "P", "", 98.00, 51.00,
            "2026-11-07T08:00:00Z", listOf("GameFi", "测试网"), true, 3.8),
        CommunityProject("demo:aetherrollup", "Aether Rollup", "Ethereum", "A", "", 226.00, 73.00,
            "2026-11-06T08:00:00Z", listOf("Layer2", "空投", "生态"), true, 4.9),
        CommunityProject("demo:lumenprotocol", "Lumen Protocol", "Polygon", "L", "", 64.00, 18.50,
            "2026-11-05T08:00:00Z", listOf("DeFi", "零成本"), true, 4.0)
    )
    // Demo and real datasets are mutually exclusive; the mode switch never mixes them.
    val combinedProjects = if (BuildConfig.DEBUG && useDemoData) demoProjects else realProjects
    val displayedProjects = combinedProjects.filter {
        (selectedTag == null || selectedTag in it.tags) &&
            (!favoritesOnly || it.id in favoriteIds) &&
            matchesCommunityProjectQuery(it.name, it.chain, it.tags, searchQuery)
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
        modifier.fillMaxSize().background(AppColors.Background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("微社区广场", color = AppColors.TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("像逛 Steam 一样发现空投项目，按收益、风险和标签探索", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { showSubmissionDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("＋ 提交新空投项目")
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("搜索项目名称、链或标签") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索社区项目") },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                            }
                        }
                    }
                )
                Surface(color = if (useDemoData) Color(0xFFFFF4E5) else Color(0xFFE8F7EF), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        if (useDemoData) "测试演示数据 · 不计入全网统计" else "全网真实数据 · 脱敏汇总",
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = if (useDemoData) Color(0xFF9A5B12) else Color(0xFF19764F),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (BuildConfig.DEBUG) {
                        FilterChip(selected = useDemoData, onClick = { useDemoData = true }, label = { Text("测试演示") })
                    }
                    FilterChip(selected = !useDemoData, onClick = { useDemoData = false }, label = { Text("全网真实") })
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("社区榜单", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CommunityRanking.values().forEach { option ->
                        FilterChip(
                            selected = ranking == option, onClick = { ranking = option },
                            label = { Text(option.title, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE8F3F1), selectedLabelColor = AppColors.Primary,
                                labelColor = AppColors.TextSecondary
                            )
                        )
                    }
                }
                Text(when {
                    useDemoData -> "按测试演示样本的收益和 Gas 排列"
                    statsLoading -> "正在读取全网统计…"
                    ranking == CommunityRanking.REWARD -> "按全网收益概况排列（含预估与实际）"
                    ranking == CommunityRanking.GAS -> "按全网 Gas 支出排列"
                    else -> "按项目收录时间排列"
                }, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("项目标签", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly },
                        label = { Text("我的收藏") }, shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE8F3F1), selectedLabelColor = AppColors.Primary))
                    FilterChip(selected = selectedTag == null, onClick = { selectedTag = null },
                        label = { Text("全部") }, shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE8F3F1), selectedLabelColor = AppColors.Primary))
                    allTags.forEach { tag ->
                        FilterChip(selected = selectedTag == tag, onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text(tag) }, shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFE8F3F1), selectedLabelColor = AppColors.Primary))
                    }
                }
            }
        }
        item {
            Text(
                when {
                    statsLoading -> "正在加载社区项目目录…"
                    catalogError != null && catalogEntries.isEmpty() -> catalogError!!
                    else -> "发现 ${displayedProjects.size} 个项目" + if (statsLoading) " · 统计加载中" else ""
                },
                color = if (catalogError != null && catalogEntries.isEmpty()) AppColors.Danger else AppColors.TextHelper,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (displayedProjects.isEmpty()) {
            item {
                Text(
                    when {
                        favoritesOnly && searchQuery.isNotBlank() -> "没有找到符合搜索条件的收藏项目"
                        favoritesOnly -> "还没有收藏项目，点击项目卡片上的爱心即可收藏"
                        searchQuery.isNotBlank() -> "没有找到匹配的社区项目"
                        else -> "暂无符合条件的项目"
                    },
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    color = AppColors.TextHelper,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        items(displayedProjects, key = { it.id }) { project ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onProjectSelected(project.id, project.name, project.chain) },
                shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                elevation = CardDefaults.cardElevation(0.dp), border = BorderStroke(1.dp, AppColors.Border)
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFFEAF2FF), modifier = Modifier.size(45.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(project.logo, color = Color(0xFF315A9D), fontWeight = FontWeight.ExtraBold, fontSize = 21.sp) }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(project.name, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(project.chain, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                color = if (project.isDemo) Color(0xFFFFF4E5) else Color(0xFFEAF7EF),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    project.label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (project.isDemo) Color(0xFF9A5B12) else Color(0xFF19764F),
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        IconButton(
                            enabled = !project.isDemo && (project.id in joinedOfficialIds || project.id !in addingProjectIds),
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
                        ) {
                            Icon(
                                imageVector = if (project.id in joinedOfficialIds) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = if (project.id in joinedOfficialIds) "已添加到库管理" else "添加到库管理",
                                tint = if (project.id in joinedOfficialIds) AppColors.Success else AppColors.Primary
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                // Favorites are personal local data in the current release.
                                CacheStore.toggleCommunityFavorite(project.id, project.name)
                            }
                        }) {
                            Icon(
                                imageVector = if (project.id in favoriteIds) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (project.id in favoriteIds) "取消收藏" else "收藏项目",
                                tint = if (project.id in favoriteIds) Color(0xFFE65B75) else AppColors.TextHelper
                            )
                        }
                    }
                    if (project.isDemo) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("★ ${String.format(Locale.US, "%.1f", project.demoScore)} · ${project.reputationLabel}",
                                color = Color(0xFFB7791F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            if (project.ratingCount > 0 && project.averageRating != null)
                                "★ ${String.format(Locale.US, "%.1f", project.averageRating)} · ${project.reputationLabel} · ${project.ratingCount} 个账号评分"
                            else "暂无全网评分",
                            color = if (project.ratingCount > 0) Color(0xFFB7791F) else AppColors.TextHelper,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (project.gas > 0.0 || project.reward > 0.0) {
                        val comparisonBrush = when {
                            project.gasWeight <= 0f -> SolidColor(Color(0xFF31B77D))
                            project.rewardWeight <= 0f -> SolidColor(Color(0xFFE76565))
                            else -> Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color(0xFFE76565),
                                    project.gasWeight to Color(0xFFE76565),
                                    project.gasWeight to Color(0xFF31B77D),
                                    1f to Color(0xFF31B77D)
                                )
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(12.dp).background(comparisonBrush, RoundedCornerShape(8.dp)))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(String.format(Locale.US, "Gas USD估值 $%,.2f", project.gas), color = Color(0xFFD34F4F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Text(String.format(Locale.US, "收益 USD估值 $%,.2f", project.reward), color = Color(0xFF16845B), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text("暂无 Gas / 收益数据", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        project.tags.forEach { tag ->
                            Surface(color = Color(0xFFF1F4F7), shape = RoundedCornerShape(6.dp)) {
                                Text(tag, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (project.websiteUrl.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                project.websiteUrl,
                                Modifier.weight(1f),
                                color = AppColors.TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(
                                onClick = {
                                    runCatching { uriHandler.openUri(project.websiteUrl) }
                                        .onFailure {
                                            Toast.makeText(context, "无法打开项目链接", Toast.LENGTH_SHORT).show()
                                        }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("打开链接")
                            }
                        }
                    } else {
                        Text("项目链接待补充", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text("查看评价  ›", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                }
            }
        }
    }

    if (showSubmissionDialog) {
        AlertDialog(
            onDismissRequest = { if (!submittingProject) showSubmissionDialog = false },
            title = { Text("提交新空投项目") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("只需填写项目名称和链接。可以是官网，也可以是第三方空投平台的项目详情页。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = submissionName,
                        onValueChange = { submissionName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("项目名称") },
                        enabled = !submittingProject
                    )
                    OutlinedTextField(
                        value = submissionUrl,
                        onValueChange = { submissionUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("项目链接（HTTPS）") },
                        enabled = !submittingProject
                    )
                }
            },
            confirmButton = {
                TextButton(
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
                                        catalogEntries = ApiClient.service.getCommunityCatalog()
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
                TextButton(
                    onClick = { showSubmissionDialog = false },
                    enabled = !submittingProject
                ) { Text("取消") }
            }
        )
    }
}
