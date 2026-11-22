package com.airdrophunter.app

import android.content.Context

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.airdrophunter.app.ui.theme.AppDesignTokens
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

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
                        getString(R.string.loc_main_tx_callback_invalid),
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
                val barColor = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
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
                            getString(R.string.loc_main_login_unavailable),
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onLogout = {},
                    onOpenAuth = {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.loc_main_login_unavailable),
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

private enum class AppOverlay { NONE, SOURCES, AIRDROP_HUB, AIRDROP_CAMPAIGNS, WALLET_MANAGER, PROJECT_LIBRARY, GLOBAL_SEARCH, MARKET_DETAIL, TRON_ENERGY, MANUAL_ACCOUNTING, TODO }

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
    var accountingInitialSection by rememberSaveable { mutableStateOf("history") }
    var accountingInitialWalletAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var reopenAccountingAfterWalletManager by rememberSaveable { mutableStateOf(false) }
    var accountingReturnTab by rememberSaveable { mutableIntStateOf(0) }
    var launchWalletConnectOnOpen by rememberSaveable { mutableStateOf(false) }

    // Never leave a previous account's secondary screen or editor alive after a session switch.
    LaunchedEffect(sessionScopeKey) {
        activeOverlay = AppOverlay.NONE
        selectedProject = null
        selectedCommunityProject = null
    }

    val hasSecondaryDestination = activeOverlay != AppOverlay.NONE || selectedProject != null || selectedCommunityProject != null
    val showTopBar = activeOverlay in setOf(AppOverlay.SOURCES, AppOverlay.AIRDROP_HUB, AppOverlay.AIRDROP_CAMPAIGNS, AppOverlay.PROJECT_LIBRARY, AppOverlay.MARKET_DETAIL, AppOverlay.TRON_ENERGY, AppOverlay.MANUAL_ACCOUNTING, AppOverlay.TODO) || selectedProject != null || selectedCommunityProject != null

    BackHandler(hasSecondaryDestination) {
        when {
            selectedCommunityProject != null -> selectedCommunityProject = null
            selectedProject != null -> selectedProject = null
            else -> activeOverlay = AppOverlay.NONE
        }
    }
    BackHandler(reopenAccountingAfterWalletManager && tab == 2) {
        reopenAccountingAfterWalletManager = false
        tab = accountingReturnTab
    }

    val destinations = listOf(
        Destination(R.string.tab_home, Icons.Default.Home),
        Destination(R.string.tab_community, Icons.Default.Public),
        Destination(R.string.tab_wallet, Icons.Default.AccountBalanceWallet),
        Destination(R.string.tab_accounting, Icons.Default.Calculate),
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
        selectedCommunityProject != null -> "community:${selectedCommunityProject!!.id}"
        selectedProject != null -> "project:${selectedProject!!.id}"
        activeOverlay == AppOverlay.PROJECT_LIBRARY -> "project-library"
        activeOverlay == AppOverlay.MANUAL_ACCOUNTING -> "manual-accounting"
        activeOverlay == AppOverlay.TODO -> "todo"
        activeOverlay == AppOverlay.GLOBAL_SEARCH -> "global-search"
        activeOverlay == AppOverlay.MARKET_DETAIL -> "market-detail"
        else -> "tab:$tab"
    }
    val secondaryTitle = when {
        activeOverlay == AppOverlay.SOURCES -> stringResource(R.string.sources_title)
        activeOverlay == AppOverlay.AIRDROP_HUB -> stringResource(R.string.loc_hub_official_announcements)
        activeOverlay == AppOverlay.AIRDROP_CAMPAIGNS -> stringResource(R.string.comm_campaigns_title)
        activeOverlay == AppOverlay.GLOBAL_SEARCH -> stringResource(R.string.loc_main_global_search)
        activeOverlay == AppOverlay.MARKET_DETAIL -> stringResource(R.string.loc_main_market_title, selectedMarketSymbol)
        activeOverlay == AppOverlay.TRON_ENERGY -> stringResource(R.string.loc_main_tron_energy)
        selectedCommunityProject != null -> stringResource(R.string.loc_main_community)
        selectedProject != null -> stringResource(R.string.project_detail)
        activeOverlay == AppOverlay.PROJECT_LIBRARY -> stringResource(R.string.loc_main_project_library)
        activeOverlay == AppOverlay.MANUAL_ACCOUNTING -> stringResource(R.string.loc_main_accounting)
        activeOverlay == AppOverlay.TODO -> stringResource(R.string.todo_title)
        else -> stringResource(R.string.project_detail)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showTopBar) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            secondaryTitle,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            modifier = Modifier.semantics { heading() }
                        )
                    },
                    navigationIcon = {
                        com.airdrophunter.app.ui.components.AppIconButton(
                            icon = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            onClick = {
                                when {
                                    selectedCommunityProject != null -> selectedCommunityProject = null
                                    selectedProject != null -> selectedProject = null
                                    else -> activeOverlay = AppOverlay.NONE
                                }
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
                        onSelect = {
                            if (reopenAccountingAfterWalletManager && it != 2) reopenAccountingAfterWalletManager = false
                            tab = it
                        },
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
                            slideInHorizontally(tween(AppDesignTokens.Motion.PageMs)) { it } +
                                fadeIn(tween(AppDesignTokens.Motion.PageMs))
                        ) togetherWith (
                            slideOutHorizontally(tween(AppDesignTokens.Motion.PageMs)) { -it } +
                                fadeOut(tween(AppDesignTokens.Motion.PageMs), targetAlpha = 0f)
                        )
                        !initialIsTab && targetIsTab -> (
                            slideInHorizontally(tween(AppDesignTokens.Motion.PageMs)) { -it } +
                                fadeIn(tween(AppDesignTokens.Motion.PageMs))
                        ) togetherWith (
                            slideOutHorizontally(tween(AppDesignTokens.Motion.PageMs)) { it } +
                                fadeOut(tween(AppDesignTokens.Motion.PageMs), targetAlpha = 0f)
                        )
                        initialIsTab && targetIsTab -> fadeIn(tween(AppDesignTokens.Motion.TabMs)) togetherWith
                            fadeOut(tween(AppDesignTokens.Motion.TabMs))
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
                    route == "manual-accounting" -> ManualAccountingScreen(
                        modifier = Modifier.fillMaxSize(),
                        onBack = { activeOverlay = AppOverlay.NONE }
                    )
                    route == "todo" -> TodoManagementScreen(modifier = Modifier.fillMaxSize())
                    route == "tron-energy" -> TronEnergyAssistantScreen(
                        modifier = Modifier.fillMaxSize(),
                        onBack = { activeOverlay = AppOverlay.NONE },
                        onOpenBrowser = { address ->
                            activeOverlay = AppOverlay.NONE
                            context.startActivity(Intent(context, BrowserActivity::class.java)
                                .putExtra(BrowserActivity.EXTRA_URL, "https://feee.io/")
                                .putExtra(BrowserActivity.EXTRA_TITLE, getString(R.string.loc_main_tron_energy_browser))
                                // This flow carries the selected wallet address into the legacy
                                // wallet-capable WebView; do not hand it off to the browsing-only X5 surface.
                                .putExtra(BrowserActivity.EXTRA_QUICK_COPY_ADDRESS, address)
                                .putExtra(BrowserActivity.EXTRA_FORCE_SYSTEM_WEBVIEW, true))
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
                    route == "project-library" -> tabStateHolder.SaveableStateProvider("wallet-library") {
                        ProjectsScreen(
                            Modifier.fillMaxSize(),
                            { selectedProject = it },
                            isGuest,
                            onRequireAuth,
                            onOpenCommunity = { selectedCommunityProject = it },
                            sessionScopeKey = sessionScopeKey
                        )
                    }
                    route == "tab:2" -> tabStateHolder.SaveableStateProvider("bottom-tab-2") {
                        WalletManagerScreen(
                        modifier = Modifier.fillMaxSize(),
                        isGuest = isGuest,
                        sessionScopeKey = sessionScopeKey,
                        launchConnectOnStart = launchWalletConnectOnOpen,
                        onConnectLaunchHandled = { launchWalletConnectOnOpen = false },
                        onOpenPendingAccounting = { address ->
                            accountingInitialSection = "pending"
                            accountingInitialWalletAddress = address
                            tab = 3
                        },
                        onOpenLibrary = { activeOverlay = AppOverlay.PROJECT_LIBRARY },
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
                                .putExtra(BrowserActivity.EXTRA_SELECTED_WALLET_ID, selectedWallet.id)
                                .putExtra(BrowserActivity.EXTRA_FORCE_SYSTEM_WEBVIEW, true))
                        }
                        )
                    }
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
                            onOpenWallet = { tab = 2 },
                            onOpenGlobalSearch = { activeOverlay = AppOverlay.GLOBAL_SEARCH },
                            onOpenMarketDetail = { symbol ->
                                selectedMarketSymbol = symbol
                                activeOverlay = AppOverlay.MARKET_DETAIL
                            },
                            onOpenProjects = { tab = 2; activeOverlay = AppOverlay.PROJECT_LIBRARY },
                            onOpenAirdropCampaigns = { activeOverlay = AppOverlay.AIRDROP_CAMPAIGNS },
                            onOpenTronEnergy = { activeOverlay = AppOverlay.TRON_ENERGY },
                            onOpenAccounting = { accountingInitialSection = "history"; accountingInitialWalletAddress = null; tab = 3 },
                            onOpenTodo = { activeOverlay = AppOverlay.TODO },
                            onOpenAirdropLibrary = { tab = 1 },
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
                    route == "tab:3" -> tabStateHolder.SaveableStateProvider("bottom-tab-3") {
                        AccountingCenterScreen(
                            modifier = Modifier.fillMaxSize(),
                            isGuest = isGuest,
                            onClose = { tab = 2 },
                            initialSection = accountingInitialSection,
                            initialWalletAddress = accountingInitialWalletAddress,
                            onConnectWallet = {
                                reopenAccountingAfterWalletManager = true
                                launchWalletConnectOnOpen = true
                                accountingReturnTab = 3
                                tab = 2
                            }
                        )
                    }
                    route == "tab:4" -> tabStateHolder.SaveableStateProvider("bottom-tab-4") {
                        ProfileScreen(
                            Modifier.fillMaxSize(),
                            onLogout,
                            isGuest,
                            onRequireAuth,
                            onManageWallets = { tab = 2 },
                            sessionScopeKey = sessionScopeKey
                        )
                    }
                }
            }
        }
    }
}
