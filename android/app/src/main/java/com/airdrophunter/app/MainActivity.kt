package com.airdrophunter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AirdropHunterApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun AirdropHunterApp(projectsViewModel: ProjectsViewModel = viewModel()) {
    val uiState by projectsViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        projectsViewModel.loadProjects()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Airdrop Hunter") }) }
    ) { innerPadding ->
        HomeScreen(
            contentPadding = innerPadding,
            uiState = uiState,
            onRetry = projectsViewModel::loadProjects
        )
    }
}

@androidx.compose.runtime.Composable
private fun HomeScreen(
    contentPadding: PaddingValues,
    uiState: ProjectsUiState,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("你的空投管理中心", style = MaterialTheme.typography.headlineSmall)
        Text("项目数据由后端 API 提供；当前为开发阶段模拟数据。")

        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.error != null -> {
                Text("加载失败：${uiState.error}")
                Button(onClick = onRetry) { Text("重新加载") }
            }
            uiState.projects.isEmpty() -> Text("暂时没有项目")
            else -> uiState.projects.forEach { project ->
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        Text(project.description)
                        Text("状态：${project.status}")
                    }
                }
            }
        }
    }
}
