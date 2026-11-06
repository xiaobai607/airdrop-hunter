package com.airdrophunter.app

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airdrophunter.app.ui.theme.AppColors

private data class LocalSource(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val category: String
)

private val localSources = listOf(
    LocalSource("airdrops-io", "Airdrops.io", "空投活动目录与参与指南", "https://airdrops.io/", "空投目录"),
    LocalSource("defillama", "DeFiLlama", "DeFi 数据与空投日历入口", "https://defillama.com/airdrops", "数据平台"),
    LocalSource("dropstab", "DropsTab", "代币解锁、空投与项目追踪", "https://dropstab.com/", "项目追踪"),
    LocalSource("coinmarketcap", "CoinMarketCap", "加密资产数据与空投活动", "https://coinmarketcap.com/airdrop/", "行情与空投"),
    LocalSource("rootdata", "RootData", "Web3 项目资料与融资信息", "https://www.rootdata.com/", "项目数据库")
)

@Composable
fun SourcesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(localSources, key = { it.id }) { source ->
            Card(
                onClick = {
                    context.startActivity(
                        Intent(context, BrowserActivity::class.java)
                            .putExtra(BrowserActivity.EXTRA_URL, source.url)
                            .putExtra(BrowserActivity.EXTRA_TITLE, source.name)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.Border),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.TealTint
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(source.name.take(1), color = AppColors.Primary,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(source.name, color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(source.description, color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall)
                        Text(source.category, color = AppColors.Primary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text("›", color = AppColors.TextHelper, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}
