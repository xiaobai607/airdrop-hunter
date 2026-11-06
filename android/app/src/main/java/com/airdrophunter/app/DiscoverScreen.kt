package com.airdrophunter.app

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadSources()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "发现优质空投平台",
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.TextPrimary
            )
            Text(
                "点击平台卡片，在应用内浏览官方页面。请留意网页安全提示。",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )
        }

        if (state.error != null) {
            Text("加载失败：${state.error}", color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::loadSources, enabled = !state.isLoading) {
                Text("重新加载")
            }
        }

        if (state.isLoading && state.sources.isEmpty()) {
            CircularProgressIndicator(color = AppColors.Primary)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.sources, key = { it.id }) { source ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(context, BrowserActivity::class.java)
                                    .putExtra(BrowserActivity.EXTRA_URL, source.website_url)
                                    .putExtra(BrowserActivity.EXTRA_TITLE, source.name)
                                context.startActivity(intent)
                            },
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        border = BorderStroke(1.dp, AppColors.Border)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(46.dp),
                                color = AppColors.SurfaceElevated,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                AsyncImage(
                                    model = source.icon_url,
                                    contentDescription = "${source.name} Logo",
                                    modifier = Modifier.padding(5.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    source.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AppColors.TextPrimary
                                )
                                Text(
                                    source.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.Primary
                                )
                                Text(
                                    source.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary
                                )
                                Text(
                                    source.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "在应用内打开",
                                tint = AppColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
