package com.airdrophunter.app.data

data class CommunityContributionRequest(
    val event_id: String,
    val network: String,
    val wallet_address: String? = null,
    val tx_hash: String? = null,
    val public_contribution_opt_in: Boolean,
    val gas_cost_usd: Double,
    val actual_reward_usd: Double,
    val estimated_reward_usd: Double? = null,
    val actual_reward_confirmed: Boolean = true,
    val contributor_id: String? = null,
    val asset_breakdown: List<LedgerAssetAmount> = emptyList()
)

data class CommunityProjectStatsDto(
    val project_official_id: String,
    val total_gas_cost_usd: Double,
    val total_actual_reward_usd: Double,
    val total_reward_usd: Double = total_actual_reward_usd,
    val total_estimated_reward_usd: Double = 0.0,
    val data_quality: String = "approximate_actual_and_estimated",
    val verification_tier: String? = null,
    val average_rating: Double? = null,
    val rating_count: Long = 0,
    val gas_by_verification_tier: Map<String, Double> = emptyMap(),
    val gas_by_token: List<CommunityTokenAggregateDto> = emptyList(),
    val reward_by_token: List<CommunityTokenAggregateDto> = emptyList()
)
