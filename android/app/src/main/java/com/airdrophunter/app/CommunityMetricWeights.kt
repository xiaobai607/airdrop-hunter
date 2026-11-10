package com.airdrophunter.app

/** Exact proportional weights for comparable USD-valued community metrics. */
internal fun communityMetricWeights(gas: Double, reward: Double): Pair<Float, Float> {
    val safeGas = gas.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    val safeReward = reward.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    val total = safeGas + safeReward
    if (!total.isFinite() || total <= 0.0) return 0f to 0f
    return (safeGas / total).toFloat() to (safeReward / total).toFloat()
}
