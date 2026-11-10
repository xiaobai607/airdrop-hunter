package com.airdrophunter.app.data

import java.math.BigDecimal
import java.math.RoundingMode

enum class ManualRewardKind {
    USD, TOKEN, MANUAL_USD_VALUATION, POINTS, UNISSUED, UNKNOWN
}

enum class ManualRewardAllocationMode {
    PER_WALLET, EVEN_SPLIT_TOTAL
}

data class ManualRewardInput(
    val kind: ManualRewardKind,
    val amount: BigDecimal? = null,
    val unit: String? = null,
    /** Explicit USD valuation only; null means the reward has no confirmed USD value. */
    val usdValuation: BigDecimal? = null,
    val unitUsdPrice: BigDecimal? = null,
    val quoteSource: String? = null,
    val quoteTimestamp: Long? = null
)

data class ManualWalletAccountingInput(
    val walletId: String,
    val walletAddress: String,
    val network: String,
    val gasCostUsd: BigDecimal,
    val txHash: String = "",
    /** Used only in PER_WALLET mode. */
    val reward: ManualRewardInput? = null,
    /** Explicit consent applies only to this wallet row. */
    val publicContributionOptIn: Boolean = false
)

data class AllocatedWalletAccountingEntry(
    val walletId: String,
    val walletAddress: String,
    val network: String,
    val gasCostUsd: BigDecimal,
    val txHash: String,
    val reward: ManualRewardInput,
    /** Null is intentionally different from a confirmed zero-dollar reward. */
    val actualRewardUsd: BigDecimal?,
    /** Null means net P&L is incomplete and must not be presented as a final result. */
    val netProfitUsd: BigDecimal?,
    val publicContributionOptIn: Boolean
)

data class ManualAccountingBatch(
    val entries: List<AllocatedWalletAccountingEntry>
) {
    val hasCompleteUsdProfit: Boolean get() = entries.all { it.netProfitUsd != null }
    val hasPublicContributionOptIn: Boolean get() = entries.any { it.publicContributionOptIn }
}

/**
 * Validates and allocates manual multi-wallet accounting input before persistence.
 * This is a pure policy layer: callers must persist the returned entries in one
 * database transaction and must not convert null valuations into zero.
 */
object ManualProjectAccountingPolicy {
    private val maxRows = 100
    private val maxAmount = BigDecimal("1000000000000")

    fun prepare(
        wallets: List<ManualWalletAccountingInput>,
        allocationMode: ManualRewardAllocationMode,
        totalReward: ManualRewardInput? = null
    ): ManualAccountingBatch {
        require(wallets.isNotEmpty() && wallets.size <= maxRows) { "请选择1至100个钱包" }
        require(wallets.map { it.walletId }.distinct().size == wallets.size) { "钱包不能重复" }
        val hashes = wallets.filter { it.txHash.isNotBlank() }
            .map { "${it.network.trim().lowercase()}:${it.txHash.trim().lowercase()}" }
        require(hashes.distinct().size == hashes.size) { "同一批次不能重复使用 Tx Hash" }
        wallets.forEach { wallet ->
            require(wallet.walletId.isNotBlank() && wallet.walletId.length <= 80) { "钱包标识无效" }
            require(wallet.walletAddress.isNotBlank() && wallet.walletAddress.length <= 160) { "钱包地址无效" }
            require(wallet.network.isNotBlank() && wallet.network.length <= 40) { "网络标识无效" }
            require(wallet.gasCostUsd.isValidAmount()) { "Gas 金额必须是有限的非负数" }
            if (wallet.publicContributionOptIn) {
                require(isSupportedHash(wallet.network, wallet.txHash)) {
                    "该钱包的 Tx Hash 格式无效，或当前网络暂不支持匿名贡献核验"
                }
            } else {
                require(wallet.txHash.isBlank() || wallet.txHash.length <= 180) { "Tx Hash 长度无效" }
            }
        }

        val rewards = when (allocationMode) {
            ManualRewardAllocationMode.PER_WALLET -> {
                require(totalReward == null) { "逐钱包分配不能同时填写总奖励" }
                wallets.map { wallet ->
                    requireNotNull(wallet.reward) { "请填写每个钱包的奖励类型和实际结果" }
                    validateReward(wallet.reward)
                }
            }
            ManualRewardAllocationMode.EVEN_SPLIT_TOTAL -> {
                require(wallets.all { it.reward == null }) { "总奖励均分模式不能混填逐钱包奖励" }
                val total = validateReward(requireNotNull(totalReward) { "请填写本次活动总奖励" })
                splitReward(total, wallets.size)
            }
        }

        val entries = wallets.mapIndexed { index, wallet ->
            val reward = rewards[index]
            val rewardUsd = reward.usdValuation
            AllocatedWalletAccountingEntry(
                walletId = wallet.walletId,
                walletAddress = wallet.walletAddress,
                network = wallet.network,
                gasCostUsd = wallet.gasCostUsd,
                txHash = wallet.txHash.trim(),
                reward = reward,
                actualRewardUsd = rewardUsd,
                netProfitUsd = rewardUsd?.subtract(wallet.gasCostUsd),
                publicContributionOptIn = wallet.publicContributionOptIn
            )
        }
        return ManualAccountingBatch(entries)
    }

