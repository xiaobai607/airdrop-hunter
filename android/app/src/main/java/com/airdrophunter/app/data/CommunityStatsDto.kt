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
    val data_quality: String = "user_confirmed"
)
