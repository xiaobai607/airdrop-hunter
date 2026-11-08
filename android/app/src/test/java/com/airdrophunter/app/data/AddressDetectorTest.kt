package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressDetectorTest {
    @Test fun detectsAllFourMainnetBitcoinAddressFormats() {
        val vectors = listOf(
            "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa" to BitcoinAddressFormat.LEGACY,
            "3EmUH8Uh9EXE7axgyAeBsCc2vdUdKkDqWK" to BitcoinAddressFormat.P2SH_NESTED_OR_SCRIPT,
            "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh" to BitcoinAddressFormat.NATIVE_SEGWIT,
            "bc1p5d7rjq7g6rdk2yhzks9smlaqtedr4dekq08ge8ztwac72sfr9rusxg3297" to BitcoinAddressFormat.TAPROOT
        )
        vectors.forEach { (address, format) ->
            assertEquals(address, DetectedNetwork.BITCOIN, AddressDetector.detect(address))
            assertEquals(address, format, BitcoinAddressValidator.classify(address))
        }
    }

    @Test fun rejectsInvalidBitcoinChecksumsAndWitnessPrograms() {
        assertNull(BitcoinAddressValidator.classify("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5"))
        assertNull(BitcoinAddressValidator.classify("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNb"))
    }

    @Test fun doesNotMisclassifySolanaBase58AddressAsBitcoin() {
        assertEquals(DetectedNetwork.SOL, AddressDetector.detect("11111111111111111111111111111111"))
    }
}
