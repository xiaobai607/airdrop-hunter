package com.airdrophunter.app.data

data class AirdropWriteRequest(
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
    val starts_at: String? = null,
    val ends_at: String? = null
)
