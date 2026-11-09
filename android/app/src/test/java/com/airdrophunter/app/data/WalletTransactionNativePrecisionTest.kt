package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletTransactionNativePrecisionTest {
    @Test
    fun legacyCachedTransactionsUseKnownChainPrecision() {
        assertEquals(18, WalletTransactionAccountingPolicy.nativeDecimals(transaction("ethereum")))
        assertEquals(18, WalletTransactionAccountingPolicy.nativeDecimals(transaction("base")))
        assertEquals(6, WalletTransactionAccountingPolicy.nativeDecimals(transaction("tron")))
        assertEquals(8, WalletTransactionAccountingPolicy.nativeDecimals(transaction("bitcoin")))
        assertEquals(9, WalletTransactionAccountingPolicy.nativeDecimals(transaction("solana")))
        assertNull(WalletTransactionAccountingPolicy.nativeDecimals(transaction("unknown-chain")))
    }

    @Test
    fun explicitBackendPrecisionTakesPrecedenceOverChainFallback() {
        assertEquals(8, WalletTransactionAccountingPolicy.nativeDecimals(transaction("ethereum", 8)))
        assertNull(WalletTransactionAccountingPolicy.nativeDecimals(transaction("ethereum", 40)))
    }

    private fun transaction(chain: String, decimals: Int? = null) = WalletTransactionDto(
        chain = chain, hash = "0x" + "a".repeat(64), timestamp = 1L,
        from_address = "from", to_address = "to", total_fee_base_unit = "1",
        fee_type = "test", gas_cost_native = "0.1", native_symbol = "TEST",
        native_decimals = decimals, fee_paid_by_wallet = true, status = "success"
    )
}
