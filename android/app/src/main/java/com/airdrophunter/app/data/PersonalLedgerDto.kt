package com.airdrophunter.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.math.BigDecimal

data class PersonalLedgerWriteRequest(
    val client_record_id: String,
    val project_id: String?,
    val project_name: String,
    val chain: String,
    val wallet_address: String,
    val tx_hash: String,
    val interaction_method: String,
    val expected_gas_cost_usd: Double?,
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
    val expected_gas_cost_usd: Double?,
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


data class LedgerAssetPeriodTotal(
    val network: String,
    val kind: String,
    val symbol: String,
    val decimals: Int?,
    val amountNative: BigDecimal,
    val usdValuation: BigDecimal?
)

data class LedgerEstimatePeriodTotal(
    val expectedGasCostUsd: BigDecimal?,
    val expectedRewardUsd: BigDecimal?
)

/** Unknown estimates remain null; totals are shown only when every row is known. */
fun aggregateLedgerEstimatePeriodTotals(
    interactions: List<UserInteractionEntity>
): LedgerEstimatePeriodTotal {
    fun sumIfComplete(values: List<Double?>): BigDecimal? {
        if (values.isEmpty() || values.any { it == null || !it.isFinite() || it < 0.0 }) return null
        return values.filterNotNull().fold(BigDecimal.ZERO) { total, value -> total.add(BigDecimal.valueOf(value)) }
    }
    return LedgerEstimatePeriodTotal(
        expectedGasCostUsd = sumIfComplete(interactions.map { it.expectedGasCostUsd }),
        expectedRewardUsd = sumIfComplete(interactions.map { it.expectedRewardUsd })
    )
}

/** Aggregate native units only within the same network, currency, precision and kind. */
fun aggregateLedgerAssetPeriodTotals(
    interactions: List<UserInteractionEntity>,
    kind: String
): List<LedgerAssetPeriodTotal> {
    data class Key(val network: String, val symbol: String, val decimals: Int?)
    class Accumulator {
        var amount = BigDecimal.ZERO
        var usd = BigDecimal.ZERO
        var hasCompleteUsd = true
    }

    val totals = linkedMapOf<Key, Accumulator>()
    interactions.forEach { interaction ->
        LedgerAssetBreakdownCodec.decode(interaction.assetBreakdownJson)
            .filter { it.kind.equals(kind, ignoreCase = true) }
            .forEach assetLoop@ { asset ->
                val amount = asset.amount_native.toBigDecimalOrNull()
                    ?.takeIf { it.signum() >= 0 } ?: return@assetLoop
                val symbol = asset.symbol.trim().uppercase(java.util.Locale.US)
                if (symbol.isBlank()) return@assetLoop
                val network = interaction.network.trim().lowercase(java.util.Locale.US).ifBlank { "unknown" }
                val key = Key(network, symbol, asset.decimals)
                val accumulator = totals.getOrPut(key) { Accumulator() }
                accumulator.amount = accumulator.amount.add(amount)
                val valuation = asset.usd_valuation?.takeIf { it.isFinite() && it >= 0.0 }
                if (valuation == null) accumulator.hasCompleteUsd = false
                else accumulator.usd = accumulator.usd.add(BigDecimal.valueOf(valuation))
            }
    }

    return totals.map { (key, value) ->
        LedgerAssetPeriodTotal(
            network = key.network,
            kind = kind.lowercase(java.util.Locale.US),
            symbol = key.symbol,
            decimals = key.decimals,
            amountNative = value.amount,
            usdValuation = value.usd.takeIf { value.hasCompleteUsd }
        )
    }.sortedWith(compareBy<LedgerAssetPeriodTotal> { it.network }.thenBy { it.symbol }.thenBy { it.decimals ?: -1 })
}

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
