package com.airdrophunter.app

import android.content.Intent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.officialIdentifier
import com.airdrophunter.app.data.TokenStore
import com.airdrophunter.app.data.GlobalSyncScheduler
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.ui.components.AppBottomBar
import com.airdrophunter.app.ui.components.AppNavigationItem
import com.airdrophunter.app.ui.theme.AirdropHunterTheme
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.theme.AppDesignTokens
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    @Volatile private var startupReady = false
    private val startupTokenState = mutableStateOf<String?>(null)

    override fun onStart() {
        super.onStart()
        val shouldSchedule = synchronized(startupSyncLock) {
            if (startupSyncScheduled) false else {
                startupSyncScheduled = true
                true
            }
        }
        if (!shouldSchedule) return
        // Schedule background work once per process, off the UI thread; Home renders Room cache first.
        lifecycleScope.launch(Dispatchers.IO) {
            GlobalSyncScheduler.ensurePeriodic(applicationContext)
            GlobalSyncScheduler.enqueueNow(applicationContext)
            GlobalSyncScheduler.enqueueDynamicCatalogWarmup(applicationContext)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWeb3TransactionCallback(intent)
    }

    private fun handleWeb3TransactionCallback(source: Intent?) {
        val uri = source?.data ?: return
        if (uri.scheme != "airdrophunter" || uri.host != "tx_callback") return

        val draftId = uri.getQueryParameter("draftId") ?: return
        val state = uri.getQueryParameter("state") ?: return
        val txHash = uri.getQueryParameter("txHash") ?: return
        val chainId = uri.getQueryParameter("chainId")?.toLongOrNull() ?: return
        if (!com.airdrophunter.app.data.Web3TransactionRepository.isValidTxHash(txHash)) return

        lifecycleScope.launch(Dispatchers.IO) {
            val app = application as? AirdropHunterApplication ?: return@launch
            val accepted = runCatching {
                app.appContainer.web3TransactionRepository.attachCallbackHash(
                    draftId = draftId,
                    callbackState = state,
                    chainId = chainId,
                    hash = txHash
                )
            }.getOrDefault(false)
            if (accepted) {
                com.airdrophunter.app.data.Web3ReceiptScheduler.enqueue(
                    applicationContext, draftId
                )
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "交易回调未通过校验，请使用链上同步补单",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !startupReady }
        super.onCreate(savedInstanceState)
        handleWeb3TransactionCallback(intent)
        lifecycleScope.launch {
            val token = try {
                withContext(Dispatchers.IO) { TokenStore.readTokenBlocking() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            startupTokenState.value = token
            startupReady = true
        }
        setContent {
            AirdropHunterTheme {
                val darkSystem = isSystemInDarkTheme()
                SideEffect {
                    val barColor = if (darkSystem) android.graphics.Color.rgb(18, 18, 20)
                        else android.graphics.Color.rgb(248, 248, 250)
                    window.statusBarColor = barColor
                    window.navigationBarColor = barColor
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = if (darkSystem) 0 else
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
                val initialToken by remember { startupTokenState }
                val token by TokenStore.observeToken(applicationContext).collectAsStateWithLifecycle(initialValue = initialToken)
                // Keep a legacy local namespace stable without treating it as a login session.
                val localScopeKey = remember(token) { CacheStore.accountScopeKey(token) }
                AirdropHunterApp(
                    isGuest = true,
                    sessionScopeKey = localScopeKey,
                    onRequireAuth = {
                        Toast.makeText(
                            applicationContext,
                            "当前版本暂不开放注册/登录，个人数据仅保存在本机",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onLogout = {},
                    onOpenAuth = {
                        Toast.makeText(
                            applicationContext,
                            "当前版本暂不开放注册/登录，个人数据仅保存在本机",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }
    }
}
private val startupSyncLock = Any()
@Volatile private var startupSyncScheduled = false

private data class Destination(val label: Int, val icon: ImageVector)

private enum class AppOverlay { NONE, SOURCES, AIRDROP_HUB, AIRDROP_CAMPAIGNS, WALLET_MANAGER, GLOBAL_SEARCH, MARKET_DETAIL, TRON_ENERGY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirdropHunterApp(
    isGuest: Boolean,
    sessionScopeKey: String,
    onRequireAuth: () -> Unit,
    onLogout: () -> Unit,
    onOpenAuth: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabStateHolder = rememberSaveableStateHolder()
    var activeOverlay by rememberSaveable { mutableStateOf(AppOverlay.NONE) }
    var selectedMarketSymbol by rememberSaveable { mutableStateOf("BTC") }
    var selectedProject by remember { mutableStateOf<ProjectDto?>(null) }
    var selectedCommunityProject by remember { mutableStateOf<ProjectDto?>(null) }
    var showAccountingSheet by rememberSaveable { mutableStateOf(false) }
    val accountingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Never leave a previous account's secondary screen or editor alive after a session switch.
    LaunchedEffect(sessionScopeKey) {
        activeOverlay = AppOverlay.NONE
        selectedProject = null
        selectedCommunityProject = null
    }

    val hasSecondaryDestination = activeOverlay != AppOverlay.NONE || selectedProject != null || selectedCommunityProject != null
    val showTopBar = activeOverlay in setOf(AppOverlay.SOURCES, AppOverlay.AIRDROP_HUB, AppOverlay.AIRDROP_CAMPAIGNS, AppOverlay.WALLET_MANAGER, AppOverlay.MARKET_DETAIL, AppOverlay.TRON_ENERGY) || selectedProject != null || selectedCommunityProject != null

    BackHandler(hasSecondaryDestination) {
        activeOverlay = AppOverlay.NONE
        selectedProject = null
        selectedCommunityProject = null
    }

    val destinations = listOf(
        Destination(R.string.tab_home, Icons.Default.Home),
        Destination(R.string.tab_community, Icons.Default.Public),
        Destination(R.string.tab_projects, Icons.Default.GridView),
        Destination(R.string.tab_profile, Icons.Default.Person)
    )
    val projectTransitionSnapshots = remember { mutableStateMapOf<String, ProjectDto>() }
    val communityTransitionSnapshots = remember { mutableStateMapOf<String, ProjectDto>() }
    LaunchedEffect(selectedProject) {
        selectedProject?.let { projectTransitionSnapshots[it.id] = it }
    }
    LaunchedEffect(selectedCommunityProject) {
        selectedCommunityProject?.let { communityTransitionSnapshots[it.id] = it }
    }
    val currentRouteKey = when {
        activeOverlay == AppOverlay.SOURCES -> "sources"
        activeOverlay == AppOverlay.AIRDROP_HUB -> "airdrop-hub"
        activeOverlay == AppOverlay.AIRDROP_CAMPAIGNS -> "airdrop-campaigns"
        activeOverlay == AppOverlay.TRON_ENERGY -> "tron-energy"
        activeOverlay == AppOverlay.GLOBAL_SEARCH -> "global-search"
        activeOverlay == AppOverlay.MARKET_DETAIL -> "market-detail"
        activeOverlay == AppOverlay.WALLET_MANAGER -> "wallet-manager"
        selectedCommunityProject != null -> "community:${selectedCommunityProject!!.id}"
        selectedProject != null -> "project:${selectedProject!!.id}"
        else -> "tab:$tab"
    }
    val secondaryTitle = when {
        activeOverlay == AppOverlay.SOURCES -> stringResource(R.string.sources_title)
        activeOverlay == AppOverlay.AIRDROP_HUB -> "空投发现"
        activeOverlay == AppOverlay.AIRDROP_CAMPAIGNS -> "空投活动"
        activeOverlay == AppOverlay.WALLET_MANAGER -> "钱包管理"
        activeOverlay == AppOverlay.GLOBAL_SEARCH -> "全局搜索"
        activeOverlay == AppOverlay.MARKET_DETAIL -> "$selectedMarketSymbol 行情"
        activeOverlay == AppOverlay.TRON_ENERGY -> "TRON 能量租赁助手"
        selectedCommunityProject != null -> "项目微社区"
        else -> stringResource(R.string.project_detail)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showTopBar) {
                CenterAlignedTopAppBar(
                    modifier = Modifier.height(56.dp),
                    title = {
                        Text(
                            secondaryTitle,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        com.airdrophunter.app.ui.components.AppIconButton(
                            icon = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            onClick = {
                                activeOverlay = AppOverlay.NONE
                                selectedProject = null
                                selectedCommunityProject = null
                            }
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        bottomBar = {
            if (!hasSecondaryDestination) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    )
                    AppBottomBar(
                        items = destinations.map { AppNavigationItem(stringResource(it.label), it.icon) },
                        selectedIndex = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding)) {
            AnimatedContent(
                targetState = currentRouteKey,
                transitionSpec = {
                    val initialIsTab = initialState.startsWith("tab:")
                    val targetIsTab = targetState.startsWith("tab:")
                    when {
                        initialIsTab && !targetIsTab -> (
                            slideInHorizontally(tween(AppDesignTokens.Motion.PageMs)) { it / 3 } +
                                fadeIn(tween(AppDesignTokens.Motion.PageMs))
                        ) togetherWith (
                            slideOutHorizontally(tween(AppDesignTokens.Motion.ExitMs)) { -it / 8 } +
                                fadeOut(tween(AppDesignTokens.Motion.ExitMs))
                        )
                        !initialIsTab && targetIsTab -> (
                            slideInHorizontally(tween(AppDesignTokens.Motion.PageMs)) { -it / 3 } +
                                fadeIn(tween(AppDesignTokens.Motion.PageMs))
                        ) togetherWith (
                            slideOutHorizontally(tween(AppDesignTokens.Motion.ExitMs)) { it / 8 } +
                                fadeOut(tween(AppDesignTokens.Motion.ExitMs))
                        )
                        else -> fadeIn(tween(AppDesignTokens.Motion.StandardMs)) togetherWith
                            fadeOut(tween(AppDesignTokens.Motion.StandardMs))
                    }
                },
                label = "app-page-transition"
            ) { route ->
                when {
                    route == "sources" -> SourcesScreen(Modifier.fillMaxSize())
                    route == "airdrop-hub" -> AirdropHubScreen(Modifier.fillMaxSize())
                    route == "airdrop-campaigns" -> AirdropCampaignsScreen(
                        modifier = Modifier.fillMaxSize(),
                        onOpenBinanceOfficial = { activeOverlay = AppOverlay.AIRDROP_HUB }
                    )
                    route == "tron-energy" -> TronEnergyAssistantScreen(
                        modifier = Modifier.fillMaxSize(),
                        onBack = { activeOverlay = AppOverlay.NONE },
                        onOpenBrowser = { address ->
                            activeOverlay = AppOverlay.NONE
                            context.startActivity(Intent(context, BrowserActivity::class.java)
                                .putExtra(BrowserActivity.EXTRA_URL, "https://feee.io/")
                                .putExtra(BrowserActivity.EXTRA_TITLE, "TRON 能量租赁")
                                .putExtra(BrowserActivity.EXTRA_QUICK_COPY_ADDRESS, address))
                        }
                    )
                    route == "global-search" -> GlobalSearchScreen(
                        modifier = Modifier.fillMaxSize(),
                        onCancel = { activeOverlay = AppOverlay.NONE },
                        onCoinSelected = { symbol ->
                            selectedMarketSymbol = symbol
                            activeOverlay = AppOverlay.MARKET_DETAIL
                        },
                        onDappSelected = { name, url ->
                            activeOverlay = AppOverlay.NONE
                            context.startActivity(
                                Intent(context, BrowserActivity::class.java)
                                    .putExtra(BrowserActivity.EXTRA_URL, url)
                                    .putExtra(BrowserActivity.EXTRA_TITLE, name)
                            )
                        }
                    )
                    route == "market-detail" -> CoinMarketDetailScreen(
                        symbol = selectedMarketSymbol,
                        modifier = Modifier.fillMaxSize()
                    )
                    route == "wallet-manager" -> WalletManagerScreen(
                        modifier = Modifier.fillMaxSize(),
                        isGuest = isGuest,
                        sessionScopeKey = sessionScopeKey,
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
                    route.startsWith("community:") -> {
                        val projectId = route.substringAfter("community:")
                        val project = communityTransitionSnapshots[projectId] ?: selectedCommunityProject
                        if (project != null) {
                            MicroCommunityScreen(
                                projectOfficialId = project.officialIdentifier(),
                                projectName = project.name,
                                modifier = Modifier.fillMaxSize(),
                                isGuest = isGuest,
                                sessionScopeKey = sessionScopeKey
                            )
                        } else Box(Modifier.fillMaxSize())
                    }
                    route.startsWith("project:") -> {
                        val projectId = route.substringAfter("project:")
                        val project = projectTransitionSnapshots[projectId] ?: selectedProject
                        if (project != null) {
                            ProjectDetailScreen(
                                project,
                                Modifier.fillMaxSize(),
                                isGuest,
                                onOpenCommunity = { selectedCommunityProject = it },
                                sessionScopeKey = sessionScopeKey
                            )
                        } else Box(Modifier.fillMaxSize())
                    }
                    route == "tab:0" -> tabStateHolder.SaveableStateProvider("bottom-tab-0") {
                        HomeScreen(
                            modifier = Modifier.fillMaxSize(),
                            onOpenSources = { activeOverlay = AppOverlay.SOURCES },
                            onOpenWallet = { activeOverlay = AppOverlay.WALLET_MANAGER },
                            onOpenGlobalSearch = { activeOverlay = AppOverlay.GLOBAL_SEARCH },
                            onOpenProjects = { tab = 2 },
                            onOpenAirdropCampaigns = { activeOverlay = AppOverlay.AIRDROP_CAMPAIGNS },
                            onOpenTronEnergy = { activeOverlay = AppOverlay.TRON_ENERGY },
                            onOpenAccounting = { showAccountingSheet = true },
                            isGuest = isGuest,
                            onRequireAuth = onRequireAuth,
                            sessionScopeKey = sessionScopeKey
                        )
                    }
                    route == "tab:1" -> tabStateHolder.SaveableStateProvider("bottom-tab-1") {
                        CommunitySquareScreen(
                            modifier = Modifier.fillMaxSize(),
                            onProjectSelected = { id, name, description ->
                                selectedCommunityProject = ProjectDto("community:$id", name, description, "tracking", communityOfficialId = id)
                            }
                        )
                    }
                    route == "tab:2" -> tabStateHolder.SaveableStateProvider("bottom-tab-2") {
                        ProjectsScreen(
                            Modifier.fillMaxSize(),
                            { selectedProject = it },
                            isGuest,
                            onRequireAuth,
                            onOpenCommunity = { selectedCommunityProject = it },
                            sessionScopeKey = sessionScopeKey
                        )
                    }
                    else -> tabStateHolder.SaveableStateProvider("bottom-tab-3") {
                        ProfileScreen(
                            Modifier.fillMaxSize(),
                            onLogout,
                            isGuest,
                            onRequireAuth,
                            onManageWallets = { activeOverlay = AppOverlay.WALLET_MANAGER },
                            sessionScopeKey = sessionScopeKey
                        )
                    }
                }
            }
            }
        }
    }
    if (showAccountingSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountingSheet = false },
            sheetState = accountingSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AccountingCenterScreen(
                modifier = Modifier.fillMaxWidth(),
                isGuest = isGuest,
                onClose = { showAccountingSheet = false }
            )
        }
    }
}
