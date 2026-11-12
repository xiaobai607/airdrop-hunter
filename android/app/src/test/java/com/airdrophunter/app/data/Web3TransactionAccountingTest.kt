package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class Web3TransactionAccountingTest {

    @Test
    fun parsesHexAndDecimalQuantitiesSafely() {
        assertEquals(BigInteger("255"), WalletTransactionLinkParser.parseQuantity("0xff"))
        assertEquals(BigInteger("255"), WalletTransactionLinkParser.parseQuantity("255"))
        assertEquals(BigInteger.ZERO, WalletTransactionLinkParser.parseQuantity("0x0"))
        assertNull(WalletTransactionLinkParser.parseQuantity("-1"))
        assertNull(WalletTransactionLinkParser.parseQuantity("0xgg"))
    }

    @Test
    fun validatesTransactionHashes() {
        val valid = "0x" + "a".repeat(64)
        assertTrue(Web3TransactionRepository.isValidTxHash(valid))
        assertFalse(Web3TransactionRepository.isValidTxHash("0x1234"))
        assertFalse(Web3TransactionRepository.isValidTxHash("0x" + "z".repeat(64)))
    }

    @Test
    fun arbitrumL1FeeUsesGasUsedForL1InChildGasUnits() {
        val fee = Web3ReceiptFeeParser.l1FeeWei(
            chainId = 42161L,
            l1FeeRaw = null,
            gasUsedForL1Raw = "0x64",
            effectiveGasPrice = BigInteger("200")
        )
        assertEquals(BigInteger("20000"), fee)
    }

    @Test
    fun optimismAndBaseUseReceiptL1FeeWei() {
        assertEquals(
            BigInteger("500"),
            Web3ReceiptFeeParser.l1FeeWei(
                10L, "0x1f4", null, BigInteger.ONE
            )
        )
        assertEquals(
            BigInteger.ZERO,
            Web3ReceiptFeeParser.l1FeeWei(
                8453L, "0x0", null, BigInteger.ONE
            )
        )
    }

    @Test
    fun ethereumDoesNotInventSeparateL1Fee() {
        assertEquals(
            BigInteger.ZERO,
            Web3ReceiptFeeParser.l1FeeWei(
                1L, null, null, BigInteger("100")
            )
        )
    }

    @Test
    fun missingL2FeeExtensionRemainsUnknown() {
        assertNull(
            Web3ReceiptFeeParser.l1FeeWei(
                42161L, null, null, BigInteger("100")
            )
        )
        assertNull(
            Web3ReceiptFeeParser.l1FeeWei(
                10L, null, null, BigInteger("100")
            )
        )
    }
}
