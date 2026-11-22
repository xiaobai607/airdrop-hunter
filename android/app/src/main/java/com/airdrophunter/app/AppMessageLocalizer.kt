package com.airdrophunter.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Translates known application messages and prevents server-side Chinese fallbacks
 * from leaking into the English UI. Unknown English messages remain unchanged.
 */
@Composable
internal fun localizeKnownMessage(message: String?): String? {
    if (message.isNullOrBlank()) return message
    return when (message) {
        "暂时无法读取数据源状态" -> stringResource(R.string.message_source_status_unavailable)
        "空投活动暂不可用" -> stringResource(R.string.message_airdrop_campaign_unavailable)
        "刷新失败" -> stringResource(R.string.message_refresh_failed)
        "数据库中暂无项目。可点击刷新同步公开数据源。" -> stringResource(R.string.message_hub_empty_projects)
        "服务器繁忙，请稍后重试。" -> stringResource(R.string.message_server_busy)
        "同步已执行，但数据库中暂时没有可展示的项目。" -> stringResource(R.string.message_hub_sync_empty)
        "数据源暂时不可用，已保留上次数据。" -> stringResource(R.string.message_hub_cached_warning)
        "同步暂不可用，已保留本地项目" -> stringResource(R.string.message_projects_sync_cached)
        "无法生成资格领取入口" -> stringResource(R.string.message_project_claim_error)
        "空投记录已保存到云端" -> stringResource(R.string.message_airdrop_saved_cloud)
        else -> if (message.any { it in '\u4e00'..'\u9fff' }) {
            stringResource(R.string.message_generic_error)
        } else {
            message
        }
    }
}
