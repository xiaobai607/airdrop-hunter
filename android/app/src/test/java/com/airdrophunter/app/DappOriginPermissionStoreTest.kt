package com.airdrophunter.app

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DappOriginPermissionStoreTest {
    @Before fun setUp() { DappOriginPermissionStore.revokeAll() }
    @After fun tearDown() { DappOriginPermissionStore.revokeAll() }

    @Test
    fun grantIsScopedToExactOriginAndSessionStamp() {
        DappOriginPermissionStore.grant("https://example.com", "session-1:0xabc")
        assertTrue(DappOriginPermissionStore.isAuthorized("https://example.com", "session-1:0xabc"))
        assertFalse(DappOriginPermissionStore.isAuthorized("https://other.example", "session-1:0xabc"))
        assertFalse(DappOriginPermissionStore.isAuthorized("https://example.com", "session-2:0xabc"))
    }

    @Test
    fun revokeRemovesOnlySelectedOrigin() {
        DappOriginPermissionStore.grant("https://example.com", "session-1")
        DappOriginPermissionStore.grant("https://other.example", "session-1")
        DappOriginPermissionStore.revoke("https://example.com")
        assertFalse(DappOriginPermissionStore.isAuthorized("https://example.com", "session-1"))
        assertTrue(DappOriginPermissionStore.isAuthorized("https://other.example", "session-1"))
    }

    @Test
    fun revokeAllClearsEveryOrigin() {
        DappOriginPermissionStore.grant("https://example.com", "session-1")
        DappOriginPermissionStore.revokeAll()
        assertTrue(DappOriginPermissionStore.authorizedOrigins.value.isEmpty())
    }
}
