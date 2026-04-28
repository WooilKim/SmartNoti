package com.smartnoti.app.data.local

import com.smartnoti.app.notification.SmartNotiNotificationListenerService

/**
 * Plan `docs/plans/2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md`
 * Task 2 — read-only port that exposes the connected listener service's
 * `activeNotifications` snapshot as a list of immutable [ActiveTrayEntry]
 * rows, plus a separate `isListenerBound()` predicate so callers can
 * distinguish "listener not connected" from "listener connected but tray
 * empty".
 *
 * Why a richer port than [ActiveSourceNotificationInspector]:
 *  - The orphan-cohort runner (Issue #511) only needed `isSourceKeyActive`
 *    because it iterated DB-persisted keys.
 *  - The user-triggered cleanup runner (Issue #524) iterates the **active
 *    notification snapshot itself** to derive the source packageName set
 *    from `smartnoti_silent_group_app:<pkg>` group keys. It also needs the
 *    `flags` field so it can identify PERSISTENT_PROTECTED entries
 *    (FOREGROUND_SERVICE / NO_CLEAR / ONGOING_EVENT) and skip them — those
 *    cancels would break music / call / nav / foreground-service contracts.
 *
 * Production implementation reads
 * [SmartNotiNotificationListenerService.activeTrayEntriesSnapshotIfConnected].
 * Tests provide a fake (see
 * `TrayOrphanCleanupRunnerTest.FakeActiveTrayInspector`).
 */
interface ActiveTrayInspector {
    fun isListenerBound(): Boolean
    fun listActive(): List<ActiveTrayEntry>
}

/**
 * Immutable projection of a single
 * `android.service.notification.StatusBarNotification` entry, exposing only
 * the fields [TrayOrphanCleanupRunner] needs to identify cleanup candidates
 * — keeps the runner unit-testable without the Android framework.
 */
data class ActiveTrayEntry(
    val key: String,
    val packageName: String,
    val groupKey: String?,
    val flags: Int,
)

/**
 * Production [ActiveTrayInspector] — reads the active notification snapshot
 * from the currently connected listener service via
 * [SmartNotiNotificationListenerService.activeTrayEntriesSnapshotIfConnected].
 * Both methods short-circuit when the listener is not bound: `false` for
 * `isListenerBound`, empty list for `listActive`.
 */
class ListenerActiveTrayInspector : ActiveTrayInspector {
    override fun isListenerBound(): Boolean {
        return SmartNotiNotificationListenerService
            .activeTrayEntriesSnapshotIfConnected() != null
    }

    override fun listActive(): List<ActiveTrayEntry> {
        return SmartNotiNotificationListenerService
            .activeTrayEntriesSnapshotIfConnected()
            ?: emptyList()
    }
}
