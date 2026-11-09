package com.airdrophunter.app.data

data class CommunityContributionRequest(
    val event_id: String,
    val gas_cost_usd: Double,
    val actual_reward_usd: Double,
    val interaction_count: Int = 1
)

data class CommunityProjectStatsDto(
    val project_official_id: String,
    val total_gas_cost_usd: Double,
    val total_actual_reward_usd: Double,
    val total_interactions: Long,
    val contribution_count: Long,
    val data_quality: String = "user_confirmed",
    val contributions_24h: Long = 0,
    val interactions_24h: Long = 0,
    val average_rating: Double? = null,
    val rating_count: Long = 0
)
