package com.airdrophunter.app.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class UserAchievementStatsTest {
    @Test
    fun aggregatesLocalProjectLedgerAndWinRate() {
        val projects = listOf(
            project("linea", "Linea", 5.0, 10.0, 5.0, 2),
            project("scroll", "Scroll", 4.0, 1.0, -3.0, 1),
            project("base", "Base", 2.0, 2.0, 0.0, 1),
            project("joined", "Joined only", 0.0, 0.0, 0.0, 0, "completed")
        )
        val interactions = listOf(
            interaction("1", Instant.parse("2026-11-08T10:00:00Z").toEpochMilli()),
            interaction("2", Instant.parse("2026-11-08T18:00:00Z").toEpochMilli()),
            interaction("3", Instant.parse("2026-11-09T09:00:00Z").toEpochMilli())
        )
        val stats = calculateLocalUserStats(projects, interactions, ZoneId.of("UTC"))
        assertEquals(4, stats.tracked_projects)
        assertEquals(1, stats.completed_projects)
        assertEquals(3, stats.ledger_project_count)
        assertEquals(11.0, stats.ledger_total_gas_cost, 0.0001)
        assertEquals(13.0, stats.ledger_total_actual_reward, 0.0001)
        assertEquals(2.0, stats.ledger_net_profit, 0.0001)
        assertEquals(1, stats.profitable_projects)
        assertEquals(3, stats.evaluated_projects)
        assertEquals(33.3333, stats.win_rate, 0.001)
        assertEquals(2, stats.ledger_interaction_days)
        assertEquals(4, stats.total_interactions)
    }

    @Test
    fun emptyLedgerHasZeroWinRateAndNoDivisionByZero() {
        val stats = calculateLocalUserStats(emptyList(), emptyList(), ZoneId.of("UTC"))
        assertEquals(0.0, stats.win_rate, 0.0)
        assertEquals(0, stats.ledger_project_count)
        assertEquals(0.0, stats.ledger_total_gas_cost, 0.0)
    }

    private fun project(id: String, name: String, gas: Double, reward: Double, net: Double,
                        count: Int, status: String = "tracking") = UserProjectEntity(
        id = "row-$id", accountScope = "guest", projectLocalId = id, projectOfficialId = id,
        projectName = name, status = status, totalGasCostUsd = gas, totalActualRewardUsd = reward,
        netProfitUsd = net, interactionCount = count, lastInteractionAt = 0L, createdAt = 0L, updatedAt = 0L
    )

    private fun interaction(id: String, timestamp: Long) = UserInteractionEntity(
        id = id, accountScope = "guest", projectLocalId = "linea", projectOfficialId = "linea",
        projectName = "Linea", txHash = "tx-$id", walletAddress = "0xabc", network = "ethereum",
        gasCostUsd = 1.0, actualRewardUsd = 0.0, interactionMethod = "test", timestamp = timestamp
    )
}
