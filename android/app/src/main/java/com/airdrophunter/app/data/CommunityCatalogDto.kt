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
    val sort_order: Int = 100
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
