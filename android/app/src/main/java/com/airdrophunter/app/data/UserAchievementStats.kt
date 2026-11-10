package com.airdrophunter.app.data

import java.time.Instant
import java.time.ZoneId

internal fun calculateLocalUserStats(
    projects: List<UserProjectEntity>,
    interactions: List<UserInteractionEntity>,
    zoneId: ZoneId = ZoneId.systemDefault()
): UserStatsDto {
    val ledgerProjects = projects.filter { it.interactionCount > 0 }
    val evaluated = ledgerProjects.filter { it.usdProfitComplete }
    val gas = ledgerProjects.sumOf { it.totalGasCostUsd }
    // Incomplete rows may contain compatibility sentinel values. Never blend
    // those values into a total presented as confirmed USD income or profit.
    val reward = evaluated.sumOf { it.totalActualRewardUsd }
    val net = evaluated.sumOf { it.netProfitUsd }
    val profitable = evaluated.count { it.netProfitUsd > 0.0 }
    val usdProfitComplete = ledgerProjects.all { it.usdProfitComplete }
    val interactionDays = interactions.mapNotNull { interaction ->
        runCatching { Instant.ofEpochMilli(interaction.timestamp).atZone(zoneId).toLocalDate() }.getOrNull()
    }.distinct().size
    return UserStatsDto(
        tracked_projects = projects.size,
        completed_projects = projects.count { it.status.equals("completed", ignoreCase = true) },
        total_actual_income = reward,
        interaction_days = interactionDays,
        ledger_project_count = ledgerProjects.size,
        ledger_total_gas_cost = gas,
        ledger_total_actual_reward = reward,
        ledger_net_profit = net,
        profitable_projects = profitable,
        evaluated_projects = evaluated.size,
        win_rate = if (evaluated.isEmpty()) 0.0 else profitable * 100.0 / evaluated.size,
        ledger_interaction_days = interactionDays,
        total_interactions = projects.sumOf { it.interactionCount },
        usd_profit_complete = usdProfitComplete
    )
}
