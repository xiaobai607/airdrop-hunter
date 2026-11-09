package com.airdrophunter.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestAccountingPrivacyPolicyTest {
    @Test
    fun guestConfirmationDoesNotPersistPrivateAccounting() {
        assertFalse(shouldPersistPrivateAccounting("guest"))
    }

    @Test
    fun authenticatedAccountCanPersistPrivateAccounting() {
        assertTrue(shouldPersistPrivateAccounting("user:123"))
    }
}
