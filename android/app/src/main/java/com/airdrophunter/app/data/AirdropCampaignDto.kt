package com.airdrophunter.app.data

data class AirdropCampaignDto(
    val id: String,
    val name: String,
    val chain: String? = null,
    val description: String = "",
    val official_url: String? = null,
    val campaign_type: String = "unknown",
    val campaign_status: String = "unknown",
    val starts_at: String? = null,
    val ends_at: String? = null,
    val snapshot_at: String? = null,
    val claim_starts_at: String? = null,
    val claim_ends_at: String? = null,
    val retroactive_possible: Boolean? = null,
    val temperature: Int? = null,
    val opportunity_score: Int? = null,
    val is_kyc_free: Boolean? = null,
    val requires_kyc: Boolean? = null,
    val requires_phone: Boolean? = null,
    val difficulty: String? = null,
    val cost_to_farm_usd: Double? = null,
    val token_network: String? = null,
    val token_contract_address: String? = null,
    val trust_status: String = "unverified",
    val dex_pair_count: Int? = null,
    val dex_liquidity_usd: Double? = null,
    val dex_volume_24h_usd: Double? = null,
    val gecko_trending: Boolean? = null,
    val score: Int? = null,
    val scoring_version: String = "v1",
    val tier: String,
    val listing_reason: String = "",
    val last_verified_at: String? = null,
    val updated_at: String
)

data class AirdropCampaignCacheEnvelope(
    val schema_version: Int = 1,
    val campaigns: List<AirdropCampaignDto> = emptyList()
)

data class AirdropCampaignFilters(
    val tier: String? = null,
    val chain: String? = null,
    val status: String? = null,
    val campaignType: String? = null,
    val kyc: String? = null,
    val query: String? = null
) {
    fun cacheKey(): String = listOf(
        "campaigns:v1",
        tier.orEmpty(),
        chain.orEmpty(),
        status.orEmpty(),
        campaignType.orEmpty(),
        kyc.orEmpty(),
        query.orEmpty()
    ).joinToString(":")
}
