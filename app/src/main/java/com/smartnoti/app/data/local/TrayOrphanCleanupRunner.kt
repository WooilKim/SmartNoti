package com.smartnoti.app.data.local

import android.app.Notification
import android.content.Context
import android.util.Log
import com.smartnoti.app.BuildConfig

/**
 * Plan `docs/plans/2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md`
 * Task 2 — user-triggered, single-pass cleanup of source notifications that
 * are still occupying the system tray after SmartNoti has already posted a
 * `silent_group_app:<pkg>` replacement.
 *
 * Identification (single O(N) pass):
 *   1. Read the active StatusBarNotification snapshot via [ActiveTrayInspector].
 *   2. Extract the source `packageName` set from every entry whose `groupKey`
 *      starts with [SILENT_GROUP_APP_PREFIX]. Those are the SmartNoti-posted
 *      grouped summaries — their suffix is the source packageName they
 *      represent.
 *   3. Treat any non-SmartNoti entry whose `packageName` is in that set as
 *      a cleanup candidate.
 *   4. Filter out PERSISTENT_PROTECTED candidates whose `flags` intersect
 *      [PROTECTED_FLAGS] — cancelling a music / call / nav / foreground-
 *      service notification breaks the source app contract (see
 *      `docs/journeys/protected-source-notifications.md`).
 *   5. Hand each remaining candidate's `key` to [SourceCancellationGateway].
 *
 * Listener-not-bound resilience: `cancelNotification(key)` only works on a
 * connected `NotificationListenerService`. If the inspector reports the
 * listener is not bound, the runner short-circuits with `notBound = true`
 * so the UI can surface the "알림 권한 활성 후 다시 시도해 주세요" toast
 * without invoking the gateway.
 *
 * Idempotent: re-running cleans up any new orphans that have arrived
 * since the last run (the #511 forward-fix should make this set tend to
 * zero, but a buggy source app or a temporary listener disconnect could
 * still leak entries).
 */
