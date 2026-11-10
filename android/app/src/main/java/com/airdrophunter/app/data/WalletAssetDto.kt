package com.airdrophunter.app.data

data class WalletAssetDto(
    val asset_id: String,
    val contract_address: String? = null,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val raw_quantity: String,
    val formatted_quantity: String,
    val price_usd: String? = null,
    val usd_value: String? = null,
    val is_verified: Boolean,
    val source: String
)

data class WalletAssetsResponseDto(
    val chain: String,
    val network: String,
    val provider: String,
    val fetched_at: Long,
    val coverage_note: String? = null,
    val assets: List<WalletAssetDto>
)
