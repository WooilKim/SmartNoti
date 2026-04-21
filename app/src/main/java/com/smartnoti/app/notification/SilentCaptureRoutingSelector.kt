package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.SilentMode

/**
 * Thin collaborator that decides which [SilentMode] the listener capture path should
 * pass into [SourceNotificationRoutingPolicy.route] for a freshly classified notification.
 *
 * Extracted from [SmartNotiNotificationListenerService] so the routing-selection contract
 * can be unit tested without spinning up Android's [android.service.notification.NotificationListenerService]
 * lifecycle. Plan: `docs/plans/2026-04-20-silent-archive-drift-fix.md` — Task 1 captures the
 * drift where newly classified SILENT notifications are still routed with `silentMode = null`
 * (legacy cancel) instead of [SilentMode.ARCHIVED]. This stub preserves the drift so the
 * accompanying tests fail for the intended reason; Task 2 flips the logic to return
 * [SilentMode.ARCHIVED] for the fresh-capture branch.
 *
 * Contract the tests encode (Task 1, to be satisfied by Task 2):
 * - Decision is SILENT, not persistent, not protected → should return [SilentMode.ARCHIVED].
 * - Decision is SILENT but effectively persistent → legacy `null` (persistent is handled
 *   by `hidePersistentSourceNotification` and does not participate in the ARCHIVED split).
 * - Decision is SILENT and protected → legacy `null`. Protected notifications short-circuit
 *   routing entirely upstream; the explicit null documents the contract for that branch.
 * - Decision is PRIORITY or DIGEST → `null`. `SilentMode` is only meaningful for SILENT.
 */
internal object SilentCaptureRoutingSelector {
    fun silentModeFor(
        decision: NotificationDecision,
        isPersistent: Boolean,
        shouldBypassPersistentHiding: Boolean,
        isProtectedSourceNotification: Boolean,
    ): SilentMode? {
        // SilentMode is only meaningful for the SILENT decision branch.
        if (decision != NotificationDecision.SILENT) return null
        // Protected notifications short-circuit routing upstream and must stay on
        // legacy null so future refactors that collapse the short-circuit do not
        // accidentally cancel media / call / foreground-service source notifications.
        if (isProtectedSourceNotification) return null
        // Persistent notifications are hidden via the dedicated
        // `hidePersistentSourceNotification` path and must not participate in the
        // ARCHIVED split — unless the listener has already opted to bypass the
        // persistent-hiding branch (e.g. the user configured us to keep a critical
        // persistent alert out of the tray), in which case the notification is a
        // fresh capture just like any other SILENT row.
        if (isPersistent && !shouldBypassPersistentHiding) return null
        return SilentMode.ARCHIVED
    }
}
