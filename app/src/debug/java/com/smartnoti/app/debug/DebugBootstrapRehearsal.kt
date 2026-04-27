package com.smartnoti.app.debug

import android.service.notification.StatusBarNotification
import com.smartnoti.app.notification.ActiveStatusBarNotificationBootstrapper

/**
 * Debug-APK-only re-entry point for the onboarding active-notification
 * bootstrap pipeline.
 *
 * Production semantics: the onboarding bootstrap is a one-shot —
 * `OnboardingActiveNotificationBootstrapCoordinator` raises a pending
 * flag during onboarding completion, and the listener consumes it
 * exactly once on the next `onListenerConnected`. After that the
 * pipeline is dormant for the rest of the app's lifetime.
 *
 * That one-shot is correct for users but blocks the
 * `onboarding-bootstrap` journey verification recipe from being
 * automated by `journey-tester` — the original recipe required
 * `pm clear` to re-arm the flag, which also wipes the
 * NotificationListener permission grant. The journey has been stuck
 * SKIPped on every rotation tick (2026-04-26, 2026-04-27 sweeps) for
 * exactly that reason.
 *
 * This entry point bypasses the one-shot flag and re-runs the
 * bootstrapper directly against the supplied active notifications,
 * piping each processed entry through the same
 * `recordProcessedByBootstrap` hook that
 * `SmartNotiNotificationListenerService.enqueueOnboardingBootstrapCheck`
 * uses, so a follow-up reconnect sweep cannot double-process.
 *
 * The hook lives in `app/src/debug/`, so it is never merged into a
 * release APK. Plan:
 * `docs/plans/2026-04-27-onboarding-bootstrap-non-destructive-recipe.md`.
 */
object DebugBootstrapRehearsal {

    /**
     * Reports the outcome of one rehearsal invocation. `processedCount`
     * is the number of active notifications that survived
     * [ActiveStatusBarNotificationBootstrapper.shouldProcess] and were
     * forwarded to [processNotification]; `skippedCount` is the count
     * filtered out (typically SmartNoti-self notifications and blank
     * group summaries).
     */
    data class Result(val processedCount: Int, val skippedCount: Int)

    /**
     * Re-runs the bootstrap pipeline against [activeNotifications],
     * recording each processed entry's dedup key via
     * [recordProcessedByBootstrap] before invoking [processNotification]
     * — exactly mirroring the order the production listener uses inside
     * `enqueueOnboardingBootstrapCheck` so a reconnect sweep enqueued
     * concurrently sees the dedup record before it sweeps.
     *
     * The function is intentionally agnostic of [SettingsRepository] —
     * it never reads or mutates the production
     * `isOnboardingBootstrapPending` flag, because the verification
     * intent is to re-exercise the pipeline _after_ the production
     * flag has already been consumed. That is what makes the recipe
     * "non-destructive": no `pm clear`, no permission grant wipe.
     */
    suspend fun rehearse(
        appPackageName: String,
        activeNotifications: Array<StatusBarNotification>,
        recordProcessedByBootstrap: (StatusBarNotification) -> Unit,
        processNotification: suspend (StatusBarNotification) -> Unit,
    ): Result {
        var processed = 0
        var skipped = 0
        val bootstrapper = ActiveStatusBarNotificationBootstrapper(
            appPackageName = appPackageName,
            processNotification = { sbn ->
                // Match production order: record the dedup key first so
                // a concurrent reconnect sweep cannot re-enqueue the
                // same item between this record() and the actual save.
                recordProcessedByBootstrap(sbn)
                processNotification(sbn)
                processed += 1
            },
        )
        // Drive the bootstrapper one notification at a time so we can
        // count the skipped ones (the bulk `bootstrap()` API silently
        // drops them via shouldProcess, which would lose the count we
        // need to surface for verification).
        activeNotifications.forEach { sbn ->
            if (bootstrapper.shouldProcess(sbn)) {
                bootstrapper.bootstrap(arrayOf(sbn))
            } else {
                skipped += 1
            }
        }
        return Result(processedCount = processed, skippedCount = skipped)
    }
}
