package com.airdrophunter.app.data

data class CommunityProjectCatalogDto(
    val project_official_id: String,
    val name: String,
    val chain: String,
    val logo: String,
    val description: String,
    val tags: List<String>,
    val website_url: String
)
