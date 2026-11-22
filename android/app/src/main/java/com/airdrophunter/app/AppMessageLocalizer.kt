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
        "手动记账" -> stringResource(R.string.acct_ui_method_manual)
        "手动记账（非链上交易）" -> stringResource(R.string.acct_ui_method_manual_nonchain)
        "链上交易导入" -> stringResource(R.string.acct_ui_method_onchain_import)
        "账号或密码错误" -> stringResource(R.string.auth_error_invalid_credentials)
        "登录状态无效或已过期，请重新登录" -> stringResource(R.string.auth_error_session_expired)
        "注册功能开发中，请等待后续更新" -> stringResource(R.string.auth_error_registration_disabled)
        "该账号已存在" -> stringResource(R.string.auth_error_username_exists)
        "账号只能由4到20位英文字母和数字组成" -> stringResource(R.string.auth_error_username_format)
        "密码长度必须为8到30位" -> stringResource(R.string.auth_error_password_format)
        "账号或密码格式不正确" -> stringResource(R.string.auth_error_format)
        "暂时无法分配用户编号，请稍后重试" -> stringResource(R.string.auth_error_uid_unavailable)
        else -> if (message.any { it in '\u4e00'..'\u9fff' }) {
            stringResource(R.string.message_generic_error)
        } else {
            message
        }
    }
}
