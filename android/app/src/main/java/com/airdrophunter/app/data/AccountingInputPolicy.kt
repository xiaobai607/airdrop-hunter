package com.airdrophunter.app.data

/** Returns only an explicitly entered, finite, non-negative USD amount. */
fun parseConfirmedUsdAmount(input: String): Double? {
    if (input.isBlank()) return null
    val value = input.trim().toDoubleOrNull() ?: return null
    return value.takeIf { it.isFinite() && it >= 0.0 }
}
