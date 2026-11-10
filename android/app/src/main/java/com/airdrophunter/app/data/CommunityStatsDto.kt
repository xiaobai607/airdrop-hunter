package com.airdrophunter.app.data

data class CommunityContributionRequest(
    val event_id: String,
    val network: String,
    val wallet_address: String,
    val tx_hash: String,
    val public_contribution_opt_in: Boolean,
    val gas_cost_usd: Double,
    val actual_reward_usd: Double,
    val asset_breakdown: List<LedgerAssetAmount> = emptyList()
)

data class CommunityProjectStatsDto(
    val project_official_id: String,
    val total_gas_cost_usd: Double,
    val total_actual_reward_usd: Double,
    val data_quality: String = "user_confirmed",
    val verification_tier: String? = null,
    val average_rating: Double? = null,
    val rating_count: Long = 0,
    val gas_by_verification_tier: Map<String, Double> = emptyMap(),
    val gas_by_token: List<CommunityTokenAggregateDto> = emptyList(),
    val reward_by_token: List<CommunityTokenAggregateDto> = emptyList()
)
