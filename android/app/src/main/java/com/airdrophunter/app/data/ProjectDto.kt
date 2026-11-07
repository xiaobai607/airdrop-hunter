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
    val communityOfficialId: String? = null
)

/** Stable local project key. It is deliberately distinct from the optional community ID. */
fun ProjectDto.officialIdentifier(): String = communityOfficialId ?: id

data class CommunityStandardProject(
    val officialId: String,
    val name: String,
    val description: String,
    val aliases: List<String> = emptyList()
)

/** Small, explicit allow-list: fuzzy matching can suggest a link but never links automatically. */
object CommunityProjectCatalog {
    val projects = listOf(
        CommunityStandardProject("linea", "Linea", "Consensys zkEVM", listOf("linea network")),
        CommunityStandardProject("scroll", "Scroll", "zkEVM"),
        CommunityStandardProject("zksync", "zkSync", "ZK Rollup", listOf("zk sync")),
        CommunityStandardProject("starknet", "Starknet", "Validity Rollup", listOf("stark net")),
        CommunityStandardProject("base", "Base", "Ethereum L2"),
        CommunityStandardProject("arbitrum", "Arbitrum", "Ethereum L2"),
        CommunityStandardProject("optimism", "Optimism", "Superchain")
    )

    fun findMatch(input: String): CommunityStandardProject? {
        val query = normalize(input)
        if (query.length < 3) return null
        val candidates = projects.flatMap { project ->
            (listOf(project.name) + project.aliases).map { it to project }
        }
        candidates.firstOrNull { normalize(it.first) == query }?.let { return it.second }
        candidates.firstOrNull { query.contains(normalize(it.first)) || normalize(it.first).contains(query) }?.let {
            return it.second
        }
        return candidates
            .map { levenshtein(query, normalize(it.first)) to it.second }
            .filter { (distance, project) -> distance <= maxOf(1, normalize(project.name).length / 4) }
            .minByOrNull { it.first }?.second
    }

    private fun normalize(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }
}
