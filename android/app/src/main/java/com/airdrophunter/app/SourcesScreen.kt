package com.airdrophunter.app

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.airdrophunter.app.ui.theme.AppColors

@Composable
fun SourcesScreen(modifier: Modifier = Modifier, viewModel: SourcesViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadSources() }
    when {
        state.isLoading && state.sources.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
        state.error != null && state.sources.isEmpty() -> Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.network_error), color = AppColors.Danger)
            Button(onClick = viewModel::loadSources) { Text(stringResource(R.string.retry)) }
        }
        state.sources.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.sources_empty), color = AppColors.TextSecondary) }
        else -> LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.sources, key = { it.id }) { source ->
                Card(modifier = Modifier.fillMaxWidth().clickable {
                    context.startActivity(Intent(context, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, source.website_url).putExtra(BrowserActivity.EXTRA_TITLE, source.name))
                }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), border = BorderStroke(1.dp, AppColors.Border)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(model = source.icon_url, contentDescription = source.name, modifier = Modifier.size(42.dp))
                        Text(source.name, color = AppColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
