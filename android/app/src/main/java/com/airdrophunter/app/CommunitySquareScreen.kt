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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airdrophunter.app.ui.theme.AppColors
import java.util.Locale

private data class CommunityProject(
    val id: String, val name: String, val chain: String, val logo: String,
    val reward: Double, val gas: Double, val score: Double, val positive: Int, val reviews: Int,
    val tags: List<String>, val freshness: Int
) {
    val label: String get() = when {
        positive >= 95 -> "好评如潮"
        positive >= 85 -> "特别好评"
        positive >= 75 -> "多半好评"
        else -> "褒贬不一"
    }
    val rewardWeight: Float get() = (reward / (reward + gas).coerceAtLeast(1.0)).toFloat().coerceIn(0.02f, 0.98f)
}

private enum class CommunityRanking(val title: String) {
    REWARD("🔥 吸金榜"), GAS("💀 避坑榜"), NEW("🚀 新星榜")
}

private val communityMockProjects = listOf(
    CommunityProject("linea", "Linea", "Consensys zkEVM", "L", 18420.0, 4280.0, 4.9, 97, 1280, listOf("L2", "零撸", "明牌空投"), 5),
    CommunityProject("scroll", "Scroll", "zkEVM", "S", 12860.0, 3860.0, 4.8, 95, 964, listOf("L2", "需测试网", "明牌空投"), 7),
    CommunityProject("zksync", "zkSync", "ZK Rollup", "Z", 15600.0, 6200.0, 4.7, 93, 1820, listOf("L2", "高Gas", "明牌空投"), 4),
    CommunityProject("starknet", "Starknet", "Validity Rollup", "✦", 9800.0, 5100.0, 4.4, 88, 1450, listOf("L2", "需测试网", "高Gas"), 3),
    CommunityProject("base", "Base", "Ethereum L2", "B", 22600.0, 7300.0, 4.9, 96, 2200, listOf("L2", "零撸", "高Gas"), 2),
    CommunityProject("arbitrum", "Arbitrum", "Ethereum L2", "A", 14200.0, 8400.0, 4.2, 82, 1730, listOf("L2", "高Gas"), 1),
    CommunityProject("optimism", "Optimism", "Superchain", "O", 11700.0, 4900.0, 4.6, 91, 1390, listOf("L2", "零撸", "明牌空投"), 6)
)

@Composable
fun CommunitySquareScreen(
    modifier: Modifier = Modifier,
    onProjectSelected: (String, String, String) -> Unit
) {
    var ranking by remember { mutableStateOf(CommunityRanking.REWARD) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val allTags = listOf("L2", "零撸", "需测试网", "高Gas", "明牌空投")
    val displayedProjects = remember(ranking, selectedTag) {
        val filtered = communityMockProjects.filter { selectedTag == null || selectedTag in it.tags }
        when (ranking) {
            CommunityRanking.REWARD -> filtered.sortedByDescending { it.reward }
            CommunityRanking.GAS -> filtered.sortedByDescending { it.gas }
            CommunityRanking.NEW -> filtered.sortedByDescending { it.freshness }
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
                Surface(color = Color(0xFFFFF4E5), shape = RoundedCornerShape(999.dp)) {
                    Text("MOCK 社区数据 · 榜单仅供界面预览", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Color(0xFF9A5B12), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
                Text(when (ranking) {
                    CommunityRanking.REWARD -> "按模拟全网总收益从高到低排列"
                    CommunityRanking.GAS -> "按模拟 Gas 消耗从高到低排列，优先查看成本风险"
                    CommunityRanking.NEW -> "按模拟加入时间从近到远排列"
                }, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("项目标签", color = AppColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
        item { Text("发现 ${displayedProjects.size} 个项目", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall) }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFB12B), modifier = Modifier.size(15.dp))
                                Text(String.format(Locale.US, "%.1f", project.score), color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            }
                            Surface(color = Color(0xFFEAF7EF), shape = RoundedCornerShape(999.dp)) {
                                Text(project.label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF19764F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${project.reviews} 位猎人评价", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        Text("查看评价  ›", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (displayedProjects.isEmpty()) {
            item { Text("当前标签下暂无项目", color = AppColors.TextHelper, modifier = Modifier.padding(vertical = 24.dp)) }
        }
    }
}