class TrayOrphanCleanupRunner(
    private val inspector: ActiveTrayInspector,
    private val gateway: SourceCancellationGateway,
) {

    /**
     * Identify cleanup candidates without invoking the gateway. Drives the
     * Settings card's live "원본 알림 N건 정리 가능 (앱 …)" preview line.
     *
     * Returns 0 candidates when the listener is not bound — the UI builder
     * decides how to surface that case (see [PreviewResult] callers).
     */
    fun preview(): PreviewResult {
        if (!inspector.isListenerBound()) {
            return PreviewResult(candidateCount = 0, candidatePackageNames = emptyList())
        }
        val cancellable = identifyCancellable(inspector.listActive())
        // Dedupe + preserve insertion order so the UI builder shows the
        // first 3 packages encountered, then "외 K개" for the rest.
        val packageNames = cancellable.map(ActiveTrayEntry::packageName).distinct()
        return PreviewResult(
            candidateCount = cancellable.size,
            candidatePackageNames = packageNames,
        )
    }

    /**
     * Cancel each cleanup candidate via [SourceCancellationGateway]. Returns
     * the breakdown so the UI can surface a precise toast / retoast preview.
     *
     * `notBound = true` is a deliberate marker: the runner cannot
     * meaningfully cancel anything when the listener is not connected (the
     * `cancelNotification(key)` API requires a live service). Callers
     * surface this as a "알림 권한 확인" hint rather than silently logging
     * "0 cancelled".
     */
    suspend fun cleanup(): CleanupResult {
        if (!inspector.isListenerBound()) {
            return CleanupResult(
                cancelledCount = 0,
                skippedProtectedCount = 0,
                notBound = true,
            )
        }

        val entries = inspector.listActive()
        val candidates = identifyCandidates(entries)
        val (cancellable, protectedEntries) = candidates.partition { entry ->
            (entry.flags and PROTECTED_FLAGS) == 0
        }

        // Logged at warn level so the ADB e2e dump can confirm the
        // PERSISTENT_PROTECTED skip set matches the live tray contents.
        protectedEntries.forEach { entry ->
            Log.w(
                TAG,
                "tray-cleanup skip protected pkg=${entry.packageName} key=${entry.key} flags=0x${Integer.toHexString(entry.flags)}",
            )
        }

        cancellable.forEach { entry -> gateway.cancel(entry.key) }

        return CleanupResult(
            cancelledCount = cancellable.size,
            skippedProtectedCount = protectedEntries.size,
            notBound = false,
        )
    }

    /**
     * Steps 1-3 of the identification algorithm: extract the SmartNoti
     * orphan-source packageName set from `silent_group_app:` group keys,
     * then return every non-SmartNoti entry whose packageName is in that
     * set. PERSISTENT_PROTECTED filtering is intentionally split out into
     * [identifyCancellable] / the cleanup partition so callers can either
     * report or act on the distinction.
     */
    private fun identifyCandidates(entries: List<ActiveTrayEntry>): List<ActiveTrayEntry> {
        val orphanPackages = entries.asSequence()
            .mapNotNull { entry ->
                val groupKey = entry.groupKey ?: return@mapNotNull null
                if (!groupKey.startsWith(SILENT_GROUP_APP_PREFIX)) return@mapNotNull null
                groupKey.substringAfter(SILENT_GROUP_APP_PREFIX).takeIf { it.isNotBlank() }
            }
            .toHashSet()

        if (orphanPackages.isEmpty()) return emptyList()

        return entries.filter { entry ->
            entry.packageName != BuildConfig.APPLICATION_ID &&
                entry.packageName in orphanPackages
        }
    }

    /**
     * Identification + PERSISTENT_PROTECTED filter (preview path). The
     * cleanup path uses `partition` instead so it can also report the
     * skipped count.
     */
    private fun identifyCancellable(entries: List<ActiveTrayEntry>): List<ActiveTrayEntry> {
        return identifyCandidates(entries).filter { entry ->
            (entry.flags and PROTECTED_FLAGS) == 0
        }
    }

    data class PreviewResult(
        val candidateCount: Int,
        val candidatePackageNames: List<String>,
    )

    data class CleanupResult(
        val cancelledCount: Int,
        val skippedProtectedCount: Int,
        val notBound: Boolean,
    )

    companion object {
        /**
         * SmartNoti silent-group tag for App-keyed groups. Mirrors
         * `SilentHiddenSummaryNotifier.GROUP_TAG_PREFIX + "app:"` —
         * duplicated as a literal so this runner stays Android-free at
         * unit-test time. If the constant in the notifier ever changes,
         * `TrayOrphanCleanupRunnerTest.fresh_cleanup_…` and
         * `SilentHiddenSummaryNotifier` must move together.
         */
        const val SILENT_GROUP_APP_PREFIX: String = "smartnoti_silent_group_app:"

        /**
         * Bitmask of `Notification.flags` values that mark an active entry
         * as PERSISTENT_PROTECTED for the cleanup path. The lighter flag
         * triplet here is intentionally narrower than
         * [com.smartnoti.app.notification.ProtectedSourceNotificationDetector]
         * (which also looks at `category` / MediaSession extras / template
         * names) because the cleanup runner has access only to the
         * [ActiveTrayEntry] projection — flags are the single signal that
         * survives the projection. False-positive bias (skip too many) is
         * preferred to false-negative (cancel a music / call notification);
         * if a protected source slips through, the next plan iteration
         * widens the projection to carry the richer detector signals.
         */
        const val PROTECTED_FLAGS: Int =
            Notification.FLAG_FOREGROUND_SERVICE or
                Notification.FLAG_NO_CLEAR or
                Notification.FLAG_ONGOING_EVENT

        private const val TAG = "TrayOrphanCleanupRunner"

        /**
         * Production wiring — used by the Settings screen (Task 4) to
         * obtain a runner backed by the live listener service.
         */
        fun create(@Suppress("UNUSED_PARAMETER") context: Context): TrayOrphanCleanupRunner {
            return TrayOrphanCleanupRunner(
                inspector = ListenerActiveTrayInspector(),
                gateway = ListenerSourceCancellationGateway(),
            )
        }
    }
}
