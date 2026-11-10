package com.airdrophunter.app.data

data class AirdropDto(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val chain: String,
    val status: String,
    val reward_details: String,
    val eligibility: String,
    val official_url: String,
    val source_name: String,
    val source_url: String,
    val source_type: String,
    val verification_status: String,
    val starts_at: String?,
    val ends_at: String?,
    val last_checked_at: String?,
    val created_at: String,
    val updated_at: String
)
