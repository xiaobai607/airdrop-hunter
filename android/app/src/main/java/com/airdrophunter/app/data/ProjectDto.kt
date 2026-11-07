package com.airdrophunter.app.data

data class ProjectDto(
    val id: String,
    val name: String,
    val description: String,
    val status: String,
    val official_url: String = "",
    val claim_url: String = "",
    val twitter_url: String = "",
    val deadline: String? = null,
    val expected_income: Double = 0.0,
    val gas_cost: Double = 0.0,
    val actual_income: Double = 0.0
)


/** Stable local aggregation key: official URL when available, otherwise the saved project ID. */
fun ProjectDto.officialIdentifier(): String =
    official_url.trim().trimEnd('/').ifBlank { id }
