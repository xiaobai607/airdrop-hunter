package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        val zero = parseConfirmedUsdAmount("0")
        val amount = parseConfirmedUsdAmount(" 12.34 ")
        assertNotNull(zero)
        assertNotNull(amount)
        assertEquals(0.0, zero!!, 0.0)
        assertEquals(12.34, amount!!, 0.0)
    }
}
