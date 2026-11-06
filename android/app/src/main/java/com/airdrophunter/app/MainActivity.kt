package com.airdrophunter.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.airdrophunter.app.data.ApiClient
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
                    else -> AirdropHunterApp { lifecycleScope.launch { TokenStore.clearToken(applicationContext) } }
                }
            }
        }
    }
}
private data class Destination(val label: String, val icon: ImageVector)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirdropHunterApp(onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val destinations = listOf(Destination("发现", Icons.Default.Explore), Destination("我的", Icons.Default.Person), Destination("项目管理", Icons.Default.FolderOpen))
    val title = when (tab) { 0 -> "发现"; 1 -> "我的"; else -> "项目管理" }
    Scaffold(
        containerColor = AppColors.Background,
        topBar = { TopAppBar(title = { Text(title) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background, titleContentColor = AppColors.TextPrimary)) },
        bottomBar = {
            NavigationBar(containerColor = AppColors.Navigation, tonalElevation = 0.dp) {
                destinations.forEachIndexed { index, d ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Icon(d.icon, d.label) }, label = { Text(d.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AppColors.Primary, selectedTextColor = AppColors.Primary, unselectedIconColor = AppColors.TextSecondary, unselectedTextColor = AppColors.TextSecondary, indicatorColor = AppColors.NavIndicator))
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> DiscoverScreen(Modifier.fillMaxSize())
                1 -> ProfileScreen(Modifier.fillMaxSize(), onLogout)
                else -> ProjectsScreen(Modifier.fillMaxSize())
            }
        }
    }
}
