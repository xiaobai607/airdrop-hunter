package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    val reward: Double, val gas: Double, val score: Double, val positive: Int, val reviews: Int
) {
    val label: String get() = when {
        positive >= 95 -> "好评如潮"
        positive >= 85 -> "特别好评"
        positive >= 75 -> "多半好评"
        else -> "褒贬不一"
    }
    val rewardWeight: Float get() = (reward / (reward + gas).coerceAtLeast(1.0)).toFloat().coerceIn(0.02f, 0.98f)
}

private val communityMockProjects = listOf(
    CommunityProject("linea", "Linea", "Consensys zkEVM", "L", 18420.0, 4280.0, 4.9, 97, 1280),
    CommunityProject("scroll", "Scroll", "zkEVM", "S", 12860.0, 3860.0, 4.8, 95, 964),
    CommunityProject("zksync", "zkSync", "ZK Rollup", "Z", 15600.0, 6200.0, 4.7, 93, 1820),
    CommunityProject("starknet", "Starknet", "Validity Rollup", "✦", 9800.0, 5100.0, 4.4, 88, 1450),
    CommunityProject("base", "Base", "Ethereum L2", "B", 22600.0, 7300.0, 4.9, 96, 2200),
    CommunityProject("arbitrum", "Arbitrum", "Ethereum L2", "A", 14200.0, 8400.0, 4.2, 82, 1730),
    CommunityProject("optimism", "Optimism", "Superchain", "O", 11700.0, 4900.0, 4.6, 91, 1390)
)

@Composable
fun CommunitySquareScreen(
    modifier: Modifier = Modifier,
    onProjectSelected: (String, String, String) -> Unit
) {
    LazyColumn(
        modifier.fillMaxSize().background(AppColors.Background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("微社区广场", color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("发现全网空投项目 · 查看参与者口碑与盈亏样本",
                    color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Surface(color = Color(0xFFFFF4E5), shape = RoundedCornerShape(999.dp)) {
                    Text("MOCK 社区数据", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Color(0xFF9A5B12), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        items(communityMockProjects, key = { it.id }) { project ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    onProjectSelected(project.id, project.name, project.chain)
                },
                shape = RoundedCornerShape(19.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                elevation = CardDefaults.cardElevation(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFFEAF2FF),
                            modifier = Modifier.size(45.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(project.logo, color = Color(0xFF315A9D), fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(project.name, color = AppColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(project.chain, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFB12B), modifier = Modifier.size(15.dp))
                                Text(String.format(Locale.US, "%.1f", project.score), color = AppColors.TextPrimary,
                                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            }
                            Surface(color = Color(0xFFEAF7EF), shape = RoundedCornerShape(999.dp)) {
                                Text(project.label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = Color(0xFF19764F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.weight(project.rewardWeight).fillMaxHeight().background(Color(0xFF31B77D),
                            RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)))
                        Box(Modifier.weight(1f - project.rewardWeight).fillMaxHeight().background(Color(0xFFE76565),
                            RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(String.format(Locale.US, "收益 $%,.0f", project.reward), color = Color(0xFF16845B),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Text(String.format(Locale.US, "Gas $%,.0f", project.gas), color = Color(0xFFD34F4F),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${project.reviews} 位猎人评价", color = AppColors.TextHelper,
                            style = MaterialTheme.typography.labelSmall)
                        Text("查看评价  ›", color = AppColors.Primary, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
