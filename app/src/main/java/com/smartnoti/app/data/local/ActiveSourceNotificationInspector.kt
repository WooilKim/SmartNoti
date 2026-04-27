package com.smartnoti.app.data.local

import com.smartnoti.app.notification.SmartNotiNotificationListenerService

/**
 * Plan `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
 * Task 5 — pure read-only port that lets the
 * [MigrateOrphanedSourceCancellationRunner] confirm a source tray entry
 * (identified by its StatusBarNotification `key`) is still live before
 * asking the [SourceCancellationGateway] to cancel it.
 *
 * Why two surfaces:
 *  - [isListenerBound] tells the runner whether the listener service is
 *    currently connected. If false the runner cannot meaningfully cancel
 *    anything (Risks R4) — `cancelNotification(key)` requires a live
 *    [android.service.notification.NotificationListenerService] instance.
 *    The runner returns
 *    [MigrateOrphanedSourceCancellationRunner.Result.DeferredListenerNotBound]
 *    in that case and leaves the migration flag unflipped so the next
 *    cold start retries.
 *  - [isSourceKeyActive] gates the per-row cancel call. The user (or the
 *    OS) may already have cleared the source between the original
 *    capture and this cold start, in which case the migration runner
 *    skips the cancel — no harm, just a wasted call — but more
 *    importantly avoids a misleading "cancelled N orphan sources" log
 *    metric.
 *
 * Production implementation reads
 * [SmartNotiNotificationListenerService.activeNotifications] under the
 * static service singleton; tests provide a fake (see
 * `MigrateOrphanedSourceCancellationRunnerTest.FakeActiveSourceNotificationInspector`).
 */
interface ActiveSourceNotificationInspector {
    fun isListenerBound(): Boolean
    fun isSourceKeyActive(sourceEntryKey: String): Boolean
}

/**
 * Production [ActiveSourceNotificationInspector] — reads the active
 * notification snapshot from the currently connected listener service via
 * [SmartNotiNotificationListenerService.activeSourceKeysSnapshotIfConnected].
 * Both methods short-circuit when the listener is not bound (returning
 * `false` for `isListenerBound` / `false` for every key lookup).
 */
class ListenerActiveSourceNotificationInspector : ActiveSourceNotificationInspector {
    override fun isListenerBound(): Boolean {
        return SmartNotiNotificationListenerService
            .activeSourceKeysSnapshotIfConnected() != null
    }

    override fun isSourceKeyActive(sourceEntryKey: String): Boolean {
        val snapshot = SmartNotiNotificationListenerService
            .activeSourceKeysSnapshotIfConnected()
            ?: return false
        return sourceEntryKey in snapshot
    }
}
