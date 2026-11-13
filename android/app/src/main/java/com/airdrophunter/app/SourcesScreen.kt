package com.airdrophunter.app

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import com.airdrophunter.app.data.AirdropSiteStore
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.appPressFeedback

@Composable
fun SourcesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sources = AirdropSiteStore.defaults
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "查看已收录的官方活动与公告网站",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        items(sources, key = { it.id }) { source ->
            Card(
                modifier = Modifier.fillMaxWidth().appPressFeedback(
                    onClick = {
                        context.startActivity(Intent(context, BrowserActivity::class.java)
                            .putExtra(BrowserActivity.EXTRA_URL, source.url)
                            .putExtra(BrowserActivity.EXTRA_TITLE, source.name))
                    },
                    scale = 0.96f
                ),
                shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(Modifier.size(42.dp), shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        SubcomposeAsyncImage(
                            model = "https://www.google.com/s2/favicons?domain=${source.domain}&sz=128",
                            contentDescription = source.name,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            loading = { SourceLetter(source.name) },
                            error = { SourceLetter(source.name) }
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            source.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            source.domain,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun SourceLetter(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}
