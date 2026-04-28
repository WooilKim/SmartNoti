package com.smartnoti.app.data.local

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 2.
 *
 * One row in the `InboxSuggestionCard` candidate list. Produced by
 * [HighVolumeAppDetector] from a DAO `COUNT(*)` projection plus the user's
 * exclusion sets. The Composable / spec object reads only the three fields
 * directly; [avgPerDay] is precomputed (count / windowDays) so the card body
 * does not need to know the window size at render time.
 */
data class HighVolumeAppCandidate(
    val packageName: String,
    val appName: String,
    val count: Int,
    val avgPerDay: Double,
)
