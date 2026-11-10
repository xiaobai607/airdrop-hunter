package com.airdrophunter.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletAddressValidatorTest {
    @Test fun acceptsValidAddressesForAllSupportedEcosystems() {
        assertTrue(WalletAddressValidator.isValidForChain("EVM", "0x1111111111111111111111111111111111111111"))
        assertTrue(WalletAddressValidator.isValidForChain("TRON", "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"))
        assertTrue(WalletAddressValidator.isValidForChain("SOL", "11111111111111111111111111111111"))
        assertTrue(WalletAddressValidator.isValidForChain("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
    }
    @Test fun rejectsWrongChainAndInvalidChecksums() {
        val solanaSystemProgram = "11111111111111111111111111111111"
        assertFalse(WalletAddressValidator.isValidForChain("BTC", solanaSystemProgram))
        assertFalse(WalletAddressValidator.isValidForChain("SOL", "0x1111111111111111111111111111111111111111"))
        assertFalse(WalletAddressValidator.isValidForChain("TRON", "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwa"))
        assertFalse(WalletAddressValidator.isValidForChain("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNb"))
        assertFalse(WalletAddressValidator.isValidForChain("SOL", "1111111111111111111111111111111"))
    }
    @Test fun explicitStoredChainWinsOverOverlappingBase58Shape() {
        assertTrue(WalletEcosystem.fromAddress("11111111111111111111111111111111", "Solana") == WalletEcosystem.SOL)
        assertTrue(WalletEcosystem.fromAddress("11111111111111111111111111111111", "Bitcoin") == WalletEcosystem.BTC)
    }
}
