package com.smartnoti.app.domain.usecase

/**
 * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 3.
 *
 * `windowMillis` is now caller-injected (no companion default). The listener
 * builds an instance per `processNotification` call site from
 * `SmartNotiSettings.duplicateWindowMinutes * 60_000L` so the user-tunable
 * dropdown takes effect on the next notification with no app restart. Test
 * call sites pass an explicit window so the contract stays honest.
 */
class DuplicateNotificationPolicy(
    private val windowMillis: Long,
) {
    fun contentSignature(title: String, body: String): String {
        return listOf(title, body)
            .joinToString(" ")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun windowStart(postedAtMillis: Long): Long = postedAtMillis - windowMillis
}