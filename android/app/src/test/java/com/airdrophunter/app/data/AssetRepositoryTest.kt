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

    @Test fun evmNativeBalancesCoverEveryConfiguredNetworkAndRemainChainSpecific() {
        val balances = EVM_ASSET_NETWORKS.associate { it.id to BigDecimal("1.25") }
        val assets = buildEvmAssetData("0x0000000000000000000000000000000000000001", balances)

        assertEquals(7, assets.tokenList.size)
        assertEquals("EVM 原生币 · 7/7 网络可用（ERC-20 未读取）", assets.ecosystemName)
        assertEquals("ETH", assets.nativeSymbol)
        assertEquals(BigDecimal("1.25"), assets.nativeBalance)
        assertEquals(
            listOf("ETH", "BNB", "POL", "ETH", "ETH", "ETH", "AVAX"),
            assets.tokenList.map { it.symbol }
        )
        assertEquals(
            listOf("native-ethereum", "native-bsc", "native-polygon", "native-arbitrum", "native-optimism", "native-base", "native-avalanche"),
            assets.tokenList.map { it.id }
        )
    }

    @Test fun evmAssetDataReportsPartialRpcAvailabilityWithoutInventingMissingBalances() {
        val assets = buildEvmAssetData(
            "0x0000000000000000000000000000000000000001",
            mapOf("ethereum" to BigDecimal("0.5"), "base" to BigDecimal("2"))
        )
        assertEquals("EVM 原生币 · 2/7 网络可用（ERC-20 未读取）", assets.ecosystemName)
        assertEquals(2, assets.tokenList.size)
        assertTrue(assets.tokenList.none { it.id == "native-bsc" })
    }

    @Test fun evmBalanceParserHandlesZeroAndRejectsMalformedRpcValues() {
        assertEquals(BigDecimal.ZERO, parseEvmNativeBalance("0x0"))
        assertEquals(BigDecimal("1"), parseEvmNativeBalance("0xde0b6b3a7640000"))
        assertTrue(runCatching {
            parseEvmNativeBalance("not-hex")
        }.isFailure)
    }

    @Test fun evmTokenHoldingsMergeByChainAndKeepUsdUnknown() {
        val base = buildEvmAssetData("0x0000000000000000000000000000000000000001", mapOf("ethereum" to BigDecimal("1")))
        val merged = mergeEvmTokenHoldings(base, listOf(EvmTokenHoldingDto("ethereum", "0x0000000000000000000000000000000000000002", "USD Coin", "USDC", "12.5", 6), EvmTokenHoldingDto("base", "0x0000000000000000000000000000000000000002", "USD Coin", "USDC", "3", 6)), listOf(EvmTokenNetworkResultDto("ethereum", "available"), EvmTokenNetworkResultDto("base", "available")))
        assertEquals(3, merged.tokenList.size)
        assertEquals(2, merged.tokenList.count { it.symbol == "USDC" })
        assertEquals(null, merged.totalUsd)
        assertTrue(merged.tokenList.filter { it.symbol == "USDC" }.all { it.usdValue == null })
        assertTrue(merged.ecosystemName.contains("ERC-20 已读取"))
    }

    @Test fun evmTokenPortfolioMarksUnavailableNetworks() {
        val base = buildEvmAssetData("0x0000000000000000000000000000000000000001", mapOf("ethereum" to BigDecimal("1")))
        val merged = mergeEvmTokenHoldings(base, emptyList(), listOf(EvmTokenNetworkResultDto("ethereum", "available"), EvmTokenNetworkResultDto("base", "unavailable")))
        assertTrue(merged.ecosystemName.contains("ERC-20 部分网络不可用"))
        assertEquals(1, merged.tokenList.size)
    }
}
