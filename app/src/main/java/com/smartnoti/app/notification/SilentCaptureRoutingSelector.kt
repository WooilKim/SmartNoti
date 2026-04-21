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
    @Suppress("UNUSED_PARAMETER")
    fun silentModeFor(
        decision: NotificationDecision,
        isPersistent: Boolean,
        shouldBypassPersistentHiding: Boolean,
        isProtectedSourceNotification: Boolean,
    ): SilentMode? {
        // TODO(silent-archive-drift-fix Task 2): return SilentMode.ARCHIVED for the
        //  fresh-capture SILENT branch. Kept `null` here on purpose so the Task 1
        //  failing tests in SilentArchivedCapturePathTest pin the drift first.
        return null
    }
}
