package com.smartnoti.app.data.local

import android.util.Log
import com.smartnoti.app.notification.SmartNotiNotificationListenerService

/**
 * Plan `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
 * Task 2 — explicit port that any replacement-builder site (and the
 * orphan-cohort migration runner) can call to cancel the **source** tray
 * entry that produced the row SmartNoti is now showing as a replacement.
 *
 * The Android constraint: `cancelNotification(key)` is only valid on a
 * connected [android.service.notification.NotificationListenerService]
 * instance. Builder sites and the migration runner do not own a listener
 * instance, so they cannot cancel directly. This port lets them delegate
 * to the live listener via
 * [SmartNotiNotificationListenerService.cancelSourceEntryIfConnected].
 * When the listener is not bound the call is a best-effort no-op that
 * surfaces a warn-level log; the replacement itself is unaffected.
 *
 * Mirrors the listener delegation pattern proven in
 * [com.smartnoti.app.notification.MarkSilentProcessedTrayCancelChain]
 * (Detail's "처리 완료로 표시" flow) and the pipeline's
 * [SmartNotiNotificationListenerService] inner
 * `ListenerSourceTrayActions.cancelSource` implementation.
 *
 * Lives under `data.local` so the
 * [MigrateOrphanedSourceCancellationRunner] (Task 4) can take this port
 * + an [ActiveSourceNotificationInspector] without pulling its test
 * harness into the `notification` package.
 */
interface SourceCancellationGateway {
    /**
     * Best-effort cancellation of the source tray entry identified by
     * [sourceEntryKey]. Implementations MUST NOT throw — failure to reach
     * a live listener is logged and swallowed so the replacement post is
     * never compromised by a tray-cancel hiccup.
     */
    fun cancel(sourceEntryKey: String)
}

/**
 * Production [SourceCancellationGateway] — hands the cancel request to the
 * currently connected listener service through the
 * [SmartNotiNotificationListenerService.cancelSourceEntryIfConnected]
 * static helper. The helper itself wraps the actual `cancelNotification`
 * call in a `runCatching`, so this gateway only needs to log the
 * not-bound / failure outcomes.
 *
 * Caller threading: the helper invokes `cancelNotification(key)` on the
 * caller's thread. Today the only call site outside the pipeline is the
 * cold-start migration runner, which dispatches through
 * `Dispatchers.Main` before invoking the gateway. The pipeline path
 * itself uses its own existing `ListenerSourceTrayActions.cancelSource`
 * (also main-dispatcher) — this gateway is intentionally not threaded
 * into the hot path because the pipeline already owns its sourceEntryKey
 * cancel.
 */
class ListenerSourceCancellationGateway : SourceCancellationGateway {
    override fun cancel(sourceEntryKey: String) {
        val cancelled = SmartNotiNotificationListenerService
            .cancelSourceEntryIfConnected(sourceEntryKey)
        if (!cancelled) {
            Log.w(
                TAG,
                "source-cancel skipped — listener not bound for key=$sourceEntryKey",
            )
        }
    }

    private companion object {
        const val TAG = "SourceCancelGateway"
    }
}
