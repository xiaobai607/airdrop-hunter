package com.airdrophunter.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
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
        window.statusBarColor = android.graphics.Color.rgb(15, 23, 42)
        window.navigationBarColor = android.graphics.Color.rgb(2, 6, 23)
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
        Destination(R.string.tab_projects, Icons.Default.FolderOpen),
        Destination(R.string.tab_profile, Icons.Default.Person)
    )
    val pageTitle = when {
        showSources -> stringResource(R.string.sources_title)
        selectedProject != null -> stringResource(R.string.project_detail)
        tab == 0 -> stringResource(R.string.tab_home)
        tab == 1 -> stringResource(R.string.tab_projects)
        else -> stringResource(R.string.tab_profile)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppColors.Background, AppColors.BackgroundDeep))),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(pageTitle, color = AppColors.TextPrimary) },
                navigationIcon = {
                    if (showSources || selectedProject != null) {
                        IconButton(onClick = { showSources = false; selectedProject = null }) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AppColors.TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = AppColors.TextPrimary)
            )
        },
        bottomBar = {
            if (!showSources && selectedProject == null) NavigationBar(containerColor = AppColors.Navigation, tonalElevation = 0.dp) {
                destinations.forEachIndexed { index, destination ->
                    val label = stringResource(destination.label)
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = {
                            tab = index
                        },
                        icon = { Icon(destination.icon, label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Primary, selectedTextColor = AppColors.Primary,
                            unselectedIconColor = AppColors.TextSecondary, unselectedTextColor = AppColors.TextSecondary,
                            indicatorColor = AppColors.NavIndicator
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppColors.Background, AppColors.BackgroundDeep))).padding(padding)) {
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
                    Modifier.fillMaxSize(),
                    onProjectSelected = { selectedProject = it },
                    isGuest = isGuest,
                    onRequireAuth = onRequireAuth
                )
                else -> if (isGuest) GuestSettingsScreen(onRequireAuth) else ProfileScreen(Modifier.fillMaxSize(), onLogout)
                
            }
        }
    }
    if (showWallet) WalletCheckDialog(onDismiss = { showWallet = false })
}

@Composable
private fun GuestSettingsScreen(onRequireAuth: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("游客模式", color = AppColors.TextPrimary, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("你可以浏览公开行情和空投导航。登录后即可同步个人项目与云端数据。", color = AppColors.TextSecondary)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRequireAuth) { Text("登录 / 注册") }
    }
}
