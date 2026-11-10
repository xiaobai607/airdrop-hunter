package com.airdrophunter.app.data

import java.util.Locale

fun normalizeSearchText(value: String): String =
    value.trim().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

fun fuzzySearchMatch(query: String, vararg candidates: String?): Boolean {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isEmpty()) return false
    return candidates.any { candidate ->
        val normalizedCandidate = normalizeSearchText(candidate.orEmpty())
        if (normalizedCandidate.contains(normalizedQuery)) true
        else {
            var queryIndex = 0
            normalizedCandidate.forEach { character ->
                if (queryIndex < normalizedQuery.length && character == normalizedQuery[queryIndex]) queryIndex++
            }
            queryIndex == normalizedQuery.length
        }
    }
}

fun MarketCoin.matchesSearch(query: String): Boolean = fuzzySearchMatch(
    query, symbol, name, sourceId, contractAddress, network
)

fun CommunityProjectCatalogDto.matchesSearch(query: String): Boolean = fuzzySearchMatch(
    query, name, chain, website_url, project_official_id, tags.joinToString(" ")
)
