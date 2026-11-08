package com.airdrophunter.app

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.officialIdentifier
import com.airdrophunter.app.data.TokenStore
import com.airdrophunter.app.ui.theme.AirdropHunterTheme
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashDiagnostics.markAction("MainActivity.onCreate")
        window.statusBarColor = android.graphics.Color.rgb(248, 249, 250)
        window.navigationBarColor = android.graphics.Color.rgb(248, 249, 250)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            AirdropHunterTheme {
                var crashReport by remember { mutableStateOf(CrashDiagnostics.readReport(applicationContext)) }
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

                crashReport?.let { report ->
                    AlertDialog(
                        onDismissRequest = { crashReport = null },
                        title = { Text("检测到上次闪退记录") },
                        text = {
                            SelectionContainer {
                                Text(
                                    report,
                                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Airdrop Hunter crash report", report))
                                CrashDiagnostics.clearReport(applicationContext)
                                crashReport = null
                                Toast.makeText(this@MainActivity, "闪退报告已复制，可以粘贴发给我", Toast.LENGTH_LONG).show()
                            }) { Text("复制报告") }
                        },
                        dismissButton = {
                            TextButton(onClick = { crashReport = null }) { Text("稍后") }
                        }
                    )
                }
            }
        }
    }
}

private data class Destination(val label: Int, val icon: ImageVector)

