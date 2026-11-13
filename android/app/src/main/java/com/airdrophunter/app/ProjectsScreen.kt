package com.airdrophunter.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.ProjectDto
import com.airdrophunter.app.data.matchesSearch
import com.airdrophunter.app.data.officialIdentifier
import com.airdrophunter.app.data.CacheStore
import com.airdrophunter.app.data.ApiClient
import com.airdrophunter.app.data.CommunityCatalogRepository
import com.airdrophunter.app.data.shouldShowAccountingReminder
import androidx.compose.runtime.collectAsState
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.ui.components.AppTextButton
import com.airdrophunter.app.ui.theme.AppDesignTokens
import com.airdrophunter.app.ui.components.appPressFeedback
import com.airdrophunter.app.ui.components.AppChip
import com.airdrophunter.app.ui.components.AppButton
import com.airdrophunter.app.ui.components.AppIconButton
import com.airdrophunter.app.ui.components.AppTextField
import java.util.Locale

@Composable
fun ProjectsScreen(
    modifier: Modifier = Modifier,
    onProjectSelected: (ProjectDto) -> Unit,
    isGuest: Boolean = true,
    onRequireAuth: () -> Unit = {},
    onOpenCommunity: (ProjectDto) -> Unit = {},
    sessionScopeKey: String = "guest",
    vm: ProjectsViewModel? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val projectsViewModel: ProjectsViewModel = vm ?: viewModel(
        factory = ProjectsViewModelFactory(application.appContainer.projectsRepository)
    )
    val state by projectsViewModel.uiState.collectAsStateWithLifecycle()
    val pnlRecords by CacheStore.observePnlRecords().collectAsState(initial = emptyList())
    val savedProjects by CacheStore.observeUserProjects().collectAsState(initial = emptyList())
    val allRatings by CacheStore.observeAllProjectRatings().collectAsState(initial = emptyList())
    var communityDirectory by remember { mutableStateOf(emptyList<com.airdrophunter.app.data.CommunityProjectCatalogDto>()) }
    var selectedFilter by remember { mutableStateOf("全部") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var newProjectDescription by remember { mutableStateOf("") }
    var newProjectOfficialUrl by remember { mutableStateOf("") }
    var newProjectClaimUrl by remember { mutableStateOf("") }
    var selectedCommunityOfficialId by remember { mutableStateOf<String?>(null) }
    var showCommunityPicker by remember { mutableStateOf(false) }
    var communitySearchQuery by remember { mutableStateOf("") }
    var actionProject by remember { mutableStateOf<ProjectDto?>(null) }
    var editingProject by remember { mutableStateOf<ProjectDto?>(null) }
    var deletingProject by remember { mutableStateOf<ProjectDto?>(null) }
    var editProjectName by remember { mutableStateOf("") }
    var editProjectDescription by remember { mutableStateOf("") }
    var editProjectOfficialUrl by remember { mutableStateOf("") }
    var editProjectClaimUrl by remember { mutableStateOf("") }
    var editProjectTwitterUrl by remember { mutableStateOf("") }
    var editProjectStatus by remember { mutableStateOf("tracking") }

    LaunchedEffect(Unit) {
        ApiClient.initialize(context)
        communityDirectory = CommunityCatalogRepository.readCatalog().orEmpty()
        runCatching { CommunityCatalogRepository.refreshCatalog() }
            .onSuccess { communityDirectory = it }
    }

    LaunchedEffect(isGuest, sessionScopeKey) {
        actionProject = null
        editingProject = null
        deletingProject = null
        showAddDialog = false
        newProjectName = ""
        newProjectDescription = ""
        newProjectOfficialUrl = ""
        newProjectClaimUrl = ""
        selectedCommunityOfficialId = null
        showCommunityPicker = false
        communitySearchQuery = ""
        editProjectName = ""
        editProjectDescription = ""
        editProjectOfficialUrl = ""
        editProjectClaimUrl = ""
        editProjectTwitterUrl = ""
        projectsViewModel.loadProjects(isGuest)
    }

    val savedProjectDtos = savedProjects.map { saved ->
        ProjectDto(
            id = saved.projectLocalId,
            name = saved.projectName,
            description = "",
            status = saved.status,
            official_url = communityDirectory.firstOrNull { it.project_official_id == saved.projectOfficialId }?.website_url.orEmpty(),
            communityOfficialId = saved.projectOfficialId
        )
    }
    val delistedOfficialIds = remember(communityDirectory) {
        communityDirectory.filterNot { it.is_active }.map { it.project_official_id }.toSet()
    }
    val visibleProjects = (savedProjectDtos + state.projects).distinctBy {
        it.communityOfficialId?.let { officialId -> "official:$officialId" } ?: "local:${it.id}"
    }.map { project ->
        project.copy(communityDelisted = project.communityOfficialId in delistedOfficialIds)
    }

    val filterOptions = listOf("全部" to visibleProjects.size) +
        listOf(
            "进行中" to visibleProjects.count { it.status == "tracking" },
            "待开始" to visibleProjects.count { it.status == "upcoming" },
            "已完成" to visibleProjects.count { it.status == "completed" }
        )
    val filtered = when (selectedFilter) {
        "进行中" -> visibleProjects.filter { it.status == "tracking" }
        "待开始" -> visibleProjects.filter { it.status == "upcoming" }
        "已完成" -> visibleProjects.filter { it.status == "completed" }
        else -> visibleProjects
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("库管理", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            AppIconButton(
                icon = Icons.Default.FolderOpen,
                contentDescription = "筛选项目",
                onClick = { selectedFilter = if (selectedFilter == "全部") "进行中" else "全部" }
            )
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterOptions.forEach { (label, count) ->
                AppChip(
                    text = "$label $count",
                    selected = selectedFilter == label,
                    onClick = { selectedFilter = label }
                )
            }
        }

        state.error?.takeIf { visibleProjects.isNotEmpty() }?.let { error ->
            Text(error, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        when {
            state.isLoading && visibleProjects.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            state.error != null && visibleProjects.isEmpty() -> EmptyProjects(
                modifier = Modifier.weight(1f),
                title = "暂时无法加载项目",
                subtitle = "服务器繁忙，请稍后重试。"
            )
            filtered.isEmpty() -> EmptyProjects(
                modifier = Modifier.weight(1f),
                title = "还没有添加项目",
                subtitle = if (isGuest) "添加的项目只保存在这台设备上。" else "添加项目后，这里会显示你的项目与任务进度。"
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { project ->
                    val pnl = pnlRecords.firstOrNull { it.projectLocalId == project.id }
                    val savedProjectRecord = savedProjects.firstOrNull { it.projectLocalId == project.id }
                    val showAccountingReminder = savedProjectRecord?.let {
                        shouldShowAccountingReminder(it.createdAt, it.interactionCount, System.currentTimeMillis())
                    } ?: false
                    val netProfit = pnl?.netProfitUsd ?: 0.0
                    val projectRatings = allRatings.filter { it.projectOfficialId == project.communityOfficialId }
                    val communityAverage = projectRatings.takeIf { it.isNotEmpty() }?.map { it.score }?.average()
                    val positiveRate = projectRatings.takeIf { it.isNotEmpty() }
                        ?.let { ratings -> ratings.count { it.score >= 4 } * 100 / ratings.size }
                    val communityLabel = when {
                        positiveRate == null -> "暂无评价"
                        positiveRate >= 95 -> "好评如潮"
                        positiveRate >= 85 -> "特别好评"
                        positiveRate >= 75 -> "多半好评"
                        else -> "褒贬不一"
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .appPressFeedback(onClick = { onProjectSelected(project) }, scale = 0.96f),
                        shape = RoundedCornerShape(AppDesignTokens.Radius.Lg.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(project.name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                    val statusLabel = when (project.status) { "tracking" -> "进行中"; "completed" -> "已完成"; else -> "待开始" }
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(999.dp)) {
                                        Text(statusLabel, Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (project.communityDelisted || showAccountingReminder) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (project.communityDelisted) {
                                            Surface(color = Color(0xFFFFE8E8), shape = RoundedCornerShape(999.dp)) {
                                                Text("社区已下架", Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    color = Color(0xFFB42318), style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        if (showAccountingReminder) {
                                            Surface(color = Color(0xFFFFF4E5), shape = RoundedCornerShape(999.dp)) {
                                                Text("待记账", Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    color = Color(0xFF9A5B12), style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                                if (project.description.isNotBlank()) {
                                    Text(project.description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("真实净盈亏", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                    Text(String.format(Locale.US, "%s$%.2f",
                                        if (netProfit >= 0) "+" else "-", kotlin.math.abs(netProfit)),
                                        color = if (netProfit >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(onClick = {
                                    when {
                                        project.communityDelisted -> Toast.makeText(context, "社区已下架，项目仍保留在本地私人模式", Toast.LENGTH_SHORT).show()
                                        project.communityOfficialId != null -> onOpenCommunity(project)
                                        else -> Toast.makeText(context, "这是纯本地项目，尚未关联社区标准项目", Toast.LENGTH_SHORT).show()
                                    }
                                }, shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFFFFF4E5), border = BorderStroke(1.dp, Color(0xFFFFD9A8))) {
                                    Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (project.communityDelisted) "私人模式" else if (project.communityOfficialId != null) "微社区" else "本地项目", color = Color(0xFF9A5B12), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        if (!project.communityDelisted && project.communityOfficialId != null && communityAverage != null) {
                                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFA629), modifier = Modifier.size(13.dp))
                                            Text(String.format(Locale.US, "%.1f", communityAverage), color = Color(0xFF9A5B12),
                                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(communityLabel, color = Color(0xFFB7791F), style = MaterialTheme.typography.labelSmall)
                            }
                            if (state.projects.any { it.id == project.id }) {
                                Box {
                                    IconButton(onClick = { actionProject = project }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "项目管理",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    DropdownMenu(
                                        expanded = actionProject?.id == project.id,
                                        onDismissRequest = { actionProject = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("编辑项目") },
                                            onClick = {
                                                actionProject = null
                                                editProjectName = project.name
                                                editProjectDescription = project.description
                                                editProjectOfficialUrl = project.official_url
                                                editProjectClaimUrl = project.claim_url
                                                editProjectTwitterUrl = project.twitter_url
                                                editProjectStatus = project.status
                                                editingProject = project
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("删除项目", color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                actionProject = null
                                                deletingProject = project
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    editingProject?.let { project ->
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) editingProject = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("编辑项目", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(editProjectName, { editProjectName = it }, "项目名称")
                    AppTextField(editProjectDescription, { editProjectDescription = it }, "项目说明", singleLine = false)
                    Text("项目状态", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("tracking" to "进行中", "upcoming" to "待开始", "completed" to "已完成").forEach { (value, label) ->
                            AppChip(
                                text = label,
                                selected = editProjectStatus == value,
                                onClick = { editProjectStatus = value }
                            )
                        }
                    }
                    AppTextField(editProjectOfficialUrl, { editProjectOfficialUrl = it }, "项目官网 HTTPS 链接")
                    AppTextField(editProjectClaimUrl, { editProjectClaimUrl = it }, "资格查询 HTTPS 链接")
                    AppTextField(editProjectTwitterUrl, { editProjectTwitterUrl = it }, "社交链接 HTTPS")
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                AppButton(
                    text = "保存修改",
                    onClick = {
                        projectsViewModel.updateProject(
                            project,
                            com.airdrophunter.app.data.ProjectWriteRequest(
                                name = editProjectName.trim(),
                                description = editProjectDescription.trim(),
                                status = editProjectStatus,
                                official_url = editProjectOfficialUrl.trim(),
                                claim_url = editProjectClaimUrl.trim(),
                                twitter_url = editProjectTwitterUrl.trim(),
                                deadline = project.deadline,
                                expected_income = project.expected_income,
                                gas_cost = project.gas_cost,
                                actual_income = project.actual_income
                            ),
                            isGuest
                        )
                        editingProject = null
                    },
                    enabled = editProjectName.isNotBlank() && !state.isSaving,
                    loading = state.isSaving
                )
            },
            dismissButton = {
                AppTextButton(enabled = !state.isSaving, onClick = { editingProject = null }) { Text("取消") }
            }
        )
    }

    deletingProject?.let { project ->
        AlertDialog(
            onDismissRequest = { deletingProject = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("删除项目？", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("将从项目列表中移除“${project.name}”。已确认的 Gas、收益和历史账本不会被删除。", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                AppTextButton(onClick = {
                    projectsViewModel.deleteProject(project, isGuest)
                    deletingProject = null
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { AppTextButton(onClick = { deletingProject = null }) { Text("取消") } }
        )
    }

    if (showCommunityPicker) {
        val selectableProjects = communityDirectory.filter {
            it.is_active && (communitySearchQuery.isBlank() || it.matchesSearch(communitySearchQuery))
        }
        AlertDialog(
            onDismissRequest = { showCommunityPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("选择社区项目", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(communitySearchQuery, { communitySearchQuery = it }, "搜索项目名称、链或标签")
                    if (selectableProjects.isEmpty()) {
                        Text(
                            if (communityDirectory.isEmpty()) "社区目录正在后台准备；联网后会自动更新。你也可以先取消并创建私人项目。"
                            else "没有找到匹配的社区项目。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            selectableProjects.forEach { item ->
                                Surface(
                                    onClick = {
                                        selectedCommunityOfficialId = item.project_official_id
                                        newProjectName = item.name
                                        newProjectDescription = item.description
                                        newProjectOfficialUrl = item.website_url
                                        newProjectClaimUrl = ""
                                        showCommunityPicker = false
                                    },
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(item.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                        Text(item.chain, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                        if (item.description.isNotBlank()) Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = { showCommunityPicker = false }) { Text("完成") } },
            dismissButton = { AppTextButton(onClick = { showCommunityPicker = false }) { Text("取消") } }
        )
    } else if (showAddDialog) AlertDialog(
        onDismissRequest = { showAddDialog = false },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("添加项目", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(newProjectName, { newProjectName = it }, "项目名称", modifier = Modifier.weight(1f))
                    AppTextButton(onClick = { communitySearchQuery = ""; showCommunityPicker = true }) {
                        Text(if (selectedCommunityOfficialId == null) "从社区中选择" else "已关联 · 更换")
                    }
                }
                AppTextField(newProjectDescription, { newProjectDescription = it }, "项目简介", singleLine = false)
                AppTextField(newProjectOfficialUrl, { newProjectOfficialUrl = it }, "项目官网 HTTPS 链接")
                AppTextField(newProjectClaimUrl, { newProjectClaimUrl = it }, "官方资格查询 HTTPS 链接（可选）")
                Text("资格查询链接请填写项目方官方页面，不要填写来历不明的第三方链接。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            AppButton(
                text = "保存项目",
                onClick = {
                    projectsViewModel.createProject(com.airdrophunter.app.data.ProjectWriteRequest(
                        name = newProjectName.trim(),
                        description = newProjectDescription.trim(),
                        status = "tracking",
                        official_url = newProjectOfficialUrl.trim(),
                        claim_url = newProjectClaimUrl.trim(),
                        community_official_id = selectedCommunityOfficialId
                    ), isGuest)
                    showAddDialog = false
                    newProjectName = ""; newProjectDescription = ""; newProjectOfficialUrl = ""; newProjectClaimUrl = ""; selectedCommunityOfficialId = null
                },
                enabled = newProjectName.isNotBlank() && !state.isSaving,
                loading = state.isSaving
            )
        },
        dismissButton = { AppTextButton(onClick = { showAddDialog = false }) { Text("取消") } }
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        FloatingActionButton(
            onClick = {
                newProjectName = ""; newProjectDescription = ""; newProjectOfficialUrl = ""; newProjectClaimUrl = ""
                selectedCommunityOfficialId = null
                showAddDialog = true
            },
            modifier = Modifier.padding(end = 20.dp, bottom = 20.dp).size(58.dp),
            shape = RoundedCornerShape(50),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.surface
        ) { Icon(Icons.Default.Add, "添加项目") }
    }
}

@Composable
private fun EmptyProjects(modifier: Modifier = Modifier, title: String, subtitle: String) {
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f), modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
