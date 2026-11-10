package com.airdrophunter.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectLibraryReminderPolicyTest {
    private val threeDays = 3L * 24L * 60L * 60L * 1000L

    @Test fun reminderAppearsOnlyAfterMoreThanThreeDaysWithoutBills() {
        assertFalse(shouldShowAccountingReminder(1_000L, 0, 1_000L + threeDays))
        assertTrue(shouldShowAccountingReminder(1_000L, 0, 1_001L + threeDays))
    }

    @Test fun anyConfirmedInteractionSuppressesReminder() {
        assertFalse(shouldShowAccountingReminder(1_000L, 1, 1_000L + threeDays + 1L))
    }

    @Test fun futureOrInvalidCreationTimeDoesNotShowReminder() {
        assertFalse(shouldShowAccountingReminder(0L, 0, 100_000_000L))
        assertFalse(shouldShowAccountingReminder(2_000L, 0, 1_000L))
    }
}
