package com.airdrophunter.app

import androidx.compose.ui.res.stringResource

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.airdrophunter.app.ui.theme.AirdropHunterTheme
import com.google.accompanist.navigation.material.BottomSheetNavigator
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.ModalBottomSheetLayout
import com.reown.appkit.ui.appKitGraph
import com.reown.appkit.ui.openAppKit

/**
 * Wallet connection surface launched above the X5 browser.
 * It never receives or reloads the DApp URL, so X5 remains the browsing surface.
 */
class WalletConnectActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AirdropHunterTheme { WalletConnectScreen(onClose = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialNavigationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WalletConnectScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val ready by WalletConnectInitializer.ready.collectAsStateWithLifecycle()
    val initializing by WalletConnectInitializer.initializing.collectAsStateWithLifecycle()
    val error by WalletConnectInitializer.error.collectAsStateWithLifecycle()
    val accounts by WalletConnectSessionDelegate.accounts.collectAsStateWithLifecycle()
    val sessionGeneration by WalletConnectSessionDelegate.sessionGeneration.collectAsStateWithLifecycle()
    var initialSessionGeneration by remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        WalletConnectInitializer.ensureInitialized(context.applicationContext as Application)
    }

    LaunchedEffect(sessionGeneration, accounts) {
        val initial = initialSessionGeneration
        if (initial == null) {
            initialSessionGeneration = sessionGeneration
        } else if (sessionGeneration != initial && accounts.isNotEmpty()) {
            // Return to the still-open X5 page immediately after wallet approval.
            onClose()
        }
    }

    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    val sheetNavigator = remember(sheetState) { BottomSheetNavigator(sheetState) }
    val navController = rememberNavController(sheetNavigator)

    ModalBottomSheetLayout(sheetNavigator) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.loc_browser_05)) },
                    navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.loc_browser_04)) } }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    accounts.isNotEmpty() -> {
                        Text(stringResource(R.string.loc_wallet_connect_connected), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.loc_wallet_connect_account_count, accounts.size))
                        accounts.take(3).forEach { account -> Text(account.address) }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { navController.openAppKit() }) {
                            Text(stringResource(R.string.loc_wallet_connect_manage))
                        }
                    }
                    ready -> {
                        Text(stringResource(R.string.loc_wallet_connect_intro))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { navController.openAppKit() }) {
                            Text(stringResource(R.string.loc_browser_35))
                        }
                    }
                    else -> {
                        if (initializing) CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(error ?: stringResource(R.string.loc_browser_32))
                        Button(
                            onClick = {
                                WalletConnectInitializer.ensureInitialized(context.applicationContext as Application)
                                Toast.makeText(context, context.getString(R.string.loc_wallet_connect_checking), Toast.LENGTH_SHORT).show()
                            }
                        ) { Text(stringResource(R.string.loc_wallet_connect_retry)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onClose) { Text(stringResource(R.string.loc_wallet_connect_back_webpage)) }
            }
        }
        NavHost(navController, startDestination = "wallet_connect_host") {
            composable("wallet_connect_host") { }
            appKitGraph(navController)
        }
    }
}
