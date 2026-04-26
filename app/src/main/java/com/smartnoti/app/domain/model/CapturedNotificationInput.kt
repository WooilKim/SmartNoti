package com.smartnoti.app.domain.model

data class CapturedNotificationInput(
    val packageName: String,
    val appName: String,
    val sender: String?,
    val title: String,
    val body: String,
    val postedAtMillis: Long,
    val quietHours: Boolean,
    val duplicateCountInWindow: Int,
    val isPersistent: Boolean = false,
    val sourceEntryKey: String? = null,
    /**
     * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 4.
     *
     * User-tunable threshold the listener pulls from
     * `SmartNotiSettings.duplicateDigestThreshold` and the processor forwards
     * to `ClassificationInput.duplicateThreshold`. Default = 3 keeps existing
     * call sites (tests, fixtures, debug harnesses) on the historical
     * behavior without an explicit migration.
     */
    val duplicateThreshold: Int = 3,
)

fun CapturedNotificationInput.withContext(context: NotificationContext): CapturedNotificationInput {
    return copy(
        quietHours = context.isQuietHoursActive(),
        duplicateCountInWindow = context.duplicateCountInWindow,
    )
}
