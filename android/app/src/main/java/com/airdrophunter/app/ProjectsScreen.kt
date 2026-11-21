package com.airdrophunter.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.airdrophunter.app.ui.components.StaggeredListItem
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
    var selectedFilter by remember { mutableStateOf("all") }
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
    val projectListState = rememberLazyListState()
    var compactFab by remember { mutableStateOf(false) }
    LaunchedEffect(projectListState) {
        var previousIndex = projectListState.firstVisibleItemIndex
        var previousOffset = projectListState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(
                projectListState.isScrollInProgress,
                projectListState.firstVisibleItemIndex,
                projectListState.firstVisibleItemScrollOffset
            )
        }.collect { (scrolling, index, offset) ->
            val movingDown = index > previousIndex || (index == previousIndex && offset > previousOffset)
            compactFab = scrolling && movingDown
            previousIndex = index
            previousOffset = offset
        }
    }
    val fabScale by animateFloatAsState(
        targetValue = if (compactFab) 0.88f else 1f,
        animationSpec = tween(AppDesignTokens.Motion.FastMs),
        label = "project-fab-scale"
    )
    val fabAlpha by animateFloatAsState(
        targetValue = if (compactFab) 0.6f else 1f,
        animationSpec = tween(AppDesignTokens.Motion.FastMs),
        label = "project-fab-alpha"
    )

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

    val filterOptions = listOf(
        Triple("all", R.string.projects_filter_all, visibleProjects.size),
        Triple("tracking", R.string.projects_filter_tracking, visibleProjects.count { it.status == "tracking" }),
        Triple("upcoming", R.string.projects_filter_upcoming, visibleProjects.count { it.status == "upcoming" }),
        Triple("completed", R.string.projects_filter_completed, visibleProjects.count { it.status == "completed" })
    )
    val filtered = when (selectedFilter) {
        "tracking" -> visibleProjects.filter { it.status == "tracking" }
        "upcoming" -> visibleProjects.filter { it.status == "upcoming" }
        "completed" -> visibleProjects.filter { it.status == "completed" }
        else -> visibleProjects
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(context.getString(R.string.projects_management_title), modifier = Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                Text(context.getString(R.string.projects_management_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            AppIconButton(
                icon = Icons.Default.Add,
                contentDescription = context.getString(R.string.projects_create),
                onClick = { showAddDialog = true }
            )
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterOptions.forEach { (filterKey, labelRes, count) ->
                AppChip(
                    text = "${context.getString(labelRes)} $count",
                    selected = selectedFilter == filterKey,
                    onClick = { selectedFilter = filterKey }
                )
            }
        }

        state.error?.takeIf { visibleProjects.isNotEmpty() }?.let { error ->
            Text(context.getString(R.string.projects_server_error), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        when {
            state.isLoading && visibleProjects.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            state.error != null && visibleProjects.isEmpty() -> EmptyProjects(
                modifier = Modifier.weight(1f),
                title = context.getString(R.string.projects_load_error),
                subtitle = context.getString(R.string.projects_server_error)
            )
            filtered.isEmpty() -> EmptyProjects(
                modifier = Modifier.weight(1f),
                title = if (visibleProjects.isEmpty()) context.getString(R.string.projects_empty) else context.getString(R.string.projects_empty_filtered),
                subtitle = if (visibleProjects.isEmpty()) {
                    if (isGuest) context.getString(R.string.projects_guest_storage) else context.getString(R.string.projects_empty_help)
                } else {
                    context.getString(R.string.projects_filter_empty, context.getString(filterOptions.firstOrNull { it.first == selectedFilter }?.second ?: R.string.projects_filter_all))
                }
            )
            else -> LazyColumn(
                state = projectListState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(filtered, key = { _, project -> project.id }) { index, project ->
                    StaggeredListItem(project.id, index) {
                    val pnl = pnlRecords.firstOrNull { it.projectLocalId == project.id }
                    val savedProjectRecord = savedProjects.firstOrNull { it.projectLocalId == project.id }
                    val showAccountingReminder = savedProjectRecord?.let {
                        shouldShowAccountingReminder(it.createdAt, it.interactionCount, System.currentTimeMillis())
                    } ?: false
                    val netProfit = pnl?.takeIf { it.usdProfitComplete }?.netProfitUsd
                    val projectRatings = allRatings.filter { it.projectOfficialId == project.communityOfficialId }
                    val communityAverage = projectRatings.takeIf { it.isNotEmpty() }?.map { it.score }?.average()
                    val positiveRate = projectRatings.takeIf { it.isNotEmpty() }
                        ?.let { ratings -> ratings.count { it.score >= 4 } * 100 / ratings.size }
                    val communityLabel = when {
                        positiveRate == null -> context.getString(R.string.projects_no_rating)
                        positiveRate >= 95 -> context.getString(R.string.projects_reputation_positive)
                        positiveRate >= 85 -> context.getString(R.string.projects_reputation_great)
                        positiveRate >= 75 -> context.getString(R.string.projects_reputation_good)
                        else -> context.getString(R.string.projects_reputation_mixed)
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
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    val statusLabel = when (project.status) { "tracking" -> context.getString(R.string.projects_filter_tracking); "completed" -> context.getString(R.string.projects_filter_completed); else -> context.getString(R.string.projects_filter_upcoming) }
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                                        Text(statusLabel, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (project.communityDelisted || showAccountingReminder) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (project.communityDelisted) {
                                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                                                Text(context.getString(R.string.projects_status_delisted), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        if (showAccountingReminder) {
                                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp)) {
                                                Text(context.getString(R.string.projects_pending_accounting), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall,
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
                                    Text(context.getString(R.string.projects_real_pnl), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                    Text(
                                        when {
                                            pnl == null -> context.getString(R.string.projects_no_ledger)
                                            !pnl.usdProfitComplete -> context.getString(R.string.projects_estimate_incomplete)
                                            else -> String.format(Locale.US, "%s$%.2f",
                                                if ((netProfit ?: 0.0) >= 0) "+" else "-",
                                                kotlin.math.abs(netProfit ?: 0.0))
                                        },
                                        color = when {
                                            pnl == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                            !pnl.usdProfitComplete -> MaterialTheme.colorScheme.onSurfaceVariant
                                            (netProfit ?: 0.0) >= 0 -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    modifier = Modifier.appPressFeedback(
                                        onClick = {
                                            when {
                                                project.communityDelisted -> Toast.makeText(context, context.getString(R.string.projects_delisted_notice), Toast.LENGTH_SHORT).show()
                                                project.communityOfficialId != null -> onOpenCommunity(project)
                                                else -> Toast.makeText(context, context.getString(R.string.projects_local_notice), Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        scale = 0.94f
                                    ),
                                    shape = RoundedCornerShape(AppDesignTokens.Radius.Full.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(if (project.communityDelisted) context.getString(R.string.projects_private_mode) else if (project.communityOfficialId != null) context.getString(R.string.projects_community) else context.getString(R.string.projects_local), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        if (!project.communityDelisted && project.communityOfficialId != null && communityAverage != null) {
                                            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(13.dp))
                                            Text(String.format(Locale.US, "%.1f", communityAverage), color = MaterialTheme.colorScheme.tertiary,
                                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(communityLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                            if (state.projects.any { it.id == project.id }) {
                                Box {
                                    AppIconButton(
                                        icon = Icons.Default.MoreVert,
                                        contentDescription = context.getString(R.string.projects_management_title),
                                        onClick = { actionProject = project }
                                    )
                                    DropdownMenu(
                                        expanded = actionProject?.id == project.id,
                                        onDismissRequest = { actionProject = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.projects_edit)) },
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
                                            text = { Text(context.getString(R.string.projects_delete), color = MaterialTheme.colorScheme.error) },
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
    }


    editingProject?.let { project ->
        AlertDialog(
            onDismissRequest = { if (!state.isSaving) editingProject = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(context.getString(R.string.projects_edit), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(editProjectName, { editProjectName = it }, context.getString(R.string.projects_name))
                    AppTextField(editProjectDescription, { editProjectDescription = it }, context.getString(R.string.projects_description), singleLine = false)
                    Text(context.getString(R.string.projects_status), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("tracking" to R.string.projects_filter_tracking, "upcoming" to R.string.projects_filter_upcoming, "completed" to R.string.projects_filter_completed).forEach { (value, label) ->
                            AppChip(
                                text = context.getString(label),
                                selected = editProjectStatus == value,
                                onClick = { editProjectStatus = value }
                            )
                        }
                    }
                    AppTextField(editProjectOfficialUrl, { editProjectOfficialUrl = it }, context.getString(R.string.projects_website))
                    AppTextField(editProjectClaimUrl, { editProjectClaimUrl = it }, context.getString(R.string.projects_claim_url))
                    AppTextField(editProjectTwitterUrl, { editProjectTwitterUrl = it }, context.getString(R.string.projects_social_url))
                    state.error?.let { Text(localizedProjectMessage(context, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                AppButton(
                    text = context.getString(R.string.projects_save_changes),
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
                AppTextButton(enabled = !state.isSaving, onClick = { editingProject = null }) { Text(context.getString(R.string.cancel)) }
            }
        )
    }

    deletingProject?.let { project ->
        AlertDialog(
            onDismissRequest = { deletingProject = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(context.getString(R.string.projects_delete_title), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(context.getString(R.string.projects_delete_message, project.name), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                AppTextButton(onClick = {
                    projectsViewModel.deleteProject(project, isGuest)
                    deletingProject = null
                }) { Text(context.getString(R.string.projects_confirm_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { AppTextButton(onClick = { deletingProject = null }) { Text(context.getString(R.string.cancel)) } }
        )
    }

    if (showCommunityPicker) {
        val selectableProjects = communityDirectory.filter {
            it.is_active && (communitySearchQuery.isBlank() || it.matchesSearch(communitySearchQuery))
        }
        AlertDialog(
            onDismissRequest = { showCommunityPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(context.getString(R.string.projects_choose_community), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(communitySearchQuery, { communitySearchQuery = it }, context.getString(R.string.projects_search))
                    if (selectableProjects.isEmpty()) {
                        Text(
                            if (communityDirectory.isEmpty()) context.getString(R.string.projects_community_loading)
                            else context.getString(R.string.projects_community_no_match),
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
                                    shape = RoundedCornerShape(AppDesignTokens.Radius.Md.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            confirmButton = { AppTextButton(onClick = { showCommunityPicker = false }) { Text(context.getString(R.string.projects_done)) } },
            dismissButton = { AppTextButton(onClick = { showCommunityPicker = false }) { Text(context.getString(R.string.cancel)) } }
        )
    } else if (showAddDialog) AlertDialog(
        onDismissRequest = { showAddDialog = false },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(context.getString(R.string.projects_add), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(newProjectName, { newProjectName = it }, context.getString(R.string.projects_name), modifier = Modifier.weight(1f))
                    AppTextButton(onClick = { communitySearchQuery = ""; showCommunityPicker = true }) {
                        Text(if (selectedCommunityOfficialId == null) context.getString(R.string.projects_select_community) else context.getString(R.string.projects_change_link))
                    }
                }
                AppTextField(newProjectDescription, { newProjectDescription = it }, context.getString(R.string.projects_intro), singleLine = false)
                AppTextField(newProjectOfficialUrl, { newProjectOfficialUrl = it }, context.getString(R.string.projects_website))
                AppTextField(newProjectClaimUrl, { newProjectClaimUrl = it }, context.getString(R.string.projects_claim_url_optional))
                Text(context.getString(R.string.projects_claim_url_help),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(localizedProjectMessage(context, it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            AppButton(
                text = context.getString(R.string.projects_save),
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
        dismissButton = { AppTextButton(onClick = { showAddDialog = false }) { Text(context.getString(R.string.cancel)) } }
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        FloatingActionButton(
            onClick = {
                newProjectName = ""; newProjectDescription = ""; newProjectOfficialUrl = ""; newProjectClaimUrl = ""
                selectedCommunityOfficialId = null
                showAddDialog = true
            },
            modifier = Modifier.padding(end = 16.dp, bottom = 16.dp).size(56.dp)
                .graphicsLayer { scaleX = fabScale; scaleY = fabScale; alpha = fabAlpha },
            shape = RoundedCornerShape(50),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.surface
        ) { Icon(Icons.Default.Add, context.getString(R.string.projects_add_accessibility)) }
    }
}

@Composable
private fun EmptyProjects(modifier: Modifier = Modifier, title: String, subtitle: String) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
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

private fun localizedProjectMessage(context: android.content.Context, message: String): String = when (message) {
    "__PROJECT_SYNC_CACHED__" -> context.getString(R.string.projects_sync_cached)
    "__PROJECT_NETWORK_ERROR__" -> context.getString(R.string.projects_network_error)
    "__PROJECT_ADDED__" -> context.getString(R.string.projects_added)
    "__PROJECT_UPDATED__" -> context.getString(R.string.projects_updated)
    "__PROJECT_DELETED__" -> context.getString(R.string.projects_deleted)
    else -> message
}

