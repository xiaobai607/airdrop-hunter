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
    val projects: List<CommunitySnapshotProjectDto> = emptyList()
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
    val actual_reward_usd_valuation: Double = 0.0,
    val net_result_usd_valuation: Double = 0.0,
    val average_rating: Double? = null,
    val rating_count: Long = 0,
    val rating_distribution: Map<String, Long> = emptyMap()
)
