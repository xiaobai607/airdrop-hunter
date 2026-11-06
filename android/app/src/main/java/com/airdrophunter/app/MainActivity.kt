package com.airdrophunter.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
        TokenStore.initialize(applicationContext)
        ApiClient.initialize(applicationContext)
        val startupToken = TokenStore.readTokenBlocking()
        window.statusBarColor = android.graphics.Color.rgb(248, 249, 250)
        window.navigationBarColor = android.graphics.Color.rgb(248, 249, 250)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            AirdropHunterTheme {
                val token by TokenStore.observeToken(applicationContext).collectAsStateWithLifecycle(initialValue = startupToken)
                var guestMode by rememberSaveable { mutableStateOf(false) }
                when {
                    !token.isNullOrBlank() -> AirdropHunterApp(
                        isGuest = false,
                        onRequireAuth = { },
                        onLogout = { lifecycleScope.launch { TokenStore.clearToken(applicationContext) } }
                    )
                    guestMode -> AirdropHunterApp(
                        isGuest = true,
                        onRequireAuth = {
                            Toast.makeText(applicationContext, "请先登录以同步您的云端数据", Toast.LENGTH_LONG).show()
                            guestMode = false
                        },
                        onLogout = { guestMode = false }
                    )
                    else -> AuthScreen(onGuestMode = { guestMode = true })
                }
            }
        }
    }
}

private data class Destination(val label: Int, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirdropHunterApp(isGuest: Boolean, onRequireAuth: () -> Unit, onLogout: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showWallet by remember { mutableStateOf(false) }
    var selectedProject by remember { mutableStateOf<ProjectDto?>(null) }

    BackHandler(showSources || selectedProject != null) {
        showSources = false
        selectedProject = null
    }

    val destinations = listOf(
        Destination(R.string.tab_home, Icons.Default.Home),
        Destination(R.string.tab_projects, Icons.Default.GridView),
        Destination(R.string.tab_profile, Icons.Default.Person)
    )
    val secondaryTitle = when {
        showSources -> stringResource(R.string.sources_title)
        else -> stringResource(R.string.project_detail)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(AppColors.Background),
        containerColor = AppColors.Background,
        topBar = {
            if (showSources || selectedProject != null) {
                TopAppBar(
                    title = { Text(secondaryTitle, color = AppColors.TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { showSources = false; selectedProject = null }) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AppColors.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.Background,
                        titleContentColor = AppColors.TextPrimary
                    )
                )
            }
        },
        bottomBar = {
            if (!showSources && selectedProject == null) {
                Box(Modifier.fillMaxWidth().height(80.dp)) {
                    Box(Modifier.fillMaxSize().blur(12.dp).background(AppColors.Navigation))
                    NavigationBar(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
                        destinations.forEachIndexed { index, destination ->
                            val label = stringResource(destination.label)
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(destination.icon, label) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AppColors.Primary,
                                    selectedTextColor = AppColors.Primary,
                                    unselectedIconColor = AppColors.TextHelper,
                                    unselectedTextColor = AppColors.TextHelper,
                                    indicatorColor = AppColors.NavIndicator
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(AppColors.Background).padding(padding)) {
            when {
                showSources -> SourcesScreen(Modifier.fillMaxSize())
                selectedProject != null -> ProjectDetailScreen(selectedProject!!, Modifier.fillMaxSize())
                tab == 0 -> HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenSources = { showSources = true },
                    onOpenWallet = { showWallet = true },
                    onOpenProjects = { tab = 1 },
                    isGuest = isGuest,
                    onRequireAuth = onRequireAuth
                )
                tab == 1 -> ProjectsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onProjectSelected = { selectedProject = it },
                    isGuest = isGuest,
                    onRequireAuth = onRequireAuth
                )
                else -> ProfileScreen(
                    modifier = Modifier.fillMaxSize(),
                    onLogout = onLogout,
                    isGuest = isGuest,
                    onRequireAuth = onRequireAuth
                )
            }
        }
    }
    if (showWallet) WalletCheckDialog(onDismiss = { showWallet = false })
}