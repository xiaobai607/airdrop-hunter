package com.airdrophunter.app.data

import com.google.gson.annotations.SerializedName

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
    val expected_reward_usd: Double,
    val actual_gas_cost_usd: Double,
    val actual_reward_usd: Double,
    val occurred_at: String,
    val extra: Map<String, String> = emptyMap()
)