private enum class AppOverlay { NONE, SOURCES, AIRDROP_HUB, WALLET_MANAGER, GLOBAL_SEARCH, TRON_ENERGY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirdropHunterApp(isGuest: Boolean, onRequireAuth: () -> Unit, onLogout: () -> Unit, onOpenAuth: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var activeOverlay by rememberSaveable { mutableStateOf(AppOverlay.NONE) }
    var selectedProject by remember { mutableStateOf<ProjectDto?>(null) }
    var selectedCommunityProject by remember { mutableStateOf<ProjectDto?>(null) }

    val hasSecondaryDestination = activeOverlay != AppOverlay.NONE || selectedProject != null || selectedCommunityProject != null
    val showTopBar = activeOverlay in setOf(AppOverlay.SOURCES, AppOverlay.AIRDROP_HUB, AppOverlay.WALLET_MANAGER, AppOverlay.TRON_ENERGY) || selectedProject != null || selectedCommunityProject != null

    BackHandler(hasSecondaryDestination) {
        activeOverlay = AppOverlay.NONE
        selectedProject = null
        selectedCommunityProject = null
    }

    val destinations = listOf(
        Destination(R.string.tab_home, Icons.Default.Home),
        Destination(R.string.tab_projects, Icons.Default.GridView),
        Destination(R.string.tab_community, Icons.Default.Public),
        Destination(R.string.tab_profile, Icons.Default.Person)
    )
    val secondaryTitle = when {
        activeOverlay == AppOverlay.SOURCES -> stringResource(R.string.sources_title)
        activeOverlay == AppOverlay.AIRDROP_HUB -> "空投大厅"
        activeOverlay == AppOverlay.WALLET_MANAGER -> "钱包管理"
        activeOverlay == AppOverlay.GLOBAL_SEARCH -> "全局搜索"
        activeOverlay == AppOverlay.TRON_ENERGY -> "TRON 能量租赁助手"
        selectedCommunityProject != null -> "项目微社区"
        else -> stringResource(R.string.project_detail)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(AppColors.Background),
        containerColor = AppColors.Background,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(secondaryTitle, color = AppColors.TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = {
                            activeOverlay = AppOverlay.NONE
                            selectedProject = null
                            selectedCommunityProject = null
                        }) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AppColors.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background, titleContentColor = AppColors.TextPrimary)
                )
            }
        },
        bottomBar = {
            if (!hasSecondaryDestination) {
                Box(Modifier.fillMaxWidth().height(80.dp)) {
                    Box(Modifier.fillMaxSize().blur(12.dp).background(AppColors.Navigation))
                    NavigationBar(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent, tonalElevation = 0.dp) {
                        destinations.forEachIndexed { index, destination ->
                            val label = stringResource(destination.label)
                            NavigationBarItem(selected = tab == index, onClick = { CrashDiagnostics.markAction("Bottom navigation tab=$index"); tab = index },
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
                activeOverlay == AppOverlay.SOURCES -> SourcesScreen(Modifier.fillMaxSize())
                activeOverlay == AppOverlay.AIRDROP_HUB -> AirdropHubScreen(Modifier.fillMaxSize())
                activeOverlay == AppOverlay.TRON_ENERGY -> TronEnergyAssistantScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = { activeOverlay = AppOverlay.NONE },
                    onOpenBrowser = { address ->
                        CrashDiagnostics.markAction("Launch browser from TRON energy assistant")
                        activeOverlay = AppOverlay.NONE
                        context.startActivity(Intent(context, BrowserActivity::class.java)
                            .putExtra(BrowserActivity.EXTRA_URL, "https://feee.io/")
                            .putExtra(BrowserActivity.EXTRA_TITLE, "TRON 能量租赁")
                            .putExtra(BrowserActivity.EXTRA_QUICK_COPY_ADDRESS, address))
                    }
                )
                activeOverlay == AppOverlay.GLOBAL_SEARCH -> GlobalSearchScreen(
                    modifier = Modifier.fillMaxSize(),
                    onCancel = { activeOverlay = AppOverlay.NONE },
                    onSearch = { url ->
                        CrashDiagnostics.markAction("Launch browser from global search")
                        activeOverlay = AppOverlay.NONE
                        context.startActivity(
                            Intent(context, BrowserActivity::class.java)
                                .putExtra(BrowserActivity.EXTRA_URL, url)
                                .putExtra(BrowserActivity.EXTRA_TITLE, "OKLink 全链搜索")
                        )
                    }
                )
                activeOverlay == AppOverlay.WALLET_MANAGER -> WalletManagerScreen(
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
                selectedCommunityProject != null -> MicroCommunityScreen(
                    projectOfficialId = selectedCommunityProject!!.officialIdentifier(),
                    projectName = selectedCommunityProject!!.name,
                    modifier = Modifier.fillMaxSize()
                )
                selectedProject != null -> ProjectDetailScreen(selectedProject!!, Modifier.fillMaxSize(), isGuest)
                tab == 0 -> HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenSources = { CrashDiagnostics.markAction("Open sources overlay"); activeOverlay = AppOverlay.SOURCES },
                    onOpenWallet = { CrashDiagnostics.markAction("Open wallet manager overlay"); activeOverlay = AppOverlay.WALLET_MANAGER },
                    onOpenGlobalSearch = { CrashDiagnostics.markAction("Open global search overlay"); activeOverlay = AppOverlay.GLOBAL_SEARCH },
                    onOpenProjects = { CrashDiagnostics.markAction("Open projects tab"); tab = 1 },
                    onOpenAirdropHub = { CrashDiagnostics.markAction("Open airdrop hub overlay"); activeOverlay = AppOverlay.AIRDROP_HUB },
                    onOpenTronEnergy = { CrashDiagnostics.markAction("Open TRON energy overlay"); activeOverlay = AppOverlay.TRON_ENERGY },
                    isGuest = isGuest, onRequireAuth = onRequireAuth
                )
                tab == 2 -> CommunitySquareScreen(
                    modifier = Modifier.fillMaxSize(),
                    onProjectSelected = { id, name, description ->
                        CrashDiagnostics.markAction("Open micro-community project")
                        selectedCommunityProject = ProjectDto(id, name, description, "tracking", communityOfficialId = id)
                    }
                )
                tab == 1 -> ProjectsScreen(Modifier.fillMaxSize(), { CrashDiagnostics.markAction("Open project detail"); selectedProject = it }, isGuest, onRequireAuth,
                    onOpenCommunity = { selectedCommunityProject = it })
                else -> ProfileScreen(Modifier.fillMaxSize(), onLogout, isGuest, onRequireAuth,
                    onManageWallets = { CrashDiagnostics.markAction("Open wallet manager from profile"); activeOverlay = AppOverlay.WALLET_MANAGER })
            }
        }
    }
}
