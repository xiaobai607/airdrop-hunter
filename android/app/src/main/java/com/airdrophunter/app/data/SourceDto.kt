package com.airdrophunter.app.data

data class SourceDto(
    val id: String,
    val name: String,
    val description: String,
    val website_url: String,
    val icon_url: String,
    val category: String,
    val access_mode: String,
    val note: String
)
