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
    val actual_income: Double = 0.0,
    /** Explicit link to a canonical public community project. Null means private/custom only. */
    val communityOfficialId: String? = null,
    /** Stable local key for ledger references; remoteId maps it to the account API. */
    val remoteId: String? = null,
    /** Authenticated project remains visible locally while server sync is pending. */
    val syncPending: Boolean = false,
    /** Server echo of the stable client key used for idempotent create/recovery. */
    val client_project_id: String? = null,
    /** Derived from an explicit inactive status in the cached official community directory. */
    val communityDelisted: Boolean = false
)

/** Stable local project key. It is deliberately distinct from the optional community ID. */
fun ProjectDto.officialIdentifier(): String = communityOfficialId ?: id

