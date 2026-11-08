package com.airdrophunter.app.data

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetRepositoryTest {
    @Test fun solanaTokenAccountsAreMergedByMintAcrossTokenPrograms() {
        val tokens = mergeSolanaTokenBalances(
            listOf(
                SolanaTokenBalance(
                    mint = "MintLegacy111111111111111111111111111111",
                    rawAmount = "1250000",
                    decimals = 6,
                    tokenProgramName = "SPL Token"
                ),
                SolanaTokenBalance(
                    mint = "MintLegacy111111111111111111111111111111",
                    rawAmount = "250000",
                    decimals = 6,
                    tokenProgramName = "SPL Token"
                ),
                SolanaTokenBalance(
                    mint = "Mint2022Token1111111111111111111111111111",
                    rawAmount = "12345",
                    decimals = 3,
                    tokenProgramName = "SPL Token-2022"
                )
            )
        )

        assertEquals(2, tokens.size)
        assertEquals(
            BigDecimal("1.5"),
            tokens.single { it.name == "SPL Token" }.quantity
        )
        assertEquals(
            BigDecimal("12.345"),
            tokens.single { it.name == "SPL Token-2022" }.quantity
        )
    }

    @Test fun solanaTokenParserRejectsInvalidAmountsDecimalsAndZeroBalances() {
        val tokens = mergeSolanaTokenBalances(
            listOf(
                SolanaTokenBalance("MintZero", "0", 6, "SPL Token"),
                SolanaTokenBalance("MintBad", "not-a-number", 6, "SPL Token-2022"),
                SolanaTokenBalance("MintBadDecimals", "100", 31, "SPL Token"),
                SolanaTokenBalance(" ", "100", 2, "SPL Token")
            )
        )
        assertTrue(tokens.isEmpty())
    }
}
