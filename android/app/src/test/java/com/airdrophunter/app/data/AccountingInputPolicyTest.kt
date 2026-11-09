package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountingInputPolicyTest {
    @Test
    fun actualAmountMustBeExplicitAndFinite() {
        assertNull(parseConfirmedUsdAmount(""))
        assertNull(parseConfirmedUsdAmount("   "))
        assertNull(parseConfirmedUsdAmount("not-a-number"))
        assertNull(parseConfirmedUsdAmount("-0.01"))
        assertNull(parseConfirmedUsdAmount("NaN"))
        assertNull(parseConfirmedUsdAmount("Infinity"))
    }

    @Test
    fun explicitZeroIsAValidConfirmedAmount() {
        assertEquals(0.0, parseConfirmedUsdAmount("0"), 0.0)
        assertEquals(12.34, parseConfirmedUsdAmount(" 12.34 "), 0.0)
    }
}
