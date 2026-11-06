package com.airdrophunter.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MockEntry(
    val title: String,
    val subtitle: String,
    val iconKey: String
)

data class MockMarket(
    val symbol: String,
    val price: String,
    val change: String,
    val positive: Boolean,
    val spark: List<Float>
)

data class Project(
    val name: String,
    val status: String,
    val progress: Float,
    val todo: Int,
    val deadline: String,
    val wallet: String,
    val filter: String
)

data class HomeMockState(
    val weekTotal: Int = 12,
    val weekDone: Int = 5,
    val weekRange: String = "11/01 - 11/12",
    val entries: List<MockEntry> = listOf(
        MockEntry("空投导航", "聚合 48 条链上任务线索", "explore"),
        MockEntry("安全查钱包", "检查地址风险与授权状态", "shield"),
        MockEntry("我的项目", "管理参与项目与任务进度", "folder")
    ),
    val markets: List<MockMarket> = listOf(
        MockMarket("BTC", "$68,420.50", "+2.48%", true, listOf(0.25f,0.38f,0.31f,0.58f,0.48f,0.75f,0.88f)),
        MockMarket("ETH", "$2,485.20", "-1.26%", false, listOf(0.8f,0.68f,0.76f,0.52f,0.6f,0.32f,0.24f)),
        MockMarket("SOL", "$162.84", "+4.12%", true, listOf(0.2f,0.28f,0.5f,0.43f,0.66f,0.72f,0.92f))
    )
)

class DashboardMockViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeMockState())
    val state: StateFlow<HomeMockState> = _state
}

data class ProjectsMockState(
    val filters: List<Pair<String, Int>> = listOf(
        "全部" to 12, "待交互" to 7, "已提交" to 3, "已空投" to 2
    ),
    val projects: List<Project> = listOf(
        Project("Linea 空投任务", "待交互", .42f, 3, "11/12", "0x7f...a9c", "待交互"),
        Project("Scroll 生态交互", "待交互", .28f, 5, "11/15", "0x2a...81d", "待交互"),
        Project("zkSync Era", "已提交", .86f, 1, "已提交", "0x91...c20", "已提交"),
        Project("Starknet 任务", "已空投", 1f, 0, "已完成", "0xb4...e17", "已空投")
    )
)

class ProjectsMockViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProjectsMockState())
    val state: StateFlow<ProjectsMockState> = _state
}

data class ProfileMockState(
    val tracked: String = "12",
    val completed: String = "5",
    val earnings: String = "1,240",
    val days: String = "18"
)

class ProfileMockViewModel : ViewModel() {
    private val _state = MutableStateFlow(ProfileMockState())
    val state: StateFlow<ProfileMockState> = _state
}