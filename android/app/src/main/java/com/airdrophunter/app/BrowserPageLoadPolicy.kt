package com.airdrophunter.app

/**
 * Shared timing and completion rules for one browser main-frame load attempt.
 * A finished callback must not turn a failed main-frame navigation into success.
 */
internal object BrowserPageLoadPolicy {
    const val ATTEMPT_TIMEOUT_MS = 12_000L

    fun shouldMarkSuccessful(mainFrameFailed: Boolean): Boolean = !mainFrameFailed
}
