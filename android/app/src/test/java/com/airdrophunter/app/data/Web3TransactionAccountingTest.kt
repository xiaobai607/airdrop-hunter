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
        assertEquals(BigInteger("10000000000000000"), WalletTransactionLinkParser.parseQuantity("1e16"))
        assertNull(WalletTransactionLinkParser.parseQuantity("-1"))
        assertNull(WalletTransactionLinkParser.parseQuantity("0xgg"))
    }

    @Test
    fun rejectsValuesOutsideUint256Range() {
        val max = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)
        assertEquals(max, WalletTransactionLinkParser.parseQuantity("0x" + max.toString(16)))
        assertNull(WalletTransactionLinkParser.parseQuantity("0x" + BigInteger.ONE.shiftLeft(256).toString(16)))
    }

    @Test
    fun mapsSupportedChainIdsToNativeGasAssets() {
        assertEquals("ETH", Web3TransactionRepository.nativeSymbolForChain(1L))
        assertEquals("ETH", Web3TransactionRepository.nativeSymbolForChain(10L))
        assertEquals("ETH", Web3TransactionRepository.nativeSymbolForChain(42161L))
        assertEquals("ETH", Web3TransactionRepository.nativeSymbolForChain(8453L))
        assertEquals("BNB", Web3TransactionRepository.nativeSymbolForChain(56L))
        assertEquals("POL", Web3TransactionRepository.nativeSymbolForChain(137L))
        assertEquals("UNKNOWN", Web3TransactionRepository.nativeSymbolForChain(999999L))
    }

    @Test
    fun validatesTransactionHashes() {
        val valid = "0x" + "a".repeat(64)
        assertTrue(Web3TransactionRepository.isValidTxHash(valid))
        assertFalse(Web3TransactionRepository.isValidTxHash("0x1234"))
        assertFalse(Web3TransactionRepository.isValidTxHash("0x" + "z".repeat(64)))
    }

    @Test
    fun manualHashVerificationRequiresExactHashAndRequestFields() {
        val hash = "0x" + "a".repeat(64)
        val address = "0x" + "b".repeat(40)
        val recipient = "0x" + "c".repeat(40)
        val calldata = "0x1234"
        val value = BigInteger("100")

        assertTrue(
            Web3ManualTransactionVerifier.matches(
                hash, hash.uppercase(), address, address.uppercase(),
                recipient, recipient.uppercase(), value, value,
                calldata, calldata.uppercase()
            )
        )
        assertFalse(
            Web3ManualTransactionVerifier.matches(
                hash, "0x" + "d".repeat(64), address, address,
                recipient, recipient, value, value, calldata, calldata
            )
        )
    }

    @Test
    fun manualHashVerificationRejectsMissingOrMismatchedCalldata() {
        val hash = "0x" + "a".repeat(64)
        val address = "0x" + "b".repeat(40)
        val recipient = "0x" + "c".repeat(40)
        val calldata = "0x1234"

        assertFalse(
            Web3ManualTransactionVerifier.matches(
                hash, hash, address, address, recipient, recipient,
                BigInteger.ZERO, BigInteger.ZERO, calldata, null
            )
        )
        assertFalse(
            Web3ManualTransactionVerifier.matches(
                hash, hash, address, address, recipient, recipient,
                BigInteger.ZERO, BigInteger.ZERO, calldata, "0xabcd"
            )
        )
    }

    @Test
    fun manualHashVerificationRejectsMissingSenderAndValue() {
        val hash = "0x" + "a".repeat(64)
        val address = "0x" + "b".repeat(40)

        assertFalse(
            Web3ManualTransactionVerifier.matches(
                hash, hash, address, null, null, null,
                null, null, null, null
            )
        )
        assertFalse(
            Web3ManualTransactionVerifier.matches(
                hash, hash, address, address, null, null,
                BigInteger.ONE, null, null, null
            )
        )
    }

    @Test
    fun verifiedReceiptCanEnrichAnExistingManualReviewBill() {
        assertTrue(Web3DraftStatus.shouldRefreshPendingAccountingBill(null))
        assertTrue(Web3DraftStatus.shouldRefreshPendingAccountingBill(""))
        assertFalse(Web3DraftStatus.shouldRefreshPendingAccountingBill("[]"))
    }

    @Test
    fun unresolvedL1FeeCanBeManuallyReviewedAndConfirmed() {
        assertTrue(Web3DraftStatus.canCreateManualReviewBill(Web3DraftStatus.WAITING_L1_FEE))
        assertTrue(Web3DraftStatus.canConfirmAccounting(Web3DraftStatus.WAITING_L1_FEE))
        assertTrue(Web3DraftStatus.canCreateManualReviewBill(Web3DraftStatus.WAITING_OPERATOR_FEE))
        assertTrue(Web3DraftStatus.canCreateManualReviewBill(Web3DraftStatus.RECEIPT_NEEDS_SYNC))
        assertFalse(Web3DraftStatus.canCreateManualReviewBill(Web3DraftStatus.WAITING_RECEIPT))
        assertFalse(Web3DraftStatus.canConfirmAccounting(Web3DraftStatus.WAITING_RECEIPT))
        assertTrue(Web3DraftStatus.canConfirmAccounting(Web3DraftStatus.CONFIRMED))
        assertTrue(Web3DraftStatus.canConfirmAccounting(Web3DraftStatus.CONFIRMED_FAILED))
    }

    @Test
    fun walletHashCanOnlyAttachToRecoverableDraftStates() {
        assertTrue(Web3DraftStatus.canAttachHash(Web3DraftStatus.PENDING_WALLET))
        assertTrue(Web3DraftStatus.canAttachHash(Web3DraftStatus.WAITING_HASH))
        assertTrue(Web3DraftStatus.canAttachHash(Web3DraftStatus.TIMED_OUT_NEEDS_SYNC))
        assertFalse(Web3DraftStatus.canAttachHash(Web3DraftStatus.CANCELLED))
        assertFalse(Web3DraftStatus.canAttachHash(Web3DraftStatus.WALLET_UNAVAILABLE))
        assertFalse(Web3DraftStatus.canAttachHash(Web3DraftStatus.WAITING_RECEIPT))
        assertFalse(Web3DraftStatus.canAttachHash(Web3DraftStatus.ACCOUNTED))
    }

    @Test
    fun receiptIdentityMustMatchSavedTransactionHash() {
        val hash = "0x" + "a".repeat(64)
        assertTrue(Web3ReceiptIdentityVerifier.matches(hash, hash.uppercase()))
        assertFalse(Web3ReceiptIdentityVerifier.matches(hash, "0x" + "b".repeat(64)))
        assertFalse(Web3ReceiptIdentityVerifier.matches(hash, null))
        assertFalse(Web3ReceiptIdentityVerifier.matches(null, hash))
        assertFalse(Web3ReceiptIdentityVerifier.matches("0x1234", hash))
        assertFalse(Web3ReceiptIdentityVerifier.matches(hash, "0x1234"))
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
    fun arbitrumDoesNotGuessFromNonStandardL1FeeWhenCanonicalFieldIsMissing() {
        assertNull(
            Web3ReceiptFeeParser.l1FeeWei(
                42161L, "0x384", null, BigInteger("200")
            )
        )
    }

    @Test
    fun opStackDepositHasNoL1DataFeeEvenWhenReceiptOmitsL1FeeField() {
        assertEquals(
            BigInteger.ZERO,
            Web3ReceiptFeeParser.l1FeeWei(
                chainId = 10L,
                l1FeeRaw = null,
                gasUsedForL1Raw = null,
                effectiveGasPrice = BigInteger.ZERO,
                transactionTypeRaw = "0x7e"
            )
        )
        assertEquals(
            BigInteger.ZERO,
            Web3ReceiptFeeParser.l1FeeWei(
                chainId = 8453L,
                l1FeeRaw = null,
                gasUsedForL1Raw = null,
                effectiveGasPrice = BigInteger.ZERO,
                transactionTypeRaw = "0x7E"
            )
        )
        assertNull(
            Web3ReceiptFeeParser.l1FeeWei(
                chainId = 10L,
                l1FeeRaw = null,
                gasUsedForL1Raw = null,
                effectiveGasPrice = BigInteger.ZERO,
                transactionTypeRaw = "0x2"
            )
        )
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
    fun arbitrumReceiptGasUsedAlreadyIncludesL1Component() {
        val fees = Web3ReceiptFeeCalculator.calculate(
            chainId = 42161L,
            gasUsed = BigInteger("23000"),
            effectiveGasPrice = BigInteger("100"),
            l1FeeWei = BigInteger("200000"),
            operatorFeeWei = BigInteger.ZERO
        )!!
        assertEquals(BigInteger("2300000"), fees.totalFeeWei)
        assertEquals(BigInteger("200000"), fees.l1FeeWei)
        assertEquals(BigInteger("2100000"), fees.l2FeeWei)
    }

    @Test
    fun optimismAddsReceiptL1FeeOnTopOfExecutionFee() {
        val fees = Web3ReceiptFeeCalculator.calculate(
            chainId = 10L,
            gasUsed = BigInteger("21000"),
            effectiveGasPrice = BigInteger("100"),
            l1FeeWei = BigInteger("500000"),
            operatorFeeWei = BigInteger("100000")
        )!!
        assertEquals(BigInteger("2700000"), fees.totalFeeWei)
        assertEquals(BigInteger("500000"), fees.l1FeeWei)
        assertEquals(BigInteger("100000"), fees.operatorFeeWei)
        assertEquals(BigInteger("2100000"), fees.l2FeeWei)
    }

    @Test
    fun recognizesOpStackDepositTransactionsForOperatorFeeExemption() {
        assertTrue(Web3ReceiptFeeParser.isOpStackDepositTransaction("0x7e"))
        assertTrue(Web3ReceiptFeeParser.isOpStackDepositTransaction("0x7E"))
        assertFalse(Web3ReceiptFeeParser.isOpStackDepositTransaction("0x2"))
        assertFalse(Web3ReceiptFeeParser.isOpStackDepositTransaction(null))
    }

    @Test
    fun opStackRequiresAndAddsOperatorFeeSeparately() {
        assertNull(
            Web3ReceiptFeeCalculator.calculate(
                10L, BigInteger("21000"), BigInteger("100"),
                BigInteger("500000"), null
            )
        )
        val fees = Web3ReceiptFeeCalculator.calculate(
            8453L, BigInteger("21000"), BigInteger("100"),
            BigInteger("500000"), BigInteger("100000")
        )!!
        assertEquals(BigInteger("2700000"), fees.totalFeeWei)
        assertEquals(BigInteger("2100000"), fees.l2FeeWei)
        assertEquals(BigInteger("500000"), fees.l1FeeWei)
        assertEquals(BigInteger("100000"), fees.operatorFeeWei)
    }

    @Test
    fun arbitrumRejectsL1ComponentLargerThanReceiptGasTotal() {
        assertNull(
            Web3ReceiptFeeCalculator.calculate(
                42161L, BigInteger("100"), BigInteger("10"),
                BigInteger("2000"), BigInteger.ZERO
            )
        )
    }

    @Test
    fun ethereumUsesOnlyExecutionFeeWithoutRollupComponents() {
        val fees = Web3ReceiptFeeCalculator.calculate(
            1L, BigInteger("21000"), BigInteger("100"),
            null, null
        )!!
        assertEquals(BigInteger("2100000"), fees.totalFeeWei)
        assertEquals(BigInteger.ZERO, fees.l1FeeWei)
        assertEquals(BigInteger.ZERO, fees.operatorFeeWei)
        assertEquals(BigInteger("2100000"), fees.l2FeeWei)
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

    @Test
    fun walletResponseRecoveryRequiresExpectedMethodTopicChainAndHash() {
        val hash = "0x" + "a".repeat(64)
        val topic = "session-topic-123"
        assertTrue(
            Web3WalletResponseIdentityVerifier.matches(
                "eth_sendTransaction", topic, 1L, hash
            )
        )
        assertFalse(
            Web3WalletResponseIdentityVerifier.matches(
                "personal_sign", topic, 1L, hash
            )
        )
        assertFalse(
            Web3WalletResponseIdentityVerifier.matches(
                "eth_sendTransaction", null, 1L, hash
            )
        )
        assertFalse(
            Web3WalletResponseIdentityVerifier.matches(
                "eth_sendTransaction", topic, 0L, hash
            )
        )
        assertFalse(
            Web3WalletResponseIdentityVerifier.matches(
                "eth_sendTransaction", topic, 1L, "0x1234"
            )
        )
    }

    @Test
    fun accountedDraftIsTerminalForReceiptWorker() {
        assertTrue(Web3DraftStatus.isReceiptWorkComplete(Web3DraftStatus.ACCOUNTED))
        assertFalse(Web3DraftStatus.isReceiptWorkComplete(Web3DraftStatus.CONFIRMED))
        assertFalse(Web3DraftStatus.isReceiptWorkComplete(Web3DraftStatus.CONFIRMED_FAILED))
        assertFalse(Web3DraftStatus.isReceiptWorkComplete(Web3DraftStatus.WAITING_RECEIPT))
        assertFalse(Web3DraftStatus.isReceiptWorkComplete(Web3DraftStatus.RECEIPT_NEEDS_SYNC))
    }
}
