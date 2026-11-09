package com.airdrophunter.app.data

private const val ACCOUNTING_REMINDER_DELAY_MILLIS = 3L * 24L * 60L * 60L * 1000L

/** Shows a gentle reminder only after more than three full days without a confirmed bill. */
fun shouldShowAccountingReminder(
    createdAtMillis: Long,
    confirmedInteractionCount: Int,
    nowMillis: Long
): Boolean = createdAtMillis > 0L &&
    confirmedInteractionCount == 0 &&
    nowMillis >= createdAtMillis &&
    nowMillis - createdAtMillis > ACCOUNTING_REMINDER_DELAY_MILLIS
