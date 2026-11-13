package com.airdrophunter.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletUsageIdentityTest {
    @Test
    fun equivalentWebsiteFormsShareIdentity() {
        assertEquals(
            canonicalWalletIdentity("MetaMask", "https://www.metamask.io/"),
            canonicalWalletIdentity("MetaMask", "metamask.io")
        )
    }

    @Test
    fun differentWalletDomainsRemainDistinct() {
        assertEquals(
            "host:phantom.app",
            canonicalWalletIdentity("Phantom", "https://www.phantom.app")
        )
    }

    @Test
    fun missingOrInvalidUrlFallsBackToNormalizedName() {
        assertEquals(
            "name:okxwallet",
            canonicalWalletIdentity("OKX Wallet", "not a valid host")
        )
    }
}
