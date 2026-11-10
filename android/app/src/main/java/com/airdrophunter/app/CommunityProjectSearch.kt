package com.airdrophunter.app

internal fun matchesCommunityProjectQuery(
    name: String,
    chain: String,
    tags: List<String>,
    query: String
): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) return true
    return name.contains(normalized, ignoreCase = true) ||
        chain.contains(normalized, ignoreCase = true) ||
        tags.any { it.contains(normalized, ignoreCase = true) }
}
