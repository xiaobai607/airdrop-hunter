package com.airdrophunter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
@Composable
private fun AirdropHunterApp() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Airdrop Hunter") }) }
    ) { innerPadding ->
        HomeScreen(contentPadding = innerPadding)
    }
}

@Composable
private fun HomeScreen(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "你的空投管理中心",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "第一阶段工程骨架已启动。接下来将逐步接入项目发现、任务、钱包地址、积分和收益记录。",
            style = MaterialTheme.typography.bodyMedium
        )
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("项目与活动", style = MaterialTheme.typography.titleMedium)
                Text("统一整理项目资料、参与条件和截止时间。")
            }
        }
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("任务进度", style = MaterialTheme.typography.titleMedium)
                Text("记录待完成、进行中和已确认的任务。")
            }
        }
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("钱包与收益", style = MaterialTheme.typography.titleMedium)
                Text("仅管理公开地址和用户录入的成本、积分及实际收益。")
            }
        }
    }
}
