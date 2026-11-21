package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalLedgerActualSummaryTest {
    @Test
    fun incompleteRewardIsNotCountedAsZeroInNetProfit() {
        val complete = ledgerRow("complete", gas = 1.0, reward = 3.0)
        val incomplete = ledgerRow(
            "incomplete", gas = 2.0, reward = 0.0,
            extra = mapOf("usd_profit_complete" to "false")
        )

        val summary = summarizePersonalLedgerRows(listOf(complete, incomplete))

        assertEquals(2, summary.recordCount)
        assertEquals(3.0, summary.actualGasUsd, 0.000001)
        assertEquals(3.0, summary.valuedActualRewardUsd, 0.000001)
        assertEquals(1, summary.incompleteRewardCount)
        assertNull(summary.completeNetProfitUsd)
    }

    @Test
    fun completeRowsProduceActualNetProfit() {
        val summary = summarizePersonalLedgerRows(
            listOf(ledgerRow("one", gas = 1.25, reward = 4.5), ledgerRow("two", gas = 0.75, reward = 1.0))
        )

        assertEquals(2, summary.recordCount)
        assertEquals(2.0, summary.actualGasUsd, 0.000001)
        assertEquals(5.5, summary.valuedActualRewardUsd, 0.000001)
        assertEquals(3.5, summary.completeNetProfitUsd!!, 0.000001)
    }

    private fun ledgerRow(
        id: String,
        gas: Double,
        reward: Double,
        extra: Map<String, String> = emptyMap()
    ) = PersonalLedgerWriteRequest(
        client_record_id = id,
        project_id = null,
        project_name = id,
        chain = "ethereum",
        wallet_address = "0x123",
        tx_hash = "0x456",
        interaction_method = "manual",
        expected_gas_cost_usd = null,
        expected_reward_usd = null,
        actual_gas_cost_usd = gas,
        actual_reward_usd = reward,
        occurred_at = "2026-11-13T00:00:00Z",
        extra = extra
    )
}
