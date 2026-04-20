package com.smartnoti.app.notification

/**
 * Identifies the system-tray notifications that should be cancelled because the app has
 * already classified them as SILENT (and therefore stored in the Hidden inbox), but whose
 * original rows predated [silent-auto-hide] and so never got cancelled.
 *
 * Pure Kotlin so it can be unit-tested without the Android framework. The caller is
 * responsible for translating [ActiveSourceNotification] and [SilentEntry] from the real
 * runtime (StatusBarNotification / NotificationEntity) before invoking.
 */
internal data class ActiveSourceNotification(
    val key: String,
    val packageName: String,
    val contentSignature: String,
    val isProtected: Boolean,
)

internal data class SilentEntry(
    val packageName: String,
    val contentSignature: String,
)

internal object SilentSourceMigrationSweeper {

    fun keysToCancel(
        activeNotifications: List<ActiveSourceNotification>,
        silentEntries: Set<SilentEntry>,
    ): List<String> {
        if (silentEntries.isEmpty()) return emptyList()
        return activeNotifications.mapNotNull { active ->
            if (active.isProtected) return@mapNotNull null
            val candidate = SilentEntry(
                packageName = active.packageName,
                contentSignature = active.contentSignature,
            )
            if (candidate !in silentEntries) return@mapNotNull null
            active.key
        }
    }
}
