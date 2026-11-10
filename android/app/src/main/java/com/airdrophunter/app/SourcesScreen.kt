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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import coil.compose.SubcomposeAsyncImage
import com.airdrophunter.app.data.AirdropSiteStore
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun SourcesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sources = AirdropSiteStore.defaults
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(sources, key = { it.id }) { source ->
            Card(
                onClick = {
                    context.startActivity(Intent(context, BrowserActivity::class.java)
                        .putExtra(BrowserActivity.EXTRA_URL, source.url)
                        .putExtra(BrowserActivity.EXTRA_TITLE, source.name))
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
                    Surface(Modifier.size(42.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFFF3F5F7)) {
                        SubcomposeAsyncImage(
                            model = "https://www.google.com/s2/favicons?domain=${source.domain}&sz=128",
                            contentDescription = source.name,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            loading = { SourceLetter(source.name) },
                            error = { SourceLetter(source.name) }
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(source.name, color = AppColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(source.url, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", color = AppColors.TextHelper, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun SourceLetter(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = AppColors.Primary, fontWeight = FontWeight.Bold)
    }
}
