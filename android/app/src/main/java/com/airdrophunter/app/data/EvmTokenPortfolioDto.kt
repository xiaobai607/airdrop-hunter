package com.airdrophunter.app.data

data class EvmTokenHoldingDto(val chain: String, val contract_address: String, val name: String, val symbol: String, val quantity: String, val decimals: Int)
data class EvmTokenNetworkResultDto(val chain: String, val status: String, val detail: String? = null)
data class EvmTokenPortfolioDto(val holdings: List<EvmTokenHoldingDto> = emptyList(), val networks: List<EvmTokenNetworkResultDto> = emptyList())
