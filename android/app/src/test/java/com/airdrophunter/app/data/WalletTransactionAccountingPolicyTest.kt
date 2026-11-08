package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletTransactionAccountingPolicyTest {
    @Test
    fun prefillOnlyWhenWalletPaidTheCompleteFee() {
        assertEquals("0.042", WalletTransactionAccountingPolicy.suggestedGasCostUsd(tx("ethereum", "evm_gas_fee_wei", true, "0.042")))
    }

    @Test
    fun inboundFeePaidByAnotherWalletIsNotPrefilled() {
        assertEquals("", WalletTransactionAccountingPolicy.suggestedGasCostUsd(tx("ethereum", "evm_gas_fee_wei", false, "0.042")))
    }

    @Test
    fun sharedBitcoinInputFeeIsNotPrefilled() {
        assertEquals("", WalletTransactionAccountingPolicy.suggestedGasCostUsd(tx("bitcoin", "bitcoin_fee_sat_shared_inputs", false, "0.50")))
    }

    @Test
    fun unprovenTronFeePayerIsNotPrefilled() {
        assertEquals("", WalletTransactionAccountingPolicy.suggestedGasCostUsd(tx("tron", "tron_total_fee_sun", false, "0.28")))
    }

    @Test
    fun unconfirmedTransactionFeeIsNeverPrefilled() {
        val pending = tx("bitcoin", "bitcoin_fee_sat", true, "0.50").copy(status = "pending")
        assertEquals("", WalletTransactionAccountingPolicy.suggestedGasCostUsd(pending))
    }

    @Test
    fun incompleteLayerTwoExecutionFeeIsNotPrefilled() {
        assertEquals("", WalletTransactionAccountingPolicy.suggestedGasCostUsd(tx("base", "evm_l2_execution_fee_wei", true, "0.01")))
    }

    private fun tx(chain: String, feeType: String, paid: Boolean, usd: String) = WalletTransactionDto(
        chain = chain,
        hash = "0x" + "a".repeat(64),
        timestamp = 1_700_000_000L,
        from_address = "sender",
        to_address = "recipient",
        total_fee_base_unit = "1000",
        fee_type = feeType,
        gas_cost_native = "0.00001",
        native_symbol = "ETH",
        gas_cost_usd = usd,
        fee_paid_by_wallet = paid,
        status = "success"
    )
}
