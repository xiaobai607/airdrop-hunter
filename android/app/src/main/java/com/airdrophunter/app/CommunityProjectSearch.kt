package com.airdrophunter.app

internal fun matchesCommunityProjectQuery(
    name: String,
    chain: String,
    tags: List<String>,
    query: String
): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) return true
    return name.contains(normalized, ignoreCase = true) ||
        chain.contains(normalized, ignoreCase = true) ||
        tags.any { it.contains(normalized, ignoreCase = true) }
}

internal data class AirdropFilterOption(val value: String, val label: String)
internal data class AirdropFilterGroup(val key: String, val title: String, val options: List<AirdropFilterOption>)

internal val airdropFilterGroups = listOf(
    AirdropFilterGroup("supported_chains", "公链 / 网络", listOf(
        AirdropFilterOption("ethereum", "Ethereum"), AirdropFilterOption("arbitrum-one", "Arbitrum One"),
        AirdropFilterOption("arbitrum-nova", "Arbitrum Nova"), AirdropFilterOption("optimism", "Optimism"),
        AirdropFilterOption("op-mainnet", "OP Mainnet"), AirdropFilterOption("base", "Base"),
        AirdropFilterOption("linea", "Linea"), AirdropFilterOption("scroll", "Scroll"),
        AirdropFilterOption("zksync-era", "zkSync Era"), AirdropFilterOption("starknet", "Starknet"),
        AirdropFilterOption("solana", "Solana"), AirdropFilterOption("bnb-chain", "BNB Chain"),
        AirdropFilterOption("polygon", "Polygon"), AirdropFilterOption("avalanche", "Avalanche"),
        AirdropFilterOption("sui", "Sui"), AirdropFilterOption("aptos", "Aptos"),
        AirdropFilterOption("ton", "TON"), AirdropFilterOption("bitcoin", "Bitcoin"),
        AirdropFilterOption("cosmos", "Cosmos")
    )),
    AirdropFilterGroup("categories", "项目赛道", listOf(
        AirdropFilterOption("layer2", "Layer2"), AirdropFilterOption("defi", "DeFi"),
        AirdropFilterOption("dex", "DEX"), AirdropFilterOption("lending", "借贷"),
        AirdropFilterOption("bridge", "跨链桥"), AirdropFilterOption("staking", "质押"),
        AirdropFilterOption("restaking", "再质押"), AirdropFilterOption("btcfi", "BTCFi"),
        AirdropFilterOption("rwa", "RWA"), AirdropFilterOption("nft", "NFT"),
        AirdropFilterOption("gamefi", "GameFi"), AirdropFilterOption("ai", "AI"),
        AirdropFilterOption("depin", "DePIN"), AirdropFilterOption("oracle", "预言机"),
        AirdropFilterOption("infrastructure", "基础设施"), AirdropFilterOption("socialfi", "SocialFi"),
        AirdropFilterOption("wallet", "钱包 / AA"), AirdropFilterOption("privacy", "隐私"),
        AirdropFilterOption("modular-da", "模块化 / DA")
    )),
    AirdropFilterGroup("participation_requirements", "参与方式 / 任务", listOf(
        AirdropFilterOption("onchain_interaction", "链上交互"), AirdropFilterOption("bridge", "跨链"),
        AirdropFilterOption("swap", "Swap 兑换"), AirdropFilterOption("trade", "交易"),
        AirdropFilterOption("liquidity", "提供流动性"), AirdropFilterOption("lending", "借贷"),
        AirdropFilterOption("staking", "质押"), AirdropFilterOption("restaking", "再质押"),
        AirdropFilterOption("hold_token", "持有代币"), AirdropFilterOption("hold_nft", "持有 NFT"),
        AirdropFilterOption("governance", "治理 / 投票"), AirdropFilterOption("testnet", "测试网交互"),
        AirdropFilterOption("points_quest", "积分 / 任务"), AirdropFilterOption("social", "社交任务"),
        AirdropFilterOption("referral", "邀请 / 推荐"), AirdropFilterOption("github_developer", "开发者贡献"),
        AirdropFilterOption("event_poap", "活动 / POAP")
    )),
    AirdropFilterGroup("funding_requirement", "资金门槛", listOf(
        AirdropFilterOption("no_extra_capital", "无需额外本金"), AirdropFilterOption("gas_only", "仅需 Gas"),
        AirdropFilterOption("low_capital", "少量资金"), AirdropFilterOption("capital_required", "需要本金"),
        AirdropFilterOption("locked_capital", "资金需锁仓"), AirdropFilterOption("unknown", "官方未说明")
    )),
    AirdropFilterGroup("campaign_stage", "项目 / 活动阶段", listOf(
        AirdropFilterOption("testnet", "测试网阶段"), AirdropFilterOption("points_season", "积分赛季"),
        AirdropFilterOption("mainnet_activity", "主网交互"), AirdropFilterOption("snapshot_announced", "已公布快照"),
        AirdropFilterOption("claim_open", "开放领取"), AirdropFilterOption("ended", "已结束"),
        AirdropFilterOption("unknown", "阶段未确认")
    )),
    AirdropFilterGroup("reward_types", "奖励类型", listOf(
        AirdropFilterOption("points", "积分"), AirdropFilterOption("token", "代币"),
        AirdropFilterOption("nft", "NFT"), AirdropFilterOption("allowlist", "白名单 / 资格"),
        AirdropFilterOption("rebate", "返佣 / 手续费返还"), AirdropFilterOption("unknown", "奖励未确认")
    )),
    AirdropFilterGroup("risk_flags", "风险 / 资格限制", listOf(
        AirdropFilterOption("kyc", "需要 KYC"), AirdropFilterOption("region_restriction", "地区限制"),
        AirdropFilterOption("lockup", "奖励锁仓"), AirdropFilterOption("impermanent_loss", "无常损失风险"),
        AirdropFilterOption("contract_approval", "合约授权风险"), AirdropFilterOption("deposit_required", "需要充值 / 存款"),
        AirdropFilterOption("official_task_verification", "官方任务核验")
    )),
    AirdropFilterGroup("effort_level", "时间投入", listOf(
        AirdropFilterOption("quick", "短时 / 一次性"), AirdropFilterOption("recurring", "周期性参与"),
        AirdropFilterOption("long_term", "长期持续"), AirdropFilterOption("unknown", "投入未知")
    ))
)

