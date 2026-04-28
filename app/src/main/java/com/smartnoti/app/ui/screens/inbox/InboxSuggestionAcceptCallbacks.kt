package com.smartnoti.app.ui.screens.inbox

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 3.
 *
 * Side-effect surface that [InboxSuggestionAcceptHandler] consumes when the
 * user taps `[예, 묶을게요]`. Lifted into a dedicated interface so the helper
 * stays Compose-free and JVM-testable — the production callsite (InboxScreen)
 * binds these against `SettingsRepository` + `TrayOrphanCleanupRunner` while
 * unit tests bind a recorder.
 *
 * Three callbacks fire on a successful `[예]` (in this exact order, pinned by
 * `InboxSuggestionAcceptIntegrationTest.accept_runs_suppress_and_cleanup_in_documented_order`):
 *  1. [addToSuppressedSourceApps] — commits the user's intent to DataStore so
 *     the listener's forward-only fix (#511) can act on the next emission.
 *  2. [cleanupTrayOrphans] — drains historical orphans for that one package
 *     via the scoped overload of `TrayOrphanCleanupRunner`. Best-effort: even
 *     a `NotBound` outcome must not roll back the suppress write or skip the
 *     dismiss.
 *  3. [dismissCard] — clears the in-memory suggestion state so the next-tick
 *     re-detect promotes the next ranked candidate.
 *
 * The other two callbacks ([markSuggestionPermanentlyDismissed],
 * [snoozeSuggestionUntil]) are NOT touched by `accept(...)` — they live on
 * the same interface so InboxScreen can pass a single object to all three of
 * the card's button handlers without juggling separate interfaces. The
 * accept handler asserts (via the integration test's
 * `accept_does_not_touch_dismissed_or_snoozed_paths` case) that it does not
 * call them, so a future refactor that accidentally re-routes accept through
 * the dismiss path is caught at unit-test time.
 */
interface InboxSuggestionAcceptCallbacks {

    /**
     * Add [packageName] to `SmartNotiSettings.suppressedSourceApps` (atomic
     * DataStore edit). Implementations should be idempotent — calling twice
     * with the same packageName must converge on the same final state.
     */
    suspend fun addToSuppressedSourceApps(packageName: String)

    /**
     * Invoke `TrayOrphanCleanupRunner.cleanup(targetPackages)` (the scoped
     * overload from #524 follow-up). Implementations should swallow runner
     * exceptions and surface them as
     * [InboxSuggestionCleanupOutcome.NotBound] / [InboxSuggestionCleanupOutcome.Cancelled]
     * so the handler can still call [dismissCard] regardless.
     */
    suspend fun cleanupTrayOrphans(targetPackages: Set<String>): InboxSuggestionCleanupOutcome

    /**
     * Clear the in-memory suggestion state so the InboxScreen LazyColumn
     * stops emitting the suggestion item. Production wiring sets the host
     * `suggestion: HighVolumeAppCandidate?` state to `null`.
     */
    suspend fun dismissCard()

    /**
     * Sticky-permanent: write `packageName` into
     * `SmartNotiSettings.suggestedSuppressionDismissed` so [HighVolumeAppDetector]
     * never proposes it again. Wired to the `[무시]` button.
     */
    suspend fun markSuggestionPermanentlyDismissed(packageName: String)

    /**
     * 24h snooze: write `packageName -> untilMillis` into
     * `SmartNotiSettings.suggestedSuppressionSnoozeUntil`. Wired to the
     * `[나중에]` button.
     */
    suspend fun snoozeSuggestionUntil(packageName: String, untilMillis: Long)
}

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 3. Outcome of [InboxSuggestionAcceptCallbacks.cleanupTrayOrphans].
 *
 * Sealed so the InboxScreen wiring can pattern-match exhaustively:
 *  - [Cancelled] — the runner cancelled `cancelledCount` tray entries (may
 *    be 0 if no orphans existed for that package).
 *  - [NotBound] — the listener service is not connected; suppress was still
 *    committed but the caller surfaces a Snackbar nudge.
 */
sealed class InboxSuggestionCleanupOutcome {
    data class Cancelled(val cancelledCount: Int) : InboxSuggestionCleanupOutcome()
    object NotBound : InboxSuggestionCleanupOutcome()
}
