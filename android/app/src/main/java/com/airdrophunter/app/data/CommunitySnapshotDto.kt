package com.airdrophunter.app.data

data class CommunitySnapshotManifestDto(
    val schema_version: Int = 1,
    val snapshots: Map<String, CommunitySnapshotReferenceDto> = emptyMap()
)

data class CommunitySnapshotReferenceDto(
    val key: String = "",
    val url: String? = null,
    val version: String = "",
    val update_time: String = ""
)

data class CommunitySnapshotDto(
    val schema_version: Int = 1,
    val window: String,
    val generated_at: String,
    val update_time: String,
    val window_start: String? = null,
    val window_end: String,
    val metric_currency: String = "USD",
    val usd_values_are_valuations: Boolean = true,
    val data_quality: String = "",
    val summary: CommunitySnapshotSummaryDto? = null,
    val projects: List<CommunitySnapshotProjectDto> = emptyList()
)

data class CommunitySnapshotSummaryDto(
    val gas_total_usd: Double = 0.0,
    val gas_by_chain: List<CommunityChainGasDto> = emptyList(),
    val reward_total_usd: Double = 0.0,
    val gas_range_distribution: List<CommunityRangeDistributionDto> = emptyList(),
    val reward_range_distribution: List<CommunityRangeDistributionDto> = emptyList(),
    val cumulative_contributors: Long = 0,
    val active_30d_contributors: Long = 0,
    val contributor_count_basis: String = ""
)

data class CommunityChainGasDto(
    val chain: String = "unknown",
    val gas_usd: Double = 0.0
)

data class CommunityRangeDistributionDto(
    val range_label: String = "",
    val count: Long = 0,
    val percentage: Double = 0.0
)

data class CommunitySnapshotProjectDto(
    val project_official_id: String,
    val name: String,
    val chain: String = "Unknown",
    val logo: String = "",
    val description: String = "",
    val website_url: String = "",
    val created_at: String = "",
    val tags: List<String> = emptyList(),
    val gas_cost_usd_valuation: Double = 0.0,
    val reward_usd_valuation: Double = 0.0,
    val actual_reward_usd_valuation: Double = 0.0,
    val estimated_reward_usd_valuation: Double = 0.0,
    val gas_by_verification_tier: Map<String, Double> = emptyMap(),
    val average_rating: Double? = null,
    val rating_count: Long = 0,
    val rating_distribution: Map<String, Long> = emptyMap(),
    val gas_by_token: List<CommunityTokenAggregateDto> = emptyList(),
    val reward_by_token: List<CommunityTokenAggregateDto> = emptyList()
)
