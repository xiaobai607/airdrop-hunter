package com.airdrophunter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.airdrophunter.app.ui.theme.AirdropHunterTheme
import com.airdrophunter.app.ui.theme.AppColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AirdropHunterTheme {
                AirdropHunterApp()
            }
        }
    }
}

private data class AppDestination(
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun AirdropHunterApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val destinations = listOf(
        AppDestination("发现", Icons.Default.Explore),
        AppDestination("空投记录", Icons.Default.TrackChanges),
        AppDestination("项目管理", Icons.Default.FolderOpen)
    )
    val title = when (selectedTab) {
        0 -> "发现"
        1 -> "空投记录"
        else -> "项目管理"
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Background,
                    titleContentColor = AppColors.TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = AppColors.Navigation,
                contentColor = AppColors.TextPrimary,
                tonalElevation = 0.dp
            ) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            androidx.compose.material3.Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Primary,
                            selectedTextColor = AppColors.Primary,
                            unselectedIconColor = AppColors.TextSecondary,
                            unselectedTextColor = AppColors.TextSecondary,
                            indicatorColor = AppColors.NavIndicator
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DiscoverScreen(modifier = Modifier.fillMaxSize())
                1 -> AirdropDiscoveryScreen(modifier = Modifier.fillMaxSize())
                else -> ProjectsScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
