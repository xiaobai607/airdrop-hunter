package com.airdrophunter.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TokenStore.initialize(applicationContext)
        ApiClient.initialize(applicationContext)
        window.statusBarColor = android.graphics.Color.rgb(248, 249, 250)
        window.navigationBarColor = android.graphics.Color.rgb(248, 249, 250)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            AirdropHunterTheme {
                val token by TokenStore.observeToken(applicationContext).collectAsStateWithLifecycle(initialValue = null)
                var showAuth by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(token) { if (!token.isNullOrBlank()) showAuth = false }
                LaunchedEffect(token) {
                    if (!token.isNullOrBlank()) {
                        com.airdrophunter.app.data.LocalDataSyncManager.prepareMockSync(applicationContext)
                    }
                }
                if (showAuth && token.isNullOrBlank()) {
                    AuthScreen(onGuestMode = { showAuth = false })
                } else {
                    AirdropHunterApp(
                        isGuest = token.isNullOrBlank(),
                        onRequireAuth = { showAuth = true },
                        onLogout = { lifecycleScope.launch { TokenStore.clearToken(applicationContext) } },
                        onOpenAuth = { showAuth = true }
                    )
                }
            }
        }
    }
}

private data class Destination(val label: Int, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirdropHunterApp(isGuest: Boolean, onRequireAuth: () -> Unit, onLogout: () -> Unit, onOpenAuth: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showAirdropHub by rememberSaveable { mutableStateOf(false) }
    var showWalletManager by rememberSaveable { mutableStateOf(false) }
    var selectedProject by remember { mutableStateOf<ProjectDto?>(null) }

    BackHandler(showSources || showAirdropHub || showWalletManager || selectedProject != null) {
        showSources = false; showAirdropHub = false; showWalletManager = false; selectedProject = null
    }

    val destinations = listOf(
        Destination(R.string.tab_home, Icons.Default.Home),
        Destination(R.string.tab_projects, Icons.Default.GridView),
        Destination(R.string.tab_profile, Icons.Default.Person)
    )
    val secondaryTitle = when {
        showSources -> stringResource(R.string.sources_title)
        showAirdropHub -> "空投大厅"
        showWalletManager -> "钱包管理"
        else -> stringResource(R.string.project_detail)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(AppColors.Background),
        containerColor = AppColors.Background,
        topBar = {
            if (showSources || showAirdropHub || showWalletManager || selectedProject != null) {
                TopAppBar(
                    title = { Text(secondaryTitle, color = AppColors.TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { showSources = false; showAirdropHub = false; showWalletManager = false; selectedProject = null }) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AppColors.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background, titleContentColor = AppColors.TextPrimary)
                )
            }
        },
        bottomBar = {
            if (!showSources && !showAirdropHub && !showWalletManager && selectedProject == null) {
                Box(Modifier.fillMaxWidth().height(80.dp)) {
                    Box(Modifier.fillMaxSize().blur(12.dp).background(AppColors.Navigation))
                    NavigationBar(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent, tonalElevation = 0.dp) {
                        destinations.forEachIndexed { index, destination ->
                            val label = stringResource(destination.label)
                            NavigationBarItem(selected = tab == index, onClick = { tab = index },
                                icon = { Icon(destination.icon, label) }, label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AppColors.Primary, selectedTextColor = AppColors.Primary,
                                    unselectedIconColor = AppColors.TextHelper, unselectedTextColor = AppColors.TextHelper,
                                    indicatorColor = AppColors.NavIndicator
                                ))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(AppColors.Background).padding(padding)) {
            when {
                showSources -> SourcesScreen(Modifier.fillMaxSize())
                showAirdropHub -> AirdropHubScreen(Modifier.fillMaxSize())
                showWalletManager -> WalletManagerScreen(
                    modifier = Modifier.fillMaxSize(),
                    isGuest = isGuest,
                    onOpenBrowser = { url, selectedWallet, wallets, title ->
                        val walletJson = JSONArray().apply {
                            wallets.forEach { wallet ->
                                put(JSONObject()
                                    .put("id", wallet.id)
                                    .put("chain", wallet.chain)
                                    .put("network_icon", wallet.network_icon)
                                    .put("wallet_name", wallet.wallet_name)
                                    .put("address", wallet.address))
                            }
                        }.toString()
                        context.startActivity(Intent(context, BrowserActivity::class.java)
                            .putExtra(BrowserActivity.EXTRA_URL, url)
                            .putExtra(BrowserActivity.EXTRA_TITLE, title)
                            .putExtra(BrowserActivity.EXTRA_WALLETS_JSON, walletJson)
                            .putExtra(BrowserActivity.EXTRA_SELECTED_WALLET_ID, selectedWallet.id))
                    }
                )
                selectedProject != null -> ProjectDetailScreen(selectedProject!!, Modifier.fillMaxSize(), isGuest)
                tab == 0 -> HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenSources = { showSources = true },
                    onOpenWallet = { showWalletManager = true },
                    onOpenProjects = { tab = 1 },
                    onOpenAirdropHub = { showAirdropHub = true },
                    isGuest = isGuest, onRequireAuth = onRequireAuth
                )
                tab == 1 -> ProjectsScreen(Modifier.fillMaxSize(), { selectedProject = it }, isGuest, onRequireAuth)
                else -> ProfileScreen(Modifier.fillMaxSize(), onLogout, isGuest, onRequireAuth,
                    onManageWallets = { showWalletManager = true })
            }
        }
    }
}
