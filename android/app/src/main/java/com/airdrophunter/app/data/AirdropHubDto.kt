package com.airdrophunter.app.data

data class AirdropHubDto(
    val id: String,
    val name: String,
    val chain: String,
    val description: String,
    val status: String,
    val link: String,
    val published_at: String? = null,
    val data_source: String = "",
    val trust_level: String = "TRUST_LEVEL_0",
    val publication_status: String = "OBSERVATION"
)

data class AirdropHubDetailDto(
    val title: String,
    val content_html: String,
    val release_date: Long? = null,
    val catalog_name: String = "",
    val original_url: String
)

data class AirdropHubSyncStatusDto(
    val last_attempt_at: String? = null,
    val last_success_at: String? = null,
    val imported_count: Int = 0,
    val source: String = "",
    val error: String? = null
)

data class AirdropHubRefreshResponse(
    val ok: Boolean,
    val imported_count: Int,
    val status: AirdropHubSyncStatusDto
)
