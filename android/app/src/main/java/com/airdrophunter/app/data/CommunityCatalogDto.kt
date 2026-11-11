package com.airdrophunter.app.data

data class CommunityProjectCatalogDto(
    val project_official_id: String,
    val name: String,
    val chain: String,
    val logo: String,
    val description: String,
    val tags: List<String>,
    val website_url: String,
    val created_at: String = "",
    val is_active: Boolean = true,
    val sort_order: Int = 100,
    val supported_chains: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val participation_requirements: List<String> = emptyList(),
    val funding_requirement: String = "unknown",
    val campaign_stage: String = "unknown",
    val reward_types: List<String> = emptyList(),
    val risk_flags: List<String> = emptyList(),
    val effort_level: String = "unknown",
    val criteria_source_url: String = "",
    val metadata_verified_at: String? = null,
    val metadata_note: String = ""
)

data class CommunityProjectSubmissionRequest(
    val name: String,
    val url: String,
    val logo: String = ""
)

data class CommunityProjectSubmissionResponse(
    val status: String,
    val message: String,
    val existing_project: CommunityProjectCatalogDto? = null
)