    fun isTransactionHashFormatValid(network: String, hash: String): Boolean = isSupportedHash(network, hash)

    /** Community directory projects contribute by default only with a verifiable EVM hash and confirmed USD reward. */
    fun isEligibleForDefaultCommunityContribution(
        projectOfficialId: String?,
        rewardKind: ManualRewardKind,
        network: String,
        txHash: String
    ): Boolean =
        !projectOfficialId.isNullOrBlank() &&
            rewardKind in setOf(ManualRewardKind.USD, ManualRewardKind.MANUAL_USD_VALUATION) &&
            isSupportedHash(network, txHash)

    fun isTransactionVerificationNetworkSupported(network: String): Boolean =
        network.trim().lowercase() in setOf("ethereum", "eth", "erc20", "bsc", "polygon", "arbitrum", "optimism", "base", "avalanche")

    private fun isSupportedHash(network: String, hash: String): Boolean {
        val normalized = network.trim().lowercase()
        return isTransactionVerificationNetworkSupported(normalized) && EVM_HASH.matches(hash.trim())
    }

    private val EVM_HASH = Regex("^0x[a-fA-F0-9]{64}$")

    private fun validateReward(reward: ManualRewardInput): ManualRewardInput {
        val amount = reward.amount
        val valuation = reward.usdValuation
        when (reward.kind) {
            ManualRewardKind.USD -> {
                require(amount.isValidAmount()) { "USD 奖励金额无效" }
                require(valuation == null || valuation.compareTo(amount!!) == 0) { "USD 奖励不能重复指定估值" }
                require(reward.unitUsdPrice == null && reward.quoteSource == null && reward.quoteTimestamp == null) {
                    "USD 直接录入不需要代币报价信息"
                }
                return reward.copy(unit = "USD", usdValuation = amount)
            }
            ManualRewardKind.TOKEN -> {
                require(amount.isValidAmount() && !reward.unit.isNullOrBlank()) { "代币奖励需要数量和币种" }
                require(valuation == null || valuation.isValidAmount()) { "代币 USD 估值无效" }
                if (valuation != null) {
                    require(reward.unitUsdPrice.isValidAmount() && reward.unitUsdPrice!!.signum() > 0 &&
                        !reward.quoteSource.isNullOrBlank() && (reward.quoteTimestamp ?: 0L) > 0L) {
                        "代币估值必须保存单价、报价来源和时间"
                    }
                } else {
                    require(reward.unitUsdPrice == null && reward.quoteSource == null && reward.quoteTimestamp == null) {
                        "未估值代币不能附带不完整报价信息"
                    }
                }
            }
            ManualRewardKind.MANUAL_USD_VALUATION -> {
                require(amount.isValidAmount()) { "手动 USD 估值必须明确填写" }
                require(valuation == null) { "手动估值请直接填写 USD 金额" }
                require(reward.unitUsdPrice == null && reward.quoteSource == null && reward.quoteTimestamp == null) {
                    "手动估值不应携带行情报价信息"
                }
                return reward.copy(unit = "USD", usdValuation = amount)
            }
            ManualRewardKind.POINTS -> {
                require(amount.isValidAmount() && !reward.unit.isNullOrBlank()) { "积分奖励需要数量和积分名称" }
                require(valuation == null) { "积分不能直接作为 USD 收益" }
                require(reward.unitUsdPrice == null && reward.quoteSource == null && reward.quoteTimestamp == null)
            }
            ManualRewardKind.UNISSUED -> {
                require(valuation == null) { "未发放奖励不能计入实际 USD 收益" }
                require(amount == null || amount.isValidAmount()) { "未发放奖励数量无效" }
                require(reward.unitUsdPrice == null && reward.quoteSource == null && reward.quoteTimestamp == null)
            }
            ManualRewardKind.UNKNOWN -> {
                require(amount == null && valuation == null) { "未知奖励不能伪造数量或 USD 估值" }
                require(reward.unitUsdPrice == null && reward.quoteSource == null && reward.quoteTimestamp == null)
            }
        }
        return reward.copy(unit = reward.unit?.trim()?.takeIf { it.isNotEmpty() })
    }

    private fun splitReward(total: ManualRewardInput, count: Int): List<ManualRewardInput> {
        if (total.kind == ManualRewardKind.UNKNOWN) {
            return List(count) { total }
        }
        val amount = total.amount ?: return List(count) { total }
        val valuation = total.usdValuation
        val divisor = BigDecimal(count)
        val splitAmount = amount.divide(divisor, 18, RoundingMode.DOWN)
        val splitValuation = valuation?.divide(divisor, 18, RoundingMode.DOWN)
        return List(count) { index ->
            val last = index == count - 1
            total.copy(
                amount = if (last) amount.subtract(splitAmount.multiply(BigDecimal(count - 1))) else splitAmount,
                usdValuation = if (last && valuation != null) {
                    valuation.subtract(splitValuation!!.multiply(BigDecimal(count - 1)))
                } else splitValuation
            )
        }
    }

    private fun BigDecimal?.isValidAmount(): Boolean =
        this != null && signum() >= 0 && compareTo(maxAmount) <= 0 && precision() <= 40
}