internal data class AirdropFilterSelection(val values: Map<String, Set<String>> = emptyMap()) {
    val selectedCount: Int get() = values.values.sumOf { it.size }

    fun toggle(group: String, value: String): AirdropFilterSelection {
        val next = values.toMutableMap()
        val selected = next[group].orEmpty().toMutableSet()
        if (!selected.add(value)) selected.remove(value)
        if (selected.isEmpty()) next.remove(group) else next[group] = selected
        return copy(values = next)
    }
}

internal data class AirdropFilterMetadata(
    val chain: String,
    val supportedChains: List<String>,
    val categories: List<String>,
    val participationRequirements: List<String>,
    val fundingRequirement: String,
    val campaignStage: String,
    val rewardTypes: List<String>,
    val riskFlags: List<String>,
    val effortLevel: String
)

internal fun matchesAirdropFilters(metadata: AirdropFilterMetadata, selection: AirdropFilterSelection): Boolean {
    if (selection.values.isEmpty()) return true
    val valuesByGroup = mapOf(
        "supported_chains" to (metadata.supportedChains.ifEmpty { listOf(metadata.chain.lowercase().replace(" ", "-")) }),
        "categories" to metadata.categories,
        "participation_requirements" to metadata.participationRequirements,
        "funding_requirement" to listOf(metadata.fundingRequirement),
        "campaign_stage" to listOf(metadata.campaignStage),
        "reward_types" to metadata.rewardTypes,
        "risk_flags" to metadata.riskFlags,
        "effort_level" to listOf(metadata.effortLevel)
    )
    return selection.values.all { (group, selected) ->
        val actual = valuesByGroup[group].orEmpty()
        if ("unknown" in selected) actual.isEmpty() || actual == listOf("unknown")
        else actual.any { it in selected }
    }
}
