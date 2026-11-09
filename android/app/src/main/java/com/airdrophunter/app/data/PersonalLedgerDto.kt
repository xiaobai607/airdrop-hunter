package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PersonalLedgerWriteRequest(
    val client_record_id: String,
    val project_id: String?,
    val project_name: String,
    val chain: String,
    val wallet_address: String,
    val tx_hash: String,
    val interaction_method: String,
    val expected_gas_cost_usd: Double,
    val expected_reward_usd: Double?,
    val actual_gas_cost_usd: Double,
    val actual_reward_usd: Double,
    val occurred_at: String,
    val extra: Map<String, String> = emptyMap()
)

data class PersonalLedgerEntryDto(
    val id: String,
    val client_record_id: String,
    val project_id: String?,
    val project_name: String,
    val chain: String,
    val wallet_address: String,
    val tx_hash: String,
    val interaction_method: String,
    val expected_gas_cost_usd: Double,
    val expected_reward_usd: Double?,
    val actual_gas_cost_usd: Double,
    val actual_reward_usd: Double,
    val occurred_at: String,
    val extra: Map<String, String> = emptyMap()
)

data class LedgerAssetAmount(
    val kind: String,
    val amount_native: String,
    val symbol: String,
    val decimals: Int? = null,
    val usd_valuation: Double? = null,
    val valuation_timestamp: String? = null,
    val valuation_source: String? = null
)

object LedgerAssetBreakdownCodec {
    private val listType = object : TypeToken<List<LedgerAssetAmount>>() {}.type
    private val gson = Gson()

    fun decode(json: String?): List<LedgerAssetAmount> =
        json?.takeIf { it.isNotBlank() }?.let {
            runCatching { gson.fromJson<List<LedgerAssetAmount>>(it, listType) }.getOrNull()
        }.orEmpty()

    fun encode(assets: List<LedgerAssetAmount>): String? =
        assets.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) }
}

data class CommunityTokenAggregateDto(
    val symbol: String,
    val decimals: Int? = null,
    val amount_native: String,
    val usd_valuation: Double? = null,
    val valuation_timestamp: String? = null,
    val valuation_source: String? = null
)

fun formatLedgerAssetBreakdown(json: String?, kind: String? = null): String =
    LedgerAssetBreakdownCodec.decode(json)
        .filter { kind == null || it.kind == kind }
        .joinToString(" · ") { asset ->
            val usd = asset.usd_valuation?.let { "（USD估值 $%.2f）".format(java.util.Locale.US, it) }.orEmpty()
            "${asset.amount_native} ${asset.symbol}$usd"
        }

fun formatCommunityTokenBreakdown(assets: List<CommunityTokenAggregateDto>): String =
    assets.joinToString(" · ") { asset ->
        val usd = asset.usd_valuation?.let { "（USD估值 $%.2f）".format(java.util.Locale.US, it) }.orEmpty()
        "${asset.amount_native} ${asset.symbol}$usd"
    }
