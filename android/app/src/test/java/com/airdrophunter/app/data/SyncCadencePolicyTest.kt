package com.airdrophunter.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCadencePolicyTest {
    private val eightHours = 8L * 60L * 60L * 1000L

    @Test fun staleFallbackOrThrottleMustNotAdvanceSuccessfulSyncCadence() {
        assertFalse(shouldMarkSyncCadenceSuccess(stale = true, errorMessage = "provider unavailable", throttled = false))
        assertFalse(shouldMarkSyncCadenceSuccess(stale = false, errorMessage = null, throttled = true))
        assertFalse(shouldMarkSyncCadenceSuccess(stale = false, errorMessage = "provider unavailable", throttled = false))
        assertTrue(shouldMarkSyncCadenceSuccess(stale = false, errorMessage = null, throttled = false))
    }

    @Test fun firstRunIsDue() {
        assertTrue(isSyncCadenceDue(0L, 100L, eightHours))
    }

    @Test fun taskIsNotDueBeforeItsInterval() {
        assertFalse(isSyncCadenceDue(1_000L, 1_000L + eightHours - 1L, eightHours))
    }

    @Test fun taskBecomesDueAtIntervalBoundary() {
        assertTrue(isSyncCadenceDue(1_000L, 1_000L + eightHours, eightHours))
    }

    @Test fun clockMovingBackwardsDoesNotLockOutSync() {
        assertTrue(isSyncCadenceDue(5_000L, 4_000L, eightHours))
    }
}
