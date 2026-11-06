package com.airdrophunter.app.data

data class MarketResponse(val provider: String, val currency: String, val prices: List<MarketPrice>)
data class MarketPrice(
    val coinpaprika_id: String,
    val symbol: String,
    val name: String,
    val price_usd: Double?,
    val change_24h: Double?,
    val market_cap_usd: Double?,
    val rank: Int?,
    val unavailable: Boolean = false
)
data class CoinDto(val id: String, val coinpaprika_id: String, val symbol: String, val name: String)
data class CoinCreateRequest(val coinpaprika_id: String, val symbol: String, val name: String)
data class TaskDto(val id: String, val project_id: String, val task_name: String, val is_completed: Boolean)
data class TaskCreateRequest(val task_name: String)
data class TaskUpdateRequest(val is_completed: Boolean)
data class WalletCheckResponse(val address: String, val eligible: Boolean, val estimated_reward_usd: Double, val status: String, val notice: String)
data class StatsDto(
    val currency: String,
    val project_count: Int,
    val total_actual_income: Double,
    val total_expected_income: Double,
    val total_gas_cost: Double,
    val net_realized_income: Double
)
