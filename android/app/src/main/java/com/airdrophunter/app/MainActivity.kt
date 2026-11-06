package com.airdrophunter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.TokenStore
import com.airdrophunter.app.ui.theme.AirdropHunterTheme
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.initialize(applicationContext)
        setContent {
            AirdropHunterTheme {
                val token by TokenStore.observeToken(applicationContext).collectAsStateWithLifecycle(initialValue = null)
                when {
                    token == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
                    token.isNullOrBlank() -> AuthScreen()
                    else -> AirdropHunterApp(onLogout = { lifecycleScope.launch { TokenStore.clearToken(applicationContext) } })
                }
            }
        }
    }
}

private data class Destination(val label: Int, val icon: ImageVector)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirdropHunterApp(onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var showSources by remember { mutableStateOf(false) }
    var showWallet by remember { mutableStateOf(false) }
    var selectedProject by remember { mutableStateOf<ProjectDto?>(null) }
    BackHandler(showSources || selectedProject != null) { showSources = false; selectedProject = null }
    val destinations = listOf(Destination(R.string.tab_home, Icons.Default.Home), Destination(R.string.tab_profile, Icons.Default.Person), Destination(R.string.tab_projects, Icons.Default.FolderOpen))
    val pageTitle = when {
        showSources -> stringResource(R.string.sources_title)
        selectedProject != null -> stringResource(R.string.project_detail)
        tab == 0 -> stringResource(R.string.tab_home)
        tab == 1 -> stringResource(R.string.tab_profile)
        else -> stringResource(R.string.tab_projects)
    }
    Scaffold(containerColor = AppColors.Background, topBar = {
        TopAppBar(
            title = { Text(pageTitle, color = AppColors.TextPrimary) },
            navigationIcon = { if (showSources || selectedProject != null) IconButton(onClick = { showSources = false; selectedProject = null }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AppColors.TextPrimary) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background, titleContentColor = AppColors.TextPrimary)
        )
    }, bottomBar = {
        if (!showSources && selectedProject == null) NavigationBar(containerColor = AppColors.Navigation, tonalElevation = 0.dp) {
            destinations.forEachIndexed { index, d ->
                val label = stringResource(d.label)
                NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(d.icon, label) }, label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = AppColors.Primary, selectedTextColor = AppColors.Primary, unselectedIconColor = AppColors.TextSecondary, unselectedTextColor = AppColors.TextSecondary, indicatorColor = AppColors.NavIndicator))
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                showSources -> SourcesScreen(Modifier.fillMaxSize())
                selectedProject != null -> ProjectDetailScreen(selectedProject!!, Modifier.fillMaxSize())
                tab == 0 -> HomeScreen(Modifier.fillMaxSize(), { showSources = true }, { showWallet = true }, { tab = 2 })
                tab == 1 -> ProfileScreen(Modifier.fillMaxSize(), onLogout)
                else -> ProjectsScreen(Modifier.fillMaxSize(), onProjectSelected = { selectedProject = it })
            }
        }
    }
    if (showWallet) WalletCheckDialog(onDismiss = { showWallet = false })
}
