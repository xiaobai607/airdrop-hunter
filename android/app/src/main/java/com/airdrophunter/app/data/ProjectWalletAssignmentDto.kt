package com.airdrophunter.app.data

data class ProjectWalletAssignmentDto(
    val project_key: String,
    val wallet_ids: List<String> = emptyList()
)
