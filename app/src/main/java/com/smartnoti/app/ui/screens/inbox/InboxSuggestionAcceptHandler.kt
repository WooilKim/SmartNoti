package com.smartnoti.app.ui.screens.inbox

import com.smartnoti.app.data.local.HighVolumeAppCandidate

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 3. Pure helper that orchestrates the `[예, 묶을게요]` side-effect chain
 * for [InboxSuggestionCard].
 *
 * Documented order (pinned by `InboxSuggestionAcceptIntegrationTest`):
 *  1. `addToSuppressedSourceApps(pkg)` — committed FIRST so any racing emission
 *     through the listener picks up the new entry via #511's forward-only fix.
 *  2. `cleanupTrayOrphans(setOf(pkg))` — drains historical orphans for the
 *     same package so the user sees a clean tray on the next swipe-down.
 *  3. `dismissCard()` — clears the in-memory state so the next-tick re-detect
 *     promotes the next ranked candidate. Runs LAST so the user does not see
 *     a stale card in the brief window between commit + cleanup.
 *
 * Best-effort cleanup: an `InboxSuggestionCleanupOutcome.NotBound` does NOT
 * roll back the suppress write or skip the dismiss — the user's intent
 * ("stop bundling this app") is independent of listener bind state. The next
 * tray-write after the listener reconnects will already be filtered by the
 * fresh `suppressedSourceApps` entry.
 */
object InboxSuggestionAcceptHandler {

    suspend fun accept(
        candidate: HighVolumeAppCandidate,
        callbacks: InboxSuggestionAcceptCallbacks,
    ) {
        callbacks.addToSuppressedSourceApps(candidate.packageName)
        // Result intentionally ignored — even NotBound proceeds to dismiss.
        // InboxScreen wiring inspects the outcome separately for the Snackbar
        // nudge ("트레이 정리는 알림 권한이 활성일 때만 가능해요") so this
        // helper does not need it.
        callbacks.cleanupTrayOrphans(setOf(candidate.packageName))
        callbacks.dismissCard()
    }
}
