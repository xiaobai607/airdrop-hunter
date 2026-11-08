package com.airdrophunter.app.data

data class EvmTokenHoldingDto(val chain: String, val contract_address: String, val name: String, val symbol: String, val quantity: String, val decimals: Int)
data class EvmTokenNetworkResultDto(val chain: String, val status: String, val detail: String? = null)
data class EvmTokenPortfolioDto(val holdings: List<EvmTokenHoldingDto> = emptyList(), val networks: List<EvmTokenNetworkResultDto> = emptyList())

internal fun mergeEvmTokenHoldings(base: UnifiedAssetData, holdings: List<EvmTokenHoldingDto>, networks: List<EvmTokenNetworkResultDto>): UnifiedAssetData {
    val tokens = holdings.mapNotNull { holding ->
        val quantity = holding.quantity.toBigDecimalOrNull() ?: return@mapNotNull null
        if (quantity.signum() <= 0 || holding.contract_address.isBlank()) return@mapNotNull null
        UnifiedToken(id = "erc20-${holding.chain}-${holding.contract_address.lowercase()}", name = holding.name.ifBlank { holding.symbol }, symbol = holding.symbol, quantity = quantity)
    }
    val unavailable = networks.filter { it.status != "available" }
    val status = when {
        networks.isEmpty() -> "ERC-20 查询状态未知"
        unavailable.isNotEmpty() -> "ERC-20 部分网络不可用（${unavailable.size}/${networks.size}）"
        else -> "ERC-20 已读取"
    }
    return base.copy(ecosystemName = base.ecosystemName.substringBefore("（ERC-20") + "（$status）", tokenList = base.tokenList + tokens)
}