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
data class TaskDto(val id: String, val project_id: String, val task_name: String, val is_completed: Boolean, val step_order: Int = 1)
data class TaskCreateRequest(val task_name: String, val step_order: Int? = null)
data class TaskUpdateRequest(
    val is_completed: Boolean? = null,
    val task_name: String? = null
)
data class WalletSyncResponse(val wallet_id: String, val public_key: String, val chain: String, val daily_gas_usd: Double, val recent_airdrop_income_usd: Double, val status: String, val notice: String)
data class StatsDto(
    val currency: String,
    val project_count: Int,
    val total_actual_income: Double,
    val total_expected_income: Double,
    val total_gas_cost: Double,
    val net_realized_income: Double,
    val wallet_count: Int = 0,
    val project_gas_cost: Double = 0.0,
    val wallet_daily_gas: Double = 0.0,
    val wallet_airdrop_income: Double = 0.0
)
data class UserStatsDto(
    val tracked_projects: Int = 0,
    val completed_projects: Int = 0,
    val total_actual_income: Double = 0.0,
    val interaction_days: Int = 0,
    val ledger_project_count: Int = 0,
    val ledger_total_gas_cost: Double = 0.0,
    val ledger_total_actual_reward: Double = 0.0,
    val ledger_net_profit: Double = 0.0,
    val profitable_projects: Int = 0,
    val evaluated_projects: Int = 0,
    val win_rate: Double = 0.0,
    val ledger_interaction_days: Int = 0,
    val total_interactions: Int = 0
)
