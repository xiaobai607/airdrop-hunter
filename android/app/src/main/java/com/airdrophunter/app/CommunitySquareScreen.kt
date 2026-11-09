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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.CommunityProjectStatsDto
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale

private data class CommunityProject(
    val id: String, val name: String, val chain: String, val logo: String,
    val reward: Double, val gas: Double, val contributionCount: Long,
    val tags: List<String>, val isDemo: Boolean = false,
    val demoScore: Double = 0.0, val demoPositive: Int = 0, val demoReviews: Int = 0,
    val demoRecentInteractions: Long = 0, val recentInteractions24h: Long = 0,
    val averageRating: Double? = null, val ratingCount: Long = 0
) {
    val label: String get() = if (isDemo) "测试演示" else if (contributionCount > 0) "真实贡献" else "暂无贡献"
    val rewardWeight: Float get() =
        if (reward + gas <= 0.0) 0.5f
        else (reward / (reward + gas)).toFloat().coerceIn(0.02f, 0.98f)
}

private enum class CommunityRanking(val title: String) {
    REWARD("🔥 吸金榜"), GAS("💀 避坑榜"), NEW("🚀 新星榜")
}

private val communityProjectCatalog = listOf(
    CommunityProject("linea", "Linea", "Consensys zkEVM", "L", 184.20, 42.80, 1280, listOf("L2", "零撸", "明牌空投"), true, 4.9, 97, 1280, 310),
    CommunityProject("scroll", "Scroll", "zkEVM", "S", 128.60, 38.60, 964, listOf("L2", "需测试网", "明牌空投"), true, 4.8, 95, 964, 205),
    CommunityProject("zksync", "zkSync", "ZK Rollup", "Z", 156.00, 62.00, 1820, listOf("L2", "高Gas", "明牌空投"), true, 4.7, 93, 1820, 480),
    CommunityProject("starknet", "Starknet", "Validity Rollup", "✦", 98.00, 51.00, 1450, listOf("L2", "需测试网", "高Gas"), true, 4.4, 88, 1450, 365),
    CommunityProject("base", "Base", "Ethereum L2", "B", 226.00, 73.00, 2200, listOf("L2", "零撸", "高Gas"), true, 4.9, 96, 2200, 520),
    CommunityProject("arbitrum", "Arbitrum", "Ethereum L2", "A", 142.00, 84.00, 1730, listOf("L2", "高Gas"), true, 4.2, 82, 1730, 190),
    CommunityProject("optimism", "Optimism", "Superchain", "O", 117.00, 49.00, 1390, listOf("L2", "零撸", "明牌空投"), true, 4.6, 91, 1390, 270)
)

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
    var communityStats by remember { mutableStateOf<Map<String, CommunityProjectStatsDto>>(emptyMap()) }
    var statsLoading by remember { mutableStateOf(true) }
    var ranking by remember { mutableStateOf(CommunityRanking.REWARD) }
    var useDemoData by remember { mutableStateOf(BuildConfig.DEBUG) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val allTags = listOf("L2", "零撸", "需测试网", "高Gas", "明牌空投")

    LaunchedEffect(Unit) {
        ApiClient.initialize(context)
        communityStats = coroutineScope {
            communityProjectCatalog.map { project ->
                async {
                    project.id to runCatching {
                        ApiClient.service.getCommunityProjectStats(project.id)
                    }.getOrNull()
                }
            }.awaitAll().mapNotNull { (id, stats) -> stats?.let { id to it } }.toMap()
        }
        statsLoading = false
    }

    val combinedProjects = communityProjectCatalog.map { base ->
        if (useDemoData) base.copy(recentInteractions24h = base.demoRecentInteractions) else {
            val stats = communityStats[base.id]
            base.copy(
                reward = stats?.total_actual_reward_usd ?: 0.0,
                gas = stats?.total_gas_cost_usd ?: 0.0,
                contributionCount = stats?.contribution_count ?: 0L,
                recentInteractions24h = stats?.interactions_24h ?: 0L,
                averageRating = stats?.average_rating,
                ratingCount = stats?.rating_count ?: 0L,
                isDemo = false
            )
        }
    }
    val displayedProjects = combinedProjects.filter {
        (selectedTag == null || selectedTag in it.tags) &&
            (!favoritesOnly || it.id in favoriteIds) &&
            matchesCommunityProjectQuery(it.name, it.chain, it.tags, searchQuery)
    }
        .let { filtered ->
            when (ranking) {
                CommunityRanking.REWARD -> filtered.sortedByDescending { it.reward }
                CommunityRanking.GAS -> filtered.sortedByDescending { it.gas }
                CommunityRanking.NEW -> filtered.sortedWith(compareByDescending<CommunityProject> { it.recentInteractions24h }.thenByDescending { it.contributionCount })
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
                        if (useDemoData) "测试演示数据 · 不计入全网统计" else "全网统计仅累计用户手动确认的真实贡献",
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = if (useDemoData) Color(0xFF9A5B12) else Color(0xFF19764F),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = useDemoData, onClick = { useDemoData = true }, label = { Text("测试演示") })
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
                    useDemoData -> "按独立测试样本的收益、Gas 和参与数排列"
                    statsLoading -> "正在读取全网已确认统计…"
                    ranking == CommunityRanking.REWARD -> "按全网用户确认的实际收益排列"
                    ranking == CommunityRanking.GAS -> "按全网用户确认的 Gas 消耗排列"
                    else -> "按最近24小时全网确认交互热度排列"
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
        item { Text("发现 ${displayedProjects.size} 个项目" + if (statsLoading) " · 统计加载中" else "",
            color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall) }
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
                            Text("近24h ${project.recentInteractions24h} 次交互",
                                color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                            Text(
                                "${project.contributionCount} 笔贡献",
                                color = AppColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
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
                        IconButton(onClick = {
                            scope.launch { CacheStore.toggleCommunityFavorite(project.id, project.name) }
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
                            Text("★ ${String.format(Locale.US, "%.1f", project.demoScore)} · ${project.demoPositive}% 好评",
                                color = Color(0xFFB7791F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Text("${project.demoReviews} 条模拟评价", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Text(
                            if (project.ratingCount > 0 && project.averageRating != null)
                                "★ ${String.format(Locale.US, "%.1f", project.averageRating)} · ${project.ratingCount} 个账号评分"
                            else "暂无全网评分",
                            color = if (project.ratingCount > 0) Color(0xFFB7791F) else AppColors.TextHelper,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.weight(project.rewardWeight).fillMaxHeight().background(Color(0xFF31B77D), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)))
                        Box(Modifier.weight(1f - project.rewardWeight).fillMaxHeight().background(Color(0xFFE76565), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(String.format(Locale.US, "收益 $%,.0f", project.reward), color = Color(0xFF16845B), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Text(String.format(Locale.US, "Gas $%,.0f", project.gas), color = Color(0xFFD34F4F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        project.tags.forEach { tag ->
                            Surface(color = Color(0xFFF1F4F7), shape = RoundedCornerShape(6.dp)) {
                                Text(tag, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = AppColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${project.contributionCount} 笔全网确认贡献", Modifier.weight(1f), color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        Text("查看评价  ›", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val success = CacheStore.joinCommunityProject(project.id, project.name)
                                Toast.makeText(
                                    context,
                                    if (success) "${project.name} 已加入我的项目" else "加入失败，请稍后重试",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = project.id !in joinedOfficialIds,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (project.id in joinedOfficialIds) "已加入我的项目" else "＋ 加入我的项目")
                    }
                }
            }
        }
    }
}
