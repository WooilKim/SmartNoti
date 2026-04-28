package com.smartnoti.app.ui.screens.inbox

import com.smartnoti.app.data.local.HighVolumeAppCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 1 (RED) — pin the integration contract for the `[예, 묶을게요]`
 * callback wired into the `InboxSuggestionCard`.
 *
 * The contract (Task 7 step 3 of the plan):
 * 1. Add the suggestion's packageName to `suppressedSourceApps` (via the
 *    settings facade).
 * 2. Invoke the scoped `TrayOrphanCleanupRunner.cleanup(setOf(pkg))` once
 *    so the orphaned source notifications drain in the same tap (#524 reuse).
 * 3. Clear the suggestion state so the next-tick re-detection promotes the
 *    next ranked candidate.
 * 4. The dismiss is best-effort: even if `TrayOrphanCleanupRunner` reports
 *    `notBound = true`, the `suppressedSourceApps` write must still land
 *    and the suggestion must still be cleared (the user's intent — "stop
 *    bundling this app" — does not depend on listener bind state).
 *
 * The Composable delegates to a pure helper [InboxSuggestionAcceptHandler]
 * (single-method `accept(candidate, callbacks)`) so this test runs on the
 * JVM without spinning up a Compose runtime — same pattern as
 * [DigestScreenBulkActionsWiringTest] and [InboxCardLanguageContractTest].
 *
 * All four tests must initially compile-fail because
 * [InboxSuggestionAcceptHandler], [InboxSuggestionAcceptCallbacks], and
 * [HighVolumeAppCandidate] do not exist yet.
 */
class InboxSuggestionAcceptIntegrationTest {

    @Test
    fun accept_adds_to_suppressedSourceApps_and_invokes_cleanup_and_clears_state() = runBlocking {
        val recorder = RecordingCallbacks()
        val candidate = HighVolumeAppCandidate(
            packageName = "com.nhn.android.search",
            appName = "네이버",
            count = 24,
            avgPerDay = 24.0 / 7.0,
        )

        InboxSuggestionAcceptHandler.accept(candidate, recorder)

        // 1. suppressedSourceApps add was called once with the candidate's pkg.
        assertEquals(listOf("com.nhn.android.search"), recorder.addedToSuppressedSourceApps)
        // 2. cleanup was called once with exactly that pkg in the target set.
        assertEquals(listOf(setOf("com.nhn.android.search")), recorder.cleanupTargets)
        // 3. dismiss was called once (suggestion state cleared).
        assertEquals(1, recorder.dismissCallCount)
    }

    @Test
    fun accept_runs_suppress_and_cleanup_in_documented_order() = runBlocking {
        val recorder = RecordingCallbacks()
        val candidate = HighVolumeAppCandidate(
            packageName = "com.kakao.talk",
            appName = "카카오톡",
            count = 20,
            avgPerDay = 20.0 / 7.0,
        )

        InboxSuggestionAcceptHandler.accept(candidate, recorder)

        // suppressedSourceApps must commit BEFORE cleanup so the listener's
        // forward-only fix (#511) sees the new entry on any racing emission.
        // Then dismiss runs LAST so the user does not see a stale card.
        assertEquals(
            listOf("ADD_SUPPRESSED", "CLEANUP", "DISMISS"),
            recorder.callOrder,
        )
    }

    @Test
    fun accept_clears_suggestion_state_even_when_cleanup_reports_notBound() = runBlocking {
        val recorder = RecordingCallbacks(cleanupNotBound = true)
        val candidate = HighVolumeAppCandidate(
            packageName = "com.coupang.eats",
            appName = "쿠팡이츠",
            count = 11,
            avgPerDay = 11.0 / 7.0,
        )

        InboxSuggestionAcceptHandler.accept(candidate, recorder)

        // The user's intent — stop bundling this app — does not depend on
        // listener bind state. Suppress write + dismiss MUST still land so
        // the next notification is captured by the suppressedSourceApps
        // policy on its way through the listener (#511 forward-only).
        assertEquals(listOf("com.coupang.eats"), recorder.addedToSuppressedSourceApps)
        assertEquals(1, recorder.dismissCallCount)
        // Cleanup was attempted exactly once even though it reported notBound.
        assertEquals(listOf(setOf("com.coupang.eats")), recorder.cleanupTargets)
    }

    @Test
    fun accept_does_not_touch_dismissed_or_snoozed_paths() = runBlocking {
        val recorder = RecordingCallbacks()
        val candidate = HighVolumeAppCandidate(
            packageName = "com.example",
            appName = "Example",
            count = 70,
            avgPerDay = 10.0,
        )

        InboxSuggestionAcceptHandler.accept(candidate, recorder)

        // [예] is the immediate-suppress path, not the sticky-dismiss path.
        // Touching either of the other two would leak suggestion state into
        // the wrong DataStore key.
        assertFalse(
            "accept() must not write to suggestedSuppressionDismissed",
            recorder.dismissedWritten,
        )
        assertFalse(
            "accept() must not write to suggestedSuppressionSnoozeUntil",
            recorder.snoozeWritten,
        )
        // Sanity: the documented three side effects still fired.
        assertTrue(recorder.addedToSuppressedSourceApps.isNotEmpty())
        assertTrue(recorder.cleanupTargets.isNotEmpty())
        assertEquals(1, recorder.dismissCallCount)
    }

    /**
     * Lightweight in-memory recorder that satisfies the
     * [InboxSuggestionAcceptCallbacks] surface area the helper consumes.
     * Records the full call sequence + per-side-effect arguments so tests
     * can assert order independently from "did it happen at all".
     */
    private class RecordingCallbacks(
        private val cleanupNotBound: Boolean = false,
    ) : InboxSuggestionAcceptCallbacks {
        val addedToSuppressedSourceApps = mutableListOf<String>()
        val cleanupTargets = mutableListOf<Set<String>>()
        var dismissCallCount = 0
        var dismissedWritten = false
        var snoozeWritten = false
        val callOrder = mutableListOf<String>()

        override suspend fun addToSuppressedSourceApps(packageName: String) {
            addedToSuppressedSourceApps += packageName
            callOrder += "ADD_SUPPRESSED"
        }

        override suspend fun cleanupTrayOrphans(targetPackages: Set<String>): InboxSuggestionCleanupOutcome {
            cleanupTargets += targetPackages
            callOrder += "CLEANUP"
            return if (cleanupNotBound) {
                InboxSuggestionCleanupOutcome.NotBound
            } else {
                InboxSuggestionCleanupOutcome.Cancelled(cancelledCount = targetPackages.size)
            }
        }

        override suspend fun dismissCard() {
            dismissCallCount += 1
            callOrder += "DISMISS"
        }

        override suspend fun markSuggestionPermanentlyDismissed(packageName: String) {
            dismissedWritten = true
        }

        override suspend fun snoozeSuggestionUntil(packageName: String, untilMillis: Long) {
            snoozeWritten = true
        }
    }
}
