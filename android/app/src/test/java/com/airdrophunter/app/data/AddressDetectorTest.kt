package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressDetectorTest {
    @Test fun detectsBitcoinLegacyAndBech32Addresses() {
        assertEquals(DetectedNetwork.BITCOIN, AddressDetector.detect("1BoatSLRHtKNngkdXEeobR76b53LETtpyT"))
        assertEquals(DetectedNetwork.BITCOIN, AddressDetector.detect("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"))
    }

    @Test fun doesNotMisclassifySolanaBase58AddressAsBitcoin() {
        assertEquals(DetectedNetwork.SOL, AddressDetector.detect("11111111111111111111111111111111"))
    }
}
