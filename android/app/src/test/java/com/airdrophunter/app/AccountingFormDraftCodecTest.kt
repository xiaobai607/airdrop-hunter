package com.airdrophunter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountingFormDraftCodecTest {
    @Test
    fun roundTripPreservesUnconfirmedAccountingInputs() {
        val draft = AccountingFormDraft(
            selectedProjectLocalId = "project-local-1",
            gas = "1.25",
            reward = "0",
            rewardUsdEdited = true,
            gasNative = "0.00042",
            gasSymbol = "ETH",
            gasDecimals = "18",
            gasValuationTimestamp = "2026-11-13T10:00:00Z",
            gasValuationSource = "user_input",
            gasL1Native = "0.0001",
            gasL2Native = "0.0002",
            gasOperatorNative = "0.00012",
            gasL1Usd = 0.2,
            gasL2Usd = 0.4,
            gasOperatorUsd = 0.65,
            gasSplitQuoteTimestamp = "2026-11-13T10:00:00Z",
            gasExecutionLabel = "L2 执行费",
            gasSplitSymbol = "ETH",
            rewardNative = "0",
            rewardSymbol = "USDC",
            rewardDecimals = "6"
        )

        assertEquals(draft, AccountingFormDraftCodec.decode(AccountingFormDraftCodec.encode(draft)))
    }

    @Test
    fun malformedOrOversizedDraftIsIgnored() {
        assertNull(AccountingFormDraftCodec.decode("{not-json"))
        assertNull(AccountingFormDraftCodec.decode("x".repeat(32_769)))
    }
}
